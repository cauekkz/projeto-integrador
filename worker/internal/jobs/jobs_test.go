package jobs

// Testes contra o PostgreSQL de verdade (o mesmo do backend, DATABASE_*), cada um numa transacao desfeita
// no fim: nada fica gravado. Sem banco no ar, os testes sao pulados.

import (
	"context"
	"fmt"
	"math/rand"
	"os"
	"testing"
	"time"

	"worker/db"

	"github.com/jackc/pgx/v5"
)

// "hoje" fixo no meio do dia, longe da virada de dia entre UTC e Sao Paulo
var today = DayOf(time.Date(2027, 3, 10, 15, 0, 0, 0, Zone))

func begin(t *testing.T) (context.Context, pgx.Tx) {
	t.Helper()
	ctx := context.Background()
	conn, err := db.Conn(ctx)
	if err != nil {
		// no CI o banco tem que estar no ar: la falha em vez de pular
		if os.Getenv("CI") != "" {
			t.Fatal("PostgreSQL fora do ar: ", err)
		}
		t.Skip("PostgreSQL fora do ar: ", err)
	}
	t.Cleanup(func() { conn.Close(context.Background()) })
	tx, err := conn.Begin(ctx)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { tx.Rollback(context.Background()) })
	return ctx, tx
}

// fixture: motorista e responsavel com um aluno, contrato, rota com o aluno, grupo, falta futura e chat
type world struct {
	driverID, responsibleID, studentID, contractID, routeID, groupID, schoolID string
}

func uuid(ctx context.Context, t *testing.T, tx pgx.Tx) string {
	var id string
	if err := tx.QueryRow(ctx, `SELECT gen_random_uuid()::text`).Scan(&id); err != nil {
		t.Fatal(err)
	}
	return id
}

func exec(ctx context.Context, t *testing.T, tx pgx.Tx, sql string, args ...any) {
	t.Helper()
	if _, err := tx.Exec(ctx, sql, args...); err != nil {
		t.Fatalf("%v\n%s", err, sql)
	}
}

func scalar[T any](ctx context.Context, t *testing.T, tx pgx.Tx, sql string, args ...any) T {
	t.Helper()
	var v T
	if err := tx.QueryRow(ctx, sql, args...).Scan(&v); err != nil {
		t.Fatalf("%v\n%s", err, sql)
	}
	return v
}

func user(ctx context.Context, t *testing.T, tx pgx.Tx, role, status, createdAt string) string {
	id := uuid(ctx, t, tx)
	n := rand.Int63()
	exec(ctx, t, tx, `INSERT INTO users (id, name, password_hash, cpf, created_at, status, email, phone, role)
		VALUES ($1, 'Teste Worker', 'x', $2, $3::timestamp, $4, $5, $6, $7)`,
		id, fmt.Sprintf("%011d", n%100000000000), createdAt, status, fmt.Sprintf("worker%d@teste.local", n),
		fmt.Sprintf("9%010d", n%10000000000), role)
	return id
}

func newWorld(ctx context.Context, t *testing.T, tx pgx.Tx, contractStatus, start, end string) world {
	w := world{}
	address := uuid(ctx, t, tx)
	exec(ctx, t, tx, `INSERT INTO addresses (id, street, zip_code, city) VALUES ($1, 'Rua Teste', '01000000', 'Sao Paulo')`, address)
	w.driverID = user(ctx, t, tx, "ROLE_DRIVER", "ACTIVE", today.Now)
	exec(ctx, t, tx, `INSERT INTO drivers (user_id, approval_status, link_code) VALUES ($1, 'APPROVED', $2)`,
		w.driverID, fmt.Sprintf("W%08d", rand.Intn(100000000)))
	w.responsibleID = user(ctx, t, tx, "ROLE_RESPONSIBLE", "ACTIVE", today.Now)
	exec(ctx, t, tx, `INSERT INTO responsibles (user_id) VALUES ($1)`, w.responsibleID)
	w.studentID = uuid(ctx, t, tx)
	exec(ctx, t, tx, `INSERT INTO students (id, birth_date, name) VALUES ($1, '2016-05-10', 'Ana Worker')`, w.studentID)
	w.schoolID = uuid(ctx, t, tx)
	exec(ctx, t, tx, `INSERT INTO schools (id, name, address_id, email) VALUES ($1, 'Escola Worker', $2, $3)`,
		w.schoolID, address, fmt.Sprintf("escola%d@teste.local", rand.Int63()))
	w.contractID = uuid(ctx, t, tx)
	exec(ctx, t, tx, `INSERT INTO contracts (id, periodicity, value, start_date, end_date, status) VALUES ($1, 'MONTHLY', 350, $2, $3, $4)`,
		w.contractID, start, end, contractStatus)
	exec(ctx, t, tx, `INSERT INTO user_driver_contracts (id, responsible_id, driver_id, contract_id, student_id, school_id)
		VALUES (gen_random_uuid(), $1, $2, $3, $4, $5)`, w.responsibleID, w.driverID, w.contractID, w.studentID, w.schoolID)
	exec(ctx, t, tx, `INSERT INTO documents (id, type, url, status, entity_type, entity_id, uploaded_by_user_id)
		VALUES (gen_random_uuid(), 'TRANSPORT_CONTRACT', 'contratos/x.pdf', 'PENDING', 'CONTRACT', $1, $2)`, w.contractID, w.driverID)
	// o banco guarda o par em ordem (user_one_id < user_two_id)
	exec(ctx, t, tx, `INSERT INTO chats (id, user_one_id, user_two_id) VALUES (gen_random_uuid(), LEAST($1::uuid, $2::uuid), GREATEST($1::uuid, $2::uuid))`,
		w.responsibleID, w.driverID)

	w.routeID = uuid(ctx, t, tx)
	exec(ctx, t, tx, `INSERT INTO routes (id, name) VALUES ($1, 'Ida Worker')`, w.routeID)
	exec(ctx, t, tx, `INSERT INTO route_drivers (id, route_id, driver_id, start_date) VALUES (gen_random_uuid(), $1, $2, '2027-01-01')`, w.routeID, w.driverID)
	stop := uuid(ctx, t, tx)
	exec(ctx, t, tx, `INSERT INTO route_stops (id, route_id, address_id, order_index) VALUES ($1, $2, $3, 1)`, stop, w.routeID, address)
	exec(ctx, t, tx, `INSERT INTO student_stops (id, student_id, stop_id, location_type, action) VALUES (gen_random_uuid(), $1, $2, 'HOME', 'BOARDING')`, w.studentID, stop)
	exec(ctx, t, tx, `INSERT INTO student_absences (id, student_id, route_id, date, created_by) VALUES (gen_random_uuid(), $1, $2, '2027-03-20', $3)`,
		w.studentID, w.routeID, w.responsibleID)
	w.groupID = uuid(ctx, t, tx)
	exec(ctx, t, tx, `INSERT INTO driver_groups (id, driver_id, name) VALUES ($1, $2, 'Grupo Worker')`, w.groupID, w.driverID)
	exec(ctx, t, tx, `INSERT INTO driver_group_students (group_id, student_id) VALUES ($1, $2)`, w.groupID, w.studentID)
	return w
}

func payment(ctx context.Context, t *testing.T, tx pgx.Tx, contractID, due, status string) string {
	id := uuid(ctx, t, tx)
	// paga sempre tem paid_at (regra do banco)
	exec(ctx, t, tx, `INSERT INTO payments (id, contract_id, value, due_date, status, paid_at)
		VALUES ($1, $2, 350, $3::date, $4, CASE WHEN $4 = 'PAID' THEN $3::date::timestamp END)`, id, contractID, due, status)
	return id
}

func notifications(ctx context.Context, t *testing.T, tx pgx.Tx, userID, kind string) int {
	return scalar[int](ctx, t, tx, `SELECT count(*) FROM notifications WHERE user_id = $1 AND type = $2`, userID, kind)
}

func run(ctx context.Context, t *testing.T, tx pgx.Tx, job func(context.Context, pgx.Tx, Day) (int64, error)) {
	t.Helper()
	if _, err := job(ctx, tx, today); err != nil {
		t.Fatal(err)
	}
}

func TestDayIsInSaoPauloEvenWhenUtcIsTomorrow(t *testing.T) {
	// 01h de 03/11 em UTC ainda e 22h de 02/11 em Sao Paulo (foi o que quebrou os testes de API)
	day := DayOf(time.Date(2026, 11, 3, 1, 0, 0, 0, time.UTC))
	if day.Date != "2026-11-02" || day.Now != "2026-11-02 22:00:00" {
		t.Fatalf("dia errado: %+v", day)
	}
}

func TestEndContractsTakesTheStudentOffRoutesGroupsAndAbsences(t *testing.T) {
	ctx, tx := begin(t)
	w := newWorld(ctx, t, tx, "ACTIVE", "2026-08-01", "2027-03-09")
	paid := payment(ctx, t, tx, w.contractID, "2027-03-01", "PAID")
	future := payment(ctx, t, tx, w.contractID, "2027-04-01", "PENDING")
	exec(ctx, t, tx, `INSERT INTO contract_cancellations (id, contract_id, requested_by, reason, requested_at, status, effective_date)
		VALUES (gen_random_uuid(), $1, $2, 'x', $3::timestamp, 'PENDING', '2027-04-09')`, w.contractID, w.responsibleID, today.Now)
	running := newWorld(ctx, t, tx, "ACTIVE", "2026-08-01", "2027-03-09")
	trip := uuid(ctx, t, tx)
	exec(ctx, t, tx, `INSERT INTO trips (id, route_id, date, status, driver_id) VALUES ($1, $2, $3::date, 'IN_PROGRESS', $4)`,
		trip, running.routeID, today.Date, running.driverID)
	exec(ctx, t, tx, `INSERT INTO boardings (id, trip_id, student_id, status) VALUES (gen_random_uuid(), $1, $2, 'BOARDED')`, trip, running.studentID)
	stillActive := newWorld(ctx, t, tx, "ACTIVE", "2026-08-01", today.Date)

	run(ctx, t, tx, EndContracts)

	if s := scalar[string](ctx, t, tx, `SELECT status FROM contracts WHERE id = $1`, w.contractID); s != "INACTIVE" {
		t.Fatalf("contrato vencido: %s", s)
	}
	for name, sql := range map[string]string{
		"pontos na rota": `SELECT count(*) FROM student_stops WHERE student_id = $1`,
		"faltas futuras": `SELECT count(*) FROM student_absences WHERE student_id = $1`,
		"grupos":         `SELECT count(*) FROM driver_group_students WHERE student_id = $1`,
	} {
		if n := scalar[int](ctx, t, tx, sql, w.studentID); n != 0 {
			t.Errorf("%s: sobrou %d", name, n)
		}
	}
	if s := scalar[string](ctx, t, tx, `SELECT string_agg(status, ',' ORDER BY due_date) FROM payments WHERE id IN ($1, $2)`, paid, future); s != "PAID,CANCELED" {
		t.Errorf("parcelas: %s", s)
	}
	if s := scalar[string](ctx, t, tx, `SELECT status FROM contract_cancellations WHERE contract_id = $1`, w.contractID); s != "WITHDRAWN" {
		t.Errorf("pedido de cancelamento: %s", s)
	}
	if notifications(ctx, t, tx, w.driverID, "CONTRACT_ENDED") != 1 || notifications(ctx, t, tx, w.responsibleID, "CONTRACT_ENDED") != 1 {
		t.Error("os dois deviam ser avisados")
	}
	// van rodando com o aluno: espera o proximo ciclo
	if s := scalar[string](ctx, t, tx, `SELECT status FROM contracts WHERE id = $1`, running.contractID); s != "ACTIVE" {
		t.Errorf("contrato com a van rodando: %s", s)
	}
	// termina hoje: ainda vale hoje
	if s := scalar[string](ctx, t, tx, `SELECT status FROM contracts WHERE id = $1`, stillActive.contractID); s != "ACTIVE" {
		t.Errorf("contrato que termina hoje: %s", s)
	}
	if n := scalar[int](ctx, t, tx, `SELECT count(*) FROM student_stops WHERE student_id = $1`, stillActive.studentID); n != 1 {
		t.Errorf("aluno do contrato que termina hoje saiu da rota")
	}
}

func TestExpireProposalsCancelsWhatPassedTheStartDate(t *testing.T) {
	ctx, tx := begin(t)
	expired := newWorld(ctx, t, tx, "PENDING", "2027-03-09", "2027-12-15")
	startsToday := newWorld(ctx, t, tx, "PENDING", today.Date, "2027-12-15")

	run(ctx, t, tx, ExpireProposals)

	if s := scalar[string](ctx, t, tx, `SELECT status FROM contracts WHERE id = $1`, expired.contractID); s != "CANCELED" {
		t.Fatalf("proposta expirada: %s", s)
	}
	if s := scalar[string](ctx, t, tx, `SELECT status FROM documents WHERE entity_id = $1`, expired.contractID); s != "REJECTED" {
		t.Errorf("documento: %s", s)
	}
	if n := scalar[int](ctx, t, tx, `SELECT count(*) FROM chat_messages m JOIN chats c ON c.id = m.chat_id
		WHERE (c.user_one_id = $1 OR c.user_two_id = $1) AND m.message_type = 'REJECTION' AND m.payload::jsonb ->> 'contractId' = $2`, expired.driverID, expired.contractID); n != 1 {
		t.Errorf("o card do chat devia fechar: %d mensagens", n)
	}
	if notifications(ctx, t, tx, expired.driverID, "CONTRACT_EXPIRED") != 1 || notifications(ctx, t, tx, expired.responsibleID, "CONTRACT_EXPIRED") != 1 {
		t.Error("os dois deviam ser avisados")
	}
	// comeca hoje: o pai ainda pode aceitar hoje
	if s := scalar[string](ctx, t, tx, `SELECT status FROM contracts WHERE id = $1`, startsToday.contractID); s != "PENDING" {
		t.Errorf("proposta que comeca hoje: %s", s)
	}
}

func TestOverduePayments(t *testing.T) {
	ctx, tx := begin(t)
	late := newWorld(ctx, t, tx, "ACTIVE", "2027-02-01", "2027-06-30")
	overdue := payment(ctx, t, tx, late.contractID, "2027-03-09", "PENDING")
	dueToday := payment(ctx, t, tx, late.contractID, today.Date, "PENDING")
	onTime := newWorld(ctx, t, tx, "ACTIVE", "2027-02-01", "2027-06-30")
	paid := payment(ctx, t, tx, onTime.contractID, "2027-03-09", "PAID")

	run(ctx, t, tx, MarkOverduePayments)
	run(ctx, t, tx, MarkOverduePayments) // o segundo ciclo nao avisa de novo

	if s := scalar[string](ctx, t, tx, `SELECT string_agg(status, ',' ORDER BY due_date) FROM payments WHERE id IN ($1, $2)`, overdue, dueToday); s != "OVERDUE,PENDING" {
		t.Errorf("parcelas: %s (a que vence hoje ainda esta em dia)", s)
	}
	if n := notifications(ctx, t, tx, late.responsibleID, "PAYMENT"); n != 1 {
		t.Errorf("aviso de atraso: %d (deveria ser 1)", n)
	}
	if s := scalar[string](ctx, t, tx, `SELECT status FROM payments WHERE id = $1`, paid); s != "PAID" {
		t.Errorf("parcela paga nao muda: %s", s)
	}
}

func TestCleanupRemovesOnlyUnverifiedUsersOlderThan48h(t *testing.T) {
	ctx, tx := begin(t)
	old := user(ctx, t, tx, "ROLE_RESPONSIBLE", "CHECK_EMAIL", "2027-03-08 14:00:00")
	recent := user(ctx, t, tx, "ROLE_RESPONSIBLE", "CHECK_EMAIL", "2027-03-08 16:00:00")
	active := user(ctx, t, tx, "ROLE_RESPONSIBLE", "ACTIVE", "2026-01-01 00:00:00")

	run(ctx, t, tx, CleanupUnverifiedUsers)

	for id, want := range map[string]int{old: 0, recent: 1, active: 1} {
		if n := scalar[int](ctx, t, tx, `SELECT count(*) FROM users WHERE id = $1`, id); n != want {
			t.Errorf("usuario %s: %d (esperado %d)", id, n, want)
		}
	}
}

// segundo contrato do mesmo motorista com o mesmo aluno (renovacao)
func renewal(ctx context.Context, t *testing.T, tx pgx.Tx, w world, status, start, end string) string {
	id := uuid(ctx, t, tx)
	exec(ctx, t, tx, `INSERT INTO contracts (id, periodicity, value, start_date, end_date, status) VALUES ($1, 'MONTHLY', 380, $2, $3, $4)`,
		id, start, end, status)
	exec(ctx, t, tx, `INSERT INTO user_driver_contracts (id, responsible_id, driver_id, contract_id, student_id, school_id)
		VALUES (gen_random_uuid(), $1, $2, $3, $4, $5)`, w.responsibleID, w.driverID, id, w.studentID, w.schoolID)
	return id
}

func cancellation(ctx context.Context, t *testing.T, tx pgx.Tx, w world, effective string) string {
	id := uuid(ctx, t, tx)
	exec(ctx, t, tx, `INSERT INTO contract_cancellations (id, contract_id, requested_by, reason, requested_at, status, effective_date)
		VALUES ($1, $2, $3, 'mudanca', '2027-02-08 10:00:00', 'PENDING', $4::date)`, id, w.contractID, w.responsibleID, effective)
	return id
}

func stops(ctx context.Context, t *testing.T, tx pgx.Tx, studentID string) int {
	return scalar[int](ctx, t, tx, `SELECT count(*) FROM student_stops WHERE student_id = $1`, studentID)
}

func TestEndContractsKeepsTheStudentWhenRenewed(t *testing.T) {
	ctx, tx := begin(t)
	w := newWorld(ctx, t, tx, "ACTIVE", "2026-08-01", "2027-03-09")
	next := renewal(ctx, t, tx, w, "SCHEDULED", "2027-03-15", "2027-12-15")

	run(ctx, t, tx, EndContracts)

	if s := scalar[string](ctx, t, tx, `SELECT status FROM contracts WHERE id = $1`, w.contractID); s != "INACTIVE" {
		t.Fatalf("contrato vencido: %s", s)
	}
	if stops(ctx, t, tx, w.studentID) != 1 || scalar[int](ctx, t, tx, `SELECT count(*) FROM driver_group_students WHERE student_id = $1`, w.studentID) != 1 {
		t.Error("com a renovacao aceita o aluno continua na rota e no grupo")
	}
	if s := scalar[string](ctx, t, tx, `SELECT status FROM contracts WHERE id = $1`, next); s != "SCHEDULED" {
		t.Errorf("renovacao: %s", s)
	}
}

func TestCloseStaleTripsOnlyClosesTripsOpenFor20Hours(t *testing.T) {
	ctx, tx := begin(t)
	w := newWorld(ctx, t, tx, "ACTIVE", "2026-08-01", "2027-12-15")
	old := uuid(ctx, t, tx)
	exec(ctx, t, tx, `INSERT INTO trips (id, route_id, date, status, driver_id, start_time) VALUES ($1, $2, '2027-03-09', 'IN_PROGRESS', $3, '07:00')`,
		old, w.routeID, w.driverID)
	current := uuid(ctx, t, tx)
	exec(ctx, t, tx, `INSERT INTO trips (id, route_id, date, status, driver_id, start_time) VALUES ($1, $2, $3::date, 'STARTED', $4, '07:00')`,
		current, w.routeID, today.Date, w.driverID)

	run(ctx, t, tx, CloseStaleTrips)

	if s := scalar[string](ctx, t, tx, `SELECT status || ':' || coalesce(end_time::text, 'null') FROM trips WHERE id = $1`, old); s != "FINISHED:null" {
		t.Errorf("viagem de ontem: %s", s)
	}
	if s := scalar[string](ctx, t, tx, `SELECT status FROM trips WHERE id = $1`, current); s != "STARTED" {
		t.Errorf("viagem de hoje nao pode fechar: %s", s)
	}
	if notifications(ctx, t, tx, w.driverID, "SYSTEM") != 1 {
		t.Error("o motorista devia ser avisado")
	}
}

func TestTripThatCrossesMidnightIsNotClosedNorLosesTheStudent(t *testing.T) {
	ctx, tx := begin(t)
	// o worker passa 00:05 do dia 10; a viagem comecou 23:50 do dia 9 e ainda esta rodando
	justAfterMidnight := DayOf(time.Date(2027, 3, 10, 0, 5, 0, 0, Zone))
	w := newWorld(ctx, t, tx, "ACTIVE", "2026-08-01", "2027-03-09")
	trip := uuid(ctx, t, tx)
	exec(ctx, t, tx, `INSERT INTO trips (id, route_id, date, status, driver_id, start_time) VALUES ($1, $2, '2027-03-09', 'IN_PROGRESS', $3, '23:50')`,
		trip, w.routeID, w.driverID)
	exec(ctx, t, tx, `INSERT INTO boardings (id, trip_id, student_id, status) VALUES (gen_random_uuid(), $1, $2, 'BOARDED')`, trip, w.studentID)

	for _, job := range []func(context.Context, pgx.Tx, Day) (int64, error){CloseStaleTrips, EndContracts} {
		if _, err := job(ctx, tx, justAfterMidnight); err != nil {
			t.Fatal(err)
		}
	}

	if s := scalar[string](ctx, t, tx, `SELECT status FROM trips WHERE id = $1`, trip); s != "IN_PROGRESS" {
		t.Errorf("viagem rodando depois da meia-noite: %s", s)
	}
	// o contrato acabou dia 9, mas a van esta rodando com o aluno: espera o proximo ciclo
	if s := scalar[string](ctx, t, tx, `SELECT status FROM contracts WHERE id = $1`, w.contractID); s != "ACTIVE" {
		t.Errorf("contrato com a van rodando desde ontem: %s", s)
	}
	if stops(ctx, t, tx, w.studentID) != 1 {
		t.Error("o aluno nao pode sair da rota no meio da viagem")
	}
}

func TestCancelAfterNoticeCancelsOnTheEffectiveDate(t *testing.T) {
	ctx, tx := begin(t)
	w := newWorld(ctx, t, tx, "ACTIVE", "2027-01-10", "2027-12-10")
	request := cancellation(ctx, t, tx, w, "2027-03-10")
	overdue := payment(ctx, t, tx, w.contractID, "2027-02-10", "OVERDUE")
	dueOnTheDay := payment(ctx, t, tx, w.contractID, "2027-03-10", "PENDING")
	after := payment(ctx, t, tx, w.contractID, "2027-04-10", "PENDING")
	notYet := newWorld(ctx, t, tx, "ACTIVE", "2027-01-10", "2027-12-10")
	cancellation(ctx, t, tx, notYet, "2027-03-11")

	run(ctx, t, tx, CancelAfterNotice)

	if s := scalar[string](ctx, t, tx, `SELECT status FROM contracts WHERE id = $1`, w.contractID); s != "CANCELED" {
		t.Fatalf("contrato no fim do aviso: %s", s)
	}
	if s := scalar[string](ctx, t, tx, `SELECT status || ':' || (decided_at IS NOT NULL)::text FROM contract_cancellations WHERE id = $1`, request); s != "COMPLETED:true" {
		t.Errorf("pedido: %s", s)
	}
	// a divida nao some: a vencida e a do dia continuam; so a depois do aviso cai
	if s := scalar[string](ctx, t, tx, `SELECT string_agg(status, ',' ORDER BY due_date) FROM payments WHERE id IN ($1, $2, $3)`,
		overdue, dueOnTheDay, after); s != "OVERDUE,PENDING,CANCELED" {
		t.Errorf("parcelas: %s", s)
	}
	if stops(ctx, t, tx, w.studentID) != 0 {
		t.Error("o aluno devia sair da rota")
	}
	if notifications(ctx, t, tx, w.driverID, "CONTRACT_CANCELED") != 1 || notifications(ctx, t, tx, w.responsibleID, "CONTRACT_CANCELED") != 1 {
		t.Error("os dois deviam ser avisados")
	}
	if s := scalar[string](ctx, t, tx, `SELECT status FROM contracts WHERE id = $1`, notYet.contractID); s != "ACTIVE" {
		t.Errorf("aviso que termina amanha: %s", s)
	}
}

func TestCancelAfterNoticeWaitsForTodaysTrip(t *testing.T) {
	ctx, tx := begin(t)
	w := newWorld(ctx, t, tx, "ACTIVE", "2027-01-10", "2027-12-10")
	cancellation(ctx, t, tx, w, "2027-03-10")
	trip := uuid(ctx, t, tx)
	exec(ctx, t, tx, `INSERT INTO trips (id, route_id, date, status, driver_id) VALUES ($1, $2, $3::date, 'IN_PROGRESS', $4)`,
		trip, w.routeID, today.Date, w.driverID)
	exec(ctx, t, tx, `INSERT INTO boardings (id, trip_id, student_id, status) VALUES (gen_random_uuid(), $1, $2, 'BOARDED')`, trip, w.studentID)

	run(ctx, t, tx, CancelAfterNotice)

	if s := scalar[string](ctx, t, tx, `SELECT status FROM contracts WHERE id = $1`, w.contractID); s != "ACTIVE" {
		t.Errorf("com a van rodando espera o proximo ciclo: %s", s)
	}
}

func TestActivateContractsOnTheStartDate(t *testing.T) {
	ctx, tx := begin(t)
	starts := newWorld(ctx, t, tx, "SCHEDULED", today.Date, "2027-12-15")
	later := newWorld(ctx, t, tx, "SCHEDULED", "2027-03-11", "2027-12-15")

	run(ctx, t, tx, ActivateContracts)

	if s := scalar[string](ctx, t, tx, `SELECT status FROM contracts WHERE id = $1`, starts.contractID); s != "ACTIVE" {
		t.Errorf("comeca hoje: %s", s)
	}
	if s := scalar[string](ctx, t, tx, `SELECT status FROM contracts WHERE id = $1`, later.contractID); s != "SCHEDULED" {
		t.Errorf("comeca amanha: %s", s)
	}
	if notifications(ctx, t, tx, starts.responsibleID, "CONTRACT_STARTED") != 1 {
		t.Error("o responsavel devia ser avisado")
	}
}

func TestNextRunIsTheNextSaoPauloTime(t *testing.T) {
	// 02h UTC de 10/03 = 23h de 09/03 em Sao Paulo: o proximo 00:05 e o de 10/03
	next := NextRun(time.Date(2027, 3, 10, 2, 0, 0, 0, time.UTC), 0, 5)
	if got := next.In(Zone).Format("2006-01-02 15:04"); got != "2027-03-10 00:05" {
		t.Errorf("proximo ciclo: %s", got)
	}
	// exatamente no horario: o proximo e amanha
	next = NextRun(time.Date(2027, 3, 10, 0, 5, 0, 0, Zone), 0, 5)
	if got := next.In(Zone).Format("2006-01-02 15:04"); got != "2027-03-11 00:05" {
		t.Errorf("no horario: %s", got)
	}
}

package jobs

import (
	"context"

	"github.com/jackc/pgx/v5"
)

// viagem rodando agora com o aluno e esse motorista (qualquer data: a que comecou 23:50 ainda roda depois da
// meia-noite; as esquecidas o CloseStaleTrips ja fechou antes, depois de 20 horas): tirar o aluno da rota quebraria a viagem, entao o
// contrato espera o proximo ciclo. Mesma regra do backend (ContractCancellationService.cancel)
const noTripRunning = `NOT EXISTS (
	SELECT 1 FROM boardings b JOIN trips t ON t.id = b.trip_id
	WHERE b.student_id = u.student_id AND t.driver_id = u.driver_id
	  AND t.status IN ('STARTED', 'IN_PROGRESS', 'PAUSED'))`

// tira o aluno das rotas em que o motorista esta hoje, das faltas futuras nelas e dos grupos do motorista, a nao
// ser que ele tenha outro contrato aceito (SCHEDULED ou ACTIVE) com o mesmo motorista (renovacao: continua tudo).
// E a mesma limpeza do backend (ContractCancellationService.cancel): se uma mudar, a outra tambem
func releaseStudent(ctx context.Context, tx pgx.Tx, day Day, contractID, driverID, studentID string) error {
	var renewed bool
	if err := tx.QueryRow(ctx, `
		SELECT EXISTS (SELECT 1 FROM user_driver_contracts u JOIN contracts c ON c.id = u.contract_id
		               WHERE u.driver_id = $1 AND u.student_id = $2 AND c.id <> $3 AND c.status IN ('SCHEDULED', 'ACTIVE'))`,
		driverID, studentID, contractID).Scan(&renewed); err != nil {
		return err
	}
	if renewed {
		return nil
	}
	steps := []struct {
		sql  string
		args []any
	}{
		{`DELETE FROM student_stops ss USING route_stops rs, route_drivers rd
		  WHERE ss.stop_id = rs.id AND rs.route_id = rd.route_id
		    AND rd.driver_id = $1 AND rd.end_date IS NULL AND ss.student_id = $2`, []any{driverID, studentID}},
		{`DELETE FROM student_absences sa USING route_drivers rd
		  WHERE sa.route_id = rd.route_id AND rd.driver_id = $1 AND rd.end_date IS NULL
		    AND sa.student_id = $2 AND sa.date >= $3::date`, []any{driverID, studentID, day.Date}},
		{`DELETE FROM driver_group_students dgs USING driver_groups g
		  WHERE dgs.group_id = g.id AND g.driver_id = $1 AND dgs.student_id = $2`, []any{driverID, studentID}},
	}
	for _, step := range steps {
		if _, err := tx.Exec(ctx, step.sql, step.args...); err != nil {
			return err
		}
	}
	return nil
}

type endedContract struct {
	contractID, driverID, responsibleID, studentID, studentName string
}

// contrato que passou da data de fim vira INACTIVE e o aluno sai das rotas, das faltas futuras e dos grupos
// do motorista (releaseStudent). Fica no historico: contrato, documento, assinaturas, parcelas, chat e viagens
func EndContracts(ctx context.Context, tx pgx.Tx, day Day) (int64, error) {
	rows, err := tx.Query(ctx, `
		SELECT c.id, u.driver_id, u.responsible_id, u.student_id, s.name
		FROM contracts c
		JOIN user_driver_contracts u ON u.contract_id = c.id
		JOIN students s ON s.id = u.student_id
		WHERE c.status = 'ACTIVE' AND c.end_date < $1::date AND `+noTripRunning+`
		FOR UPDATE OF c`, day.Date)
	if err != nil {
		return 0, err
	}
	ended, err := pgx.CollectRows(rows, func(row pgx.CollectableRow) (endedContract, error) {
		var c endedContract
		err := row.Scan(&c.contractID, &c.driverID, &c.responsibleID, &c.studentID, &c.studentName)
		return c, err
	})
	if err != nil {
		return 0, err
	}

	for _, c := range ended {
		steps := []struct {
			sql  string
			args []any
		}{
			{`UPDATE contracts SET status = 'INACTIVE' WHERE id = $1`, []any{c.contractID}},
			// parcela futura nao deveria existir (vencem ate a data de fim), mas se existir nao vale mais
			{`UPDATE payments SET status = 'CANCELED' WHERE contract_id = $1 AND status = 'PENDING' AND due_date > $2::date`,
				[]any{c.contractID, day.Date}},
			// pedido de cancelamento com aviso correndo perde o sentido
			{`UPDATE contract_cancellations SET status = 'WITHDRAWN', decided_at = $2::timestamp
			  WHERE contract_id = $1 AND status = 'PENDING'`, []any{c.contractID, day.Now}},
		}
		for _, step := range steps {
			if _, err := tx.Exec(ctx, step.sql, step.args...); err != nil {
				return 0, err
			}
		}
		if err := releaseStudent(ctx, tx, day, c.contractID, c.driverID, c.studentID); err != nil {
			return 0, err
		}
		message := "O contrato de " + c.studentName + " chegou ao fim"
		for _, userID := range []string{c.driverID, c.responsibleID} {
			if err := notify(ctx, tx, day, userID, "CONTRACT_ENDED", "Contrato encerrado", message,
				map[string]string{"contractId": c.contractID}); err != nil {
				return 0, err
			}
		}
	}
	return int64(len(ended)), nil
}

type noticeEnded struct {
	cancellationID, contractID, effectiveDate, driverID, responsibleID, studentID, studentName string
}

// pedido de cancelamento cujo aviso previo terminou (effective_date chegou) e ninguem aceitou nem desistiu:
// o contrato e cancelado. As parcelas que vencem depois do fim do aviso sao canceladas; as anteriores continuam
// devidas. Mesma limpeza do cancelamento no backend
func CancelAfterNotice(ctx context.Context, tx pgx.Tx, day Day) (int64, error) {
	rows, err := tx.Query(ctx, `
		SELECT cc.id, c.id, cc.effective_date::text, u.driver_id, u.responsible_id, u.student_id, s.name
		FROM contract_cancellations cc
		JOIN contracts c ON c.id = cc.contract_id
		JOIN user_driver_contracts u ON u.contract_id = c.id
		JOIN students s ON s.id = u.student_id
		WHERE cc.status = 'PENDING' AND cc.effective_date <= $1::date AND c.status IN ('SCHEDULED', 'ACTIVE')
		  AND `+noTripRunning+`
		FOR UPDATE OF c, cc`, day.Date)
	if err != nil {
		return 0, err
	}
	due, err := pgx.CollectRows(rows, func(row pgx.CollectableRow) (noticeEnded, error) {
		var n noticeEnded
		err := row.Scan(&n.cancellationID, &n.contractID, &n.effectiveDate, &n.driverID, &n.responsibleID, &n.studentID, &n.studentName)
		return n, err
	})
	if err != nil {
		return 0, err
	}
	for _, n := range due {
		steps := []struct {
			sql  string
			args []any
		}{
			{`UPDATE contracts SET status = 'CANCELED' WHERE id = $1`, []any{n.contractID}},
			{`UPDATE contract_cancellations SET status = 'COMPLETED', decided_at = $2::timestamp WHERE id = $1`,
				[]any{n.cancellationID, day.Now}},
			{`UPDATE payments SET status = 'CANCELED' WHERE contract_id = $1 AND status IN ('PENDING', 'OVERDUE')
			  AND due_date > $2::date`, []any{n.contractID, n.effectiveDate}},
		}
		for _, step := range steps {
			if _, err := tx.Exec(ctx, step.sql, step.args...); err != nil {
				return 0, err
			}
		}
		if err := releaseStudent(ctx, tx, day, n.contractID, n.driverID, n.studentID); err != nil {
			return 0, err
		}
		message := "O aviso prévio terminou e o contrato de " + n.studentName + " foi cancelado. As parcelas vencidas até hoje continuam devidas"
		for _, userID := range []string{n.driverID, n.responsibleID} {
			if err := notify(ctx, tx, day, userID, "CONTRACT_CANCELED", "Contrato cancelado", message,
				map[string]string{"contractId": n.contractID}); err != nil {
				return 0, err
			}
		}
	}
	return int64(len(due)), nil
}

// contrato aceito antes da data de inicio (SCHEDULED) passa a valer no dia: so entao o motorista pode colocar o
// aluno nas rotas e o aluno entra nas viagens
func ActivateContracts(ctx context.Context, tx pgx.Tx, day Day) (int64, error) {
	rows, err := tx.Query(ctx, `
		WITH started AS (
		    UPDATE contracts SET status = 'ACTIVE' WHERE status = 'SCHEDULED' AND start_date <= $1::date RETURNING id
		)
		SELECT st.id, u.driver_id, u.responsible_id, s.name
		FROM started st JOIN user_driver_contracts u ON u.contract_id = st.id JOIN students s ON s.id = u.student_id`, day.Date)
	if err != nil {
		return 0, err
	}
	type startedContract struct{ contractID, driverID, responsibleID, studentName string }
	started, err := pgx.CollectRows(rows, func(row pgx.CollectableRow) (startedContract, error) {
		var c startedContract
		err := row.Scan(&c.contractID, &c.driverID, &c.responsibleID, &c.studentName)
		return c, err
	})
	if err != nil {
		return 0, err
	}
	for _, c := range started {
		for _, userID := range []string{c.driverID, c.responsibleID} {
			if err := notify(ctx, tx, day, userID, "CONTRACT_STARTED", "Contrato começou",
				"O contrato de "+c.studentName+" começou hoje", map[string]string{"contractId": c.contractID}); err != nil {
				return 0, err
			}
		}
	}
	return int64(len(started)), nil
}

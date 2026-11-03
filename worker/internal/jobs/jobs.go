// Package jobs tem as tarefas diarias do worker. Cada uma roda numa transacao propria:
// se falhar no meio, nada dela fica gravado e ela tenta de novo no proximo ciclo.
package jobs

import (
	"context"
	"encoding/json"
	"time"

	"github.com/jackc/pgx/v5"
)

// mesmo fuso do backend (TripService.ZONE). O banco roda em UTC: "hoje" sempre vem daqui, nunca do CURRENT_DATE
var Zone = mustLoad("America/Sao_Paulo")

func mustLoad(name string) *time.Location {
	loc, err := time.LoadLocation(name)
	if err != nil {
		panic(err)
	}
	return loc
}

// Day e o momento em que o ciclo roda, no fuso de Sao Paulo
type Day struct {
	Date string // AAAA-MM-DD: "hoje" pras comparacoes com vencimento, inicio e fim
	Now  string // AAAA-MM-DD HH:MM:SS: horario gravado nas colunas sem fuso (igual ao LocalDateTime do Java)
}

func DayOf(t time.Time) Day {
	local := t.In(Zone)
	return Day{Date: local.Format("2006-01-02"), Now: local.Format("2006-01-02 15:04:05")}
}

// proximo horario hh:mm de Sao Paulo depois de now (hoje, se ainda nao passou; senao amanha)
func NextRun(now time.Time, hour, minute int) time.Time {
	local := now.In(Zone)
	next := time.Date(local.Year(), local.Month(), local.Day(), hour, minute, 0, 0, Zone)
	if !next.After(local) {
		next = next.AddDate(0, 0, 1)
	}
	return next
}

// Job e uma tarefa diaria; devolve quantos registros ela mudou
type Job struct {
	Name string
	Run  func(ctx context.Context, tx pgx.Tx, day Day) (int64, error)
}

// todas, na ordem em que rodam:
//   - as viagens esquecidas fecham antes de mexer em contratos, porque viagem rodando segura o aluno na rota;
//   - o aviso previo vence antes de o contrato agendado comecar (nao avisa "comecou" de um contrato cancelado)
var All = []Job{
	{Name: "usuarios sem e-mail confirmado", Run: CleanupUnverifiedUsers},
	{Name: "viagens esquecidas abertas", Run: CloseStaleTrips},
	{Name: "propostas expiradas", Run: ExpireProposals},
	{Name: "cancelamentos no fim do aviso previo", Run: CancelAfterNotice},
	{Name: "contratos que comecam hoje", Run: ActivateContracts},
	{Name: "contratos encerrados", Run: EndContracts},
	{Name: "parcelas atrasadas", Run: MarkOverduePayments},
}

// grava uma notificacao igual as do backend (o app mostra quando abrir; o worker nao tem o WebSocket)
func notify(ctx context.Context, tx pgx.Tx, day Day, userID, kind, title, message string, payload map[string]string) error {
	body, err := json.Marshal(payload)
	if err != nil {
		return err
	}
	_, err = tx.Exec(ctx, `
		INSERT INTO notifications (id, user_id, title, message, created_at, type, payload)
		VALUES (gen_random_uuid(), $1, $2, $3, $4::timestamp, $5, $6::jsonb)`,
		userID, title, message, day.Now, kind, string(body))
	return err
}

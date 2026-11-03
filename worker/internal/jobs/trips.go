package jobs

import (
	"context"

	"github.com/jackc/pgx/v5"
)

// viagem aberta ha mais de 20 horas (o motorista esqueceu de finalizar): fecha como FINISHED, sem horario de fim
// (end_time continua nulo = fechada pelo sistema), e avisa o motorista. Sem isso ela contaria como "van rodando"
// pra sempre e seguraria o aluno na rota. Conta as horas desde o inicio, e nao "dia anterior": a viagem que comecou
// 23:50 e ainda roda as 00:05 (quando o worker passa) nao e fechada
func CloseStaleTrips(ctx context.Context, tx pgx.Tx, day Day) (int64, error) {
	rows, err := tx.Query(ctx, `
		UPDATE trips t SET status = 'FINISHED'
		FROM routes r
		WHERE r.id = t.route_id AND t.status IN ('STARTED', 'IN_PROGRESS', 'PAUSED')
		  AND t.date + COALESCE(t.start_time, TIME '00:00') < $1::timestamp - INTERVAL '20 hours'
		RETURNING t.id, t.driver_id, r.name, to_char(t.date, 'DD/MM/YYYY')`, day.Now)
	if err != nil {
		return 0, err
	}
	type staleTrip struct {
		tripID    string
		driverID  *string
		routeName string
		date      string
	}
	closed, err := pgx.CollectRows(rows, func(row pgx.CollectableRow) (staleTrip, error) {
		var s staleTrip
		err := row.Scan(&s.tripID, &s.driverID, &s.routeName, &s.date)
		return s, err
	})
	if err != nil {
		return 0, err
	}
	for _, s := range closed {
		if s.driverID == nil {
			continue
		}
		if err := notify(ctx, tx, day, *s.driverID, "SYSTEM", "Viagem fechada automaticamente",
			"A viagem de "+s.date+" da rota "+s.routeName+" não foi finalizada e foi fechada pelo sistema",
			map[string]string{"tripId": s.tripID}); err != nil {
			return 0, err
		}
	}
	return int64(len(closed)), nil
}

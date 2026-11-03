package jobs

import (
	"context"

	"github.com/jackc/pgx/v5"
)

type endedContract struct {
	contractID, driverID, responsibleID, studentID, studentName string
}

// contrato que passou da data de fim vira INACTIVE e o aluno sai das rotas, das faltas futuras e dos grupos
// do motorista. E a mesma limpeza do cancelamento no backend (ContractCancellationService.cancel): se uma
// mudar, a outra tambem. Fica no historico: contrato, documento, assinaturas, parcelas, chat e viagens.
// Com a van rodando com o aluno agora, espera o proximo ciclo (tirar ele da rota quebraria a viagem)
func EndContracts(ctx context.Context, tx pgx.Tx, day Day) (int64, error) {
	rows, err := tx.Query(ctx, `
		SELECT c.id, u.driver_id, u.responsible_id, u.student_id, s.name
		FROM contracts c
		JOIN user_driver_contracts u ON u.contract_id = c.id
		JOIN students s ON s.id = u.student_id
		WHERE c.status = 'ACTIVE' AND c.end_date < $1::date
		  AND NOT EXISTS (
		      SELECT 1 FROM boardings b JOIN trips t ON t.id = b.trip_id
		      WHERE b.student_id = u.student_id AND t.driver_id = u.driver_id
		        AND t.status IN ('STARTED', 'IN_PROGRESS', 'PAUSED'))
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
			// pontos do aluno nas rotas em que o motorista esta hoje
			{`DELETE FROM student_stops ss USING route_stops rs, route_drivers rd
			  WHERE ss.stop_id = rs.id AND rs.route_id = rd.route_id
			    AND rd.driver_id = $1 AND rd.end_date IS NULL AND ss.student_id = $2`, []any{c.driverID, c.studentID}},
			{`DELETE FROM student_absences sa USING route_drivers rd
			  WHERE sa.route_id = rd.route_id AND rd.driver_id = $1 AND rd.end_date IS NULL
			    AND sa.student_id = $2 AND sa.date >= $3::date`, []any{c.driverID, c.studentID, day.Date}},
			{`DELETE FROM driver_group_students dgs USING driver_groups g
			  WHERE dgs.group_id = g.id AND g.driver_id = $1 AND dgs.student_id = $2`, []any{c.driverID, c.studentID}},
			// parcela futura nao deveria existir (vencem ate a data de fim), mas se existir nao vale mais
			{`UPDATE payments SET status = 'CANCELED' WHERE contract_id = $1 AND status = 'PENDING' AND due_date > $2::date`,
				[]any{c.contractID, day.Date}},
			// pedido de cancelamento que ficou sem resposta perde o sentido
			{`UPDATE contract_cancellations SET status = 'WITHDRAWN', decided_at = $2::timestamp
			  WHERE contract_id = $1 AND status = 'PENDING'`, []any{c.contractID, day.Now}},
		}
		for _, step := range steps {
			if _, err := tx.Exec(ctx, step.sql, step.args...); err != nil {
				return 0, err
			}
		}
		message := "O contrato de " + c.studentName + " chegou ao fim. O aluno saiu das rotas e dos grupos do motorista"
		for _, userID := range []string{c.driverID, c.responsibleID} {
			if err := notify(ctx, tx, day, userID, "CONTRACT_ENDED", "Contrato encerrado", message,
				map[string]string{"contractId": c.contractID}); err != nil {
				return 0, err
			}
		}
	}
	return int64(len(ended)), nil
}

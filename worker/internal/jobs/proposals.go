package jobs

import (
	"context"

	"github.com/jackc/pgx/v5"
)

type expiredProposal struct {
	contractID, driverID, responsibleID, studentName string
}

// proposta que ninguem respondeu ate a data de inicio nao pode mais ser aceita (o backend recusa):
// vira CANCELED, o documento REJECTED, o card do chat fecha e os dois sao avisados. Fica no historico
func ExpireProposals(ctx context.Context, tx pgx.Tx, day Day) (int64, error) {
	rows, err := tx.Query(ctx, `
		SELECT c.id, u.driver_id, u.responsible_id, s.name
		FROM contracts c
		JOIN user_driver_contracts u ON u.contract_id = c.id
		JOIN students s ON s.id = u.student_id
		WHERE c.status = 'PENDING' AND c.start_date < $1::date
		FOR UPDATE OF c`, day.Date)
	if err != nil {
		return 0, err
	}
	expired, err := pgx.CollectRows(rows, func(row pgx.CollectableRow) (expiredProposal, error) {
		var p expiredProposal
		err := row.Scan(&p.contractID, &p.driverID, &p.responsibleID, &p.studentName)
		return p, err
	})
	if err != nil {
		return 0, err
	}

	for _, p := range expired {
		if _, err := tx.Exec(ctx, `UPDATE contracts SET status = 'CANCELED' WHERE id = $1`, p.contractID); err != nil {
			return 0, err
		}
		if _, err := tx.Exec(ctx, `
			UPDATE documents SET status = 'REJECTED'
			WHERE entity_type = 'CONTRACT' AND entity_id = $1 AND status = 'PENDING'`, p.contractID); err != nil {
			return 0, err
		}
		// mesma mensagem que o backend manda quando o motorista retira a proposta: o front fecha o card
		if _, err := tx.Exec(ctx, `
			INSERT INTO chat_messages (id, chat_id, sender_user_id, content, sent_at, message_type, payload)
			SELECT gen_random_uuid(), ch.id, $1, 'Proposta Expirada', $3::timestamp, 'REJECTION', jsonb_build_object('contractId', $4::text)
			FROM chats ch
			WHERE (ch.user_one_id = $1 AND ch.user_two_id = $2) OR (ch.user_one_id = $2 AND ch.user_two_id = $1)
			LIMIT 1`, p.driverID, p.responsibleID, day.Now, p.contractID); err != nil {
			return 0, err
		}
		message := "A proposta de contrato de " + p.studentName + " não foi respondida até a data de início e foi cancelada"
		for _, userID := range []string{p.driverID, p.responsibleID} {
			if err := notify(ctx, tx, day, userID, "CONTRACT_EXPIRED", "Proposta expirada", message,
				map[string]string{"contractId": p.contractID}); err != nil {
				return 0, err
			}
		}
	}
	return int64(len(expired)), nil
}

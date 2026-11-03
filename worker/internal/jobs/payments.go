package jobs

import (
	"context"

	"github.com/jackc/pgx/v5"
)

type overduePayment struct {
	paymentID, contractID, responsibleID, studentName, dueDate string
}

// parcela pendente com vencimento antes de hoje vira OVERDUE e o responsavel e avisado (uma vez so:
// no proximo ciclo ela ja nao e mais PENDING). Vale tambem pra contrato cancelado: a divida continua
func MarkOverduePayments(ctx context.Context, tx pgx.Tx, day Day) (int64, error) {
	rows, err := tx.Query(ctx, `
		UPDATE payments p SET status = 'OVERDUE'
		FROM user_driver_contracts u, students s
		WHERE u.contract_id = p.contract_id AND s.id = u.student_id
		  AND p.status = 'PENDING' AND p.due_date < $1::date
		RETURNING p.id, p.contract_id, u.responsible_id, s.name, to_char(p.due_date, 'DD/MM/YYYY')`, day.Date)
	if err != nil {
		return 0, err
	}
	overdue, err := pgx.CollectRows(rows, func(row pgx.CollectableRow) (overduePayment, error) {
		var p overduePayment
		err := row.Scan(&p.paymentID, &p.contractID, &p.responsibleID, &p.studentName, &p.dueDate)
		return p, err
	})
	if err != nil {
		return 0, err
	}
	for _, p := range overdue {
		if err := notify(ctx, tx, day, p.responsibleID, "PAYMENT", "Parcela atrasada",
			"A parcela de "+p.dueDate+" de "+p.studentName+" venceu e ainda não foi confirmada pelo motorista",
			map[string]string{"paymentId": p.paymentID, "contractId": p.contractID}); err != nil {
			return 0, err
		}
	}
	return int64(len(overdue)), nil
}

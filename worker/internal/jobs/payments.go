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

// OVERDUE pra quem tem parcela atrasada em qualquer contrato, UP_TO_DATE pra quem nao tem.
// O backend tambem atualiza na hora quando o motorista da baixa; aqui pega quem ficou atrasado de um dia pro outro
func RefreshFinancialStatus(ctx context.Context, tx pgx.Tx, day Day) (int64, error) {
	result, err := tx.Exec(ctx, `
		UPDATE responsibles r SET financial_status = x.status
		FROM (
		    SELECT r2.user_id,
		           CASE WHEN EXISTS (
		               SELECT 1 FROM payments p JOIN user_driver_contracts u ON u.contract_id = p.contract_id
		               WHERE u.responsible_id = r2.user_id
		                 AND (p.status = 'OVERDUE' OR (p.status = 'PENDING' AND p.due_date < $1::date))
		           ) THEN 'OVERDUE' ELSE 'UP_TO_DATE' END AS status
		    FROM responsibles r2
		) x
		WHERE x.user_id = r.user_id
		  AND r.financial_status IN ('UP_TO_DATE', 'OVERDUE', 'PENDING')
		  AND r.financial_status <> x.status`, day.Date)
	if err != nil {
		return 0, err
	}
	return result.RowsAffected(), nil
}

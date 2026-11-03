package jobs

import (
	"context"

	"github.com/jackc/pgx/v5"
)

// apaga quem se cadastrou e nao confirmou o e-mail em 48 horas (motorista e responsavel saem junto, em cascata)
func CleanupUnverifiedUsers(ctx context.Context, tx pgx.Tx, day Day) (int64, error) {
	result, err := tx.Exec(ctx, `
		DELETE FROM users
		WHERE status = 'CHECK_EMAIL'
		  AND created_at <= $1::timestamp - INTERVAL '48 hours'`, day.Now)
	if err != nil {
		return 0, err
	}
	return result.RowsAffected(), nil
}

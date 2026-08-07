package repository

import (
	"context"

	"github.com/jackc/pgx/v5"
)

func CleanupUsers(ctx context.Context, conn *pgx.Conn) (int64, error) {
	query := `
		DELETE FROM users
		WHERE status = 'CHECK_EMAIL'
		AND created_at <= NOW() - INTERVAL '48 hours'
	`

	result, err := conn.Exec(ctx, query)
	if err != nil {
		return 0, err
	}

	return result.RowsAffected(), nil
}

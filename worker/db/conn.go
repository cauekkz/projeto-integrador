package db

import (
	"context"
	"worker/internal/config"

	"github.com/jackc/pgx/v5"
)

func Conn(ctx context.Context) (pgx.Conn, error) {

	databaseURL := config.GetDatabaseURL()

	conn, err := pgx.Connect(ctx, databaseURL)
	if err != nil {
		return pgx.Conn{}, err
	}

	return *conn, nil
}

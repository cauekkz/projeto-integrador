package db

import (
	"context"
	"worker/internal/config"

	"github.com/jackc/pgx/v5"
)

func Conn(ctx context.Context) (*pgx.Conn, error) {
	return pgx.Connect(ctx, config.GetDatabaseURL())
}

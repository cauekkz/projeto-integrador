package db

import (
	"context"
	"worker/internal/config"

	"github.com/jackc/pgx/v5"
)

func Conn(ctx context.Context) (*pgx.Conn, error) {
	address, err := config.GetDatabaseURL()
	if err != nil {
		return nil, err
	}
	return pgx.Connect(ctx, address)
}

package utils

import (
	"context"
	"os"
	"time"
)

func GetEnv(key, fallback string) string {
	value := os.Getenv(key)

	if value == "" {
		return fallback
	}

	return value
}

// cancela a query se demorar mt, dá pra definir tempo caso tenhar outros workers

const defaultTimeout = 10 * time.Second

func WithTimeout(ctx context.Context, customTimeout ...time.Duration) (context.Context, context.CancelFunc) {
	timeout := defaultTimeout

	if len(customTimeout) > 0 {
		timeout = customTimeout[0]
	}

	return context.WithTimeout(ctx, timeout)
}

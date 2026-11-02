package config

import (
	"fmt"
	"worker/internal/utils"
)

func GetDatabaseURL() string {
	databaseName := utils.GetEnv("DATABASE_NAME", "vanroute_db")
	databaseUsername := utils.GetEnv("DATABASE_USERNAME", "postgres")
	databasePassword := utils.GetEnv("DATABASE_PASSWORD", "123456")
	databaseHost := utils.GetEnv("DATABASE_HOST", "localhost")
	databasePort := utils.GetEnv("DATABASE_PORT", "5432")

	return fmt.Sprintf(
		"postgres://%s:%s@%s:%s/%s",
		databaseUsername,
		databasePassword,
		databaseHost,
		databasePort,
		databaseName,
	)
}

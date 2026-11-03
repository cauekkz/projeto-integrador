package config

import (
	"errors"
	"net/url"
	"os"

	"worker/internal/utils"
)

// endereco do PostgreSQL (o mesmo banco do backend), montado das variaveis DATABASE_*.
// DATABASE_PASSWORD e obrigatoria (sem valor padrao, igual ao backend): em desenvolvimento e a do
// docker-compose.yml. DATABASE_SSLMODE: "prefer" (padrao: usa SSL se o servidor tiver), "require" em producao
// com banco em outra maquina, ou "disable"
func GetDatabaseURL() (string, error) {
	password := os.Getenv("DATABASE_PASSWORD")
	if password == "" {
		return "", errors.New("DATABASE_PASSWORD nao definida")
	}
	u := url.URL{
		Scheme: "postgres",
		// url.UserPassword escapa a senha (com @, : ou / ela quebraria o endereco)
		User: url.UserPassword(utils.GetEnv("DATABASE_USERNAME", "postgres"), password),
		Host: utils.GetEnv("DATABASE_HOST", "localhost") + ":" + utils.GetEnv("DATABASE_PORT", "5432"),
		Path: "/" + utils.GetEnv("DATABASE_NAME", "vanroute_db"),
	}
	u.RawQuery = url.Values{"sslmode": {utils.GetEnv("DATABASE_SSLMODE", "prefer")}}.Encode()
	return u.String(), nil
}

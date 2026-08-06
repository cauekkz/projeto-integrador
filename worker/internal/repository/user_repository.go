package repository

import (
	"context"
	"log"

	"github.com/jackc/pgx/v5"
)

func CleanupUsers(ctx context.Context, conn *pgx.Conn) {
	//adicionar um intervalo de tempo do created_at para nao remover registros mt recentes
	query := `
		DELETE FROM users
		WHERE status = 'CHECK_EMAIL'
		AND created_at < NOW() 
	`

	result, err := conn.Exec(ctx, query)
	if err != nil {
		log.Println("Erro ao limpar usuários:", err)
		return
	}

	log.Printf("Usuários removidos: %d\n", result.RowsAffected())
}

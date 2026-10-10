package repository

import (
	"context"
	"fmt"

	"github.com/jackc/pgx/v5"
)

type Response struct {
	Message    string
	Success    bool
	DeletedIDs []int64
}

// user_drive_contract, contracts e documents
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

// user_driver_contracts, contracts e documents
func DeleteContract(ctx context.Context, conn *pgx.Conn) (Response, error) {
	tx, err := conn.Begin(ctx)
	if err != nil {
		return Response{
			Message: "Erro ao iniciar transação",
			Success: false,
		}, err
	}
	defer tx.Rollback(ctx)

	query := `
	DELETE FROM contracts
	WHERE status = 'PENDING'
	  AND created_at <= NOW() - INTERVAL '30 days'
	RETURNING id
`

	rows, err := tx.Query(ctx, query)
	if err != nil {
		return Response{
			Message: "Erro ao executar a query",
			Success: false,
		}, err
	}

	var deletedIDs []int64
	for rows.Next() {
		var id int64
		if err := rows.Scan(&id); err != nil {
			rows.Close()
			return Response{
				Message: "Erro ao ler o retorno",
				Success: false,
			}, err
		}
		deletedIDs = append(deletedIDs, id)
	}
	rows.Close() 
	if err := rows.Err(); err != nil {
		return Response{
			Message: "Erro ao ler o retorno",
			Success: false,
		}, err
	}

	for _, id := range deletedIDs {
		_, err := tx.Exec(ctx, `
			DELETE FROM user_driver_contracts
			WHERE contract_id = $1
		`, id)
		if err != nil {
			return Response{
				Message: "Erro ao excluir user_driver_contracts",
				Success: false,
			}, err
		}

		_, err = tx.Exec(ctx, `
			DELETE FROM documents
			WHERE contract_id = $1
		`, id)
		if err != nil {
			return Response{
				Message: "Erro ao excluir documents",
				Success: false,
			}, err
		}
	}

	if err := tx.Commit(ctx); err != nil {
		return Response{
			Message: "Erro ao confirmar transação",
			Success: false,
		}, err
	}

	return Response{
		Message:    fmt.Sprintf("%d contrato(s) excluído(s)", len(deletedIDs)),
		Success:    true,
		DeletedIDs: deletedIDs,
	}, nil
}
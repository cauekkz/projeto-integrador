// Worker das tarefas diarias do VanRoute, separado do backend: roda em uma instancia so, mesmo que o
// backend tenha varias. Roda tudo ao subir e depois a cada WORKER_INTERVAL (padrao 24h).
package main

import (
	"context"
	"log"
	"os/signal"
	"syscall"
	"time"

	// fuso de Sao Paulo embutido no binario (a imagem do container pode nao ter o tzdata)
	_ "time/tzdata"

	"worker/db"
	"worker/internal/jobs"
	"worker/internal/utils"

	"github.com/jackc/pgx/v5"
)

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	interval, err := time.ParseDuration(utils.GetEnv("WORKER_INTERVAL", "24h"))
	if err != nil {
		log.Fatal("WORKER_INTERVAL invalido: ", err)
	}

	runAll(ctx)
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			log.Println("worker encerrado")
			return
		case <-ticker.C:
			runAll(ctx)
		}
	}
}

// uma conexao por ciclo (o worker passa o dia parado; nao segura conexao aberta a toa)
func runAll(ctx context.Context) {
	conn, err := db.Conn(ctx)
	if err != nil {
		log.Println("erro ao conectar no PostgreSQL:", err)
		return
	}
	defer conn.Close(context.Background())

	day := jobs.DayOf(time.Now())
	log.Printf("ciclo do dia %s", day.Date)
	for _, job := range jobs.All {
		changed, err := runJob(ctx, conn, job, day)
		if err != nil {
			log.Printf("  %s: ERRO: %v", job.Name, err)
			continue
		}
		log.Printf("  %s: %d", job.Name, changed)
	}
}

// cada tarefa na sua transacao: se der erro, nada dela fica gravado e as outras rodam mesmo assim
func runJob(ctx context.Context, conn *pgx.Conn, job jobs.Job, day jobs.Day) (int64, error) {
	ctx, cancel := utils.WithTimeout(ctx, time.Minute)
	defer cancel()
	tx, err := conn.Begin(ctx)
	if err != nil {
		return 0, err
	}
	defer tx.Rollback(context.Background())
	changed, err := job.Run(ctx, tx, day)
	if err != nil {
		return 0, err
	}
	return changed, tx.Commit(ctx)
}

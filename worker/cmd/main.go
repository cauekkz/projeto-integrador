// Worker das tarefas diarias do VanRoute, separado do backend: roda em uma instancia so, mesmo que o
// backend tenha varias. Roda tudo ao subir e depois todo dia em WORKER_RUN_AT (padrao 00:05, horario de
// Sao Paulo): logo depois da virada do dia, antes das viagens da manha. WORKER_INTERVAL (ex.: 1m) troca o
// horario fixo por um intervalo, pra testar
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

	next, err := scheduler()
	if err != nil {
		log.Fatal(err)
	}

	runAll(ctx)
	for {
		wait := time.Until(next(time.Now()))
		log.Printf("proximo ciclo em %s", wait.Round(time.Second))
		select {
		case <-ctx.Done():
			log.Println("worker encerrado")
			return
		case <-time.After(wait):
			runAll(ctx)
		}
	}
}

// devolve a funcao que calcula o proximo ciclo a partir de agora
func scheduler() (func(time.Time) time.Time, error) {
	if raw := utils.GetEnv("WORKER_INTERVAL", ""); raw != "" {
		interval, err := time.ParseDuration(raw)
		if err != nil {
			return nil, err
		}
		return func(now time.Time) time.Time { return now.Add(interval) }, nil
	}
	at, err := time.Parse("15:04", utils.GetEnv("WORKER_RUN_AT", "00:05"))
	if err != nil {
		return nil, err
	}
	return func(now time.Time) time.Time { return jobs.NextRun(now, at.Hour(), at.Minute()) }, nil
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

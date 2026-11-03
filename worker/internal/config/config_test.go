package config

import (
	"strings"
	"testing"
)

func TestDatabaseURLRequiresPassword(t *testing.T) {
	t.Setenv("DATABASE_PASSWORD", "")
	if _, err := GetDatabaseURL(); err == nil {
		t.Fatal("sem DATABASE_PASSWORD devia dar erro")
	}
}

func TestDatabaseURLEscapesThePassword(t *testing.T) {
	t.Setenv("DATABASE_PASSWORD", "p@ss:w/rd")
	t.Setenv("DATABASE_HOST", "db")
	t.Setenv("DATABASE_SSLMODE", "require")
	got, err := GetDatabaseURL()
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(got, "p%40ss%3Aw%2Frd@db:") || !strings.HasSuffix(got, "?sslmode=require") {
		t.Errorf("endereco: %s", got)
	}
}

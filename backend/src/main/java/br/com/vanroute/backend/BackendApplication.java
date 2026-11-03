package br.com.vanroute.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class BackendApplication {

	// um fuso so pra tudo: as colunas de data e hora nao tem fuso (TIMESTAMP), o app e so do Brasil e o worker
	// tambem grava no horario de Sao Paulo. Sem isso, num container em UTC, o @CreationTimestamp e os now() sem
	// fuso gravariam 3 horas adiantado em relacao ao resto
	static {
		TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
	}

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}

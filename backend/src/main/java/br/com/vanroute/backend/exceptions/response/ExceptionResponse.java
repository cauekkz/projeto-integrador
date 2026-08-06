package br.com.vanroute.backend.exceptions.response;

import java.util.Date;

public record ExceptionResponse(Date timestamp, String message, String details) {
}

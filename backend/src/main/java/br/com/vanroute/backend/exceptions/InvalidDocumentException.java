package br.com.vanroute.backend.exceptions;

// documento enviado (CNH, CRLV) que nao passou na validacao: o handler devolve 422
public class InvalidDocumentException extends RuntimeException {
    public InvalidDocumentException(String message) {
        super(message);
    }
}

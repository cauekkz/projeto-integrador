package br.com.vanroute.backend.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class UserOrDriverOrResponsibleAlreadyRegisteredException extends RuntimeException {
    public UserOrDriverOrResponsibleAlreadyRegisteredException(String message) {
        super(message);
    }
}

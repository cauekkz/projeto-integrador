package br.com.vanroute.backend.exceptions.handler;

import br.com.vanroute.backend.exceptions.BadRequestException;
import br.com.vanroute.backend.exceptions.UserOrDriverOrResponsibleAlreadyRegisteredException;
import br.com.vanroute.backend.exceptions.response.ExceptionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Date;

@ControllerAdvice
@RestController
public class CustomEntityResponseHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(Exception.class)
    public final ResponseEntity<ExceptionResponse> handleAllExceptions(Exception ex, WebRequest request){
        ExceptionResponse response = new ExceptionResponse(new Date(), ex.getMessage(),
                request.getDescription(false));
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(BadRequestException.class)
    public final ResponseEntity<ExceptionResponse> handleBadRequestException(Exception ex, WebRequest request){
        ExceptionResponse response = new ExceptionResponse(new Date(), ex.getMessage(),
                request.getDescription(false));
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UserOrDriverOrResponsibleAlreadyRegisteredException.class)
    public final ResponseEntity<ExceptionResponse> handleUserOrDriverOrResponsibleAlreadyRegisteredException(Exception ex, WebRequest request){
        ExceptionResponse response = new ExceptionResponse(new Date(), ex.getMessage(),
                request.getDescription(false));
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }






}

package br.com.vanroute.backend.exceptions.handler;

import br.com.vanroute.backend.exceptions.BadRequestException;
import br.com.vanroute.backend.exceptions.InvalidCredentialsException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.server.ResponseStatusException;
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

    @ExceptionHandler(IllegalArgumentException.class)
    public final ResponseEntity<ExceptionResponse> handleIllegalArgumentException(Exception ex, WebRequest request){
        return build(ex, request, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public final ResponseEntity<ExceptionResponse> handleInvalidCredentialsException(Exception ex, WebRequest request){
        return build(ex, request, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler({EntityNotFoundException.class, UsernameNotFoundException.class})
    public final ResponseEntity<ExceptionResponse> handleNotFoundException(Exception ex, WebRequest request){
        return build(ex, request, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public final ResponseEntity<ExceptionResponse> handleDataIntegrityViolationException(Exception ex, WebRequest request){
        ExceptionResponse response = new ExceptionResponse(new Date(), "Registro duplicado ou referência inválida.",
                request.getDescription(false));
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public final ResponseEntity<ExceptionResponse> handleResponseStatusException(ResponseStatusException ex, WebRequest request){
        ExceptionResponse response = new ExceptionResponse(new Date(), ex.getReason(),
                request.getDescription(false));
        return new ResponseEntity<>(response, ex.getStatusCode());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public final ResponseEntity<ExceptionResponse> handleAccessDeniedException(Exception ex, WebRequest request){
        return build(ex, request, HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<ExceptionResponse> build(Exception ex, WebRequest request, HttpStatus status){
        ExceptionResponse response = new ExceptionResponse(new Date(), ex.getMessage(),
                request.getDescription(false));
        return new ResponseEntity<>(response, status);
    }
}

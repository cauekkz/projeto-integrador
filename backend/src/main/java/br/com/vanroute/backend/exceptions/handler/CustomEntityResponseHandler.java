package br.com.vanroute.backend.exceptions.handler;

import br.com.vanroute.backend.exceptions.InvalidDocumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@ControllerAdvice
@RestController
public class CustomEntityResponseHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomEntityResponseHandler.class);

    // erro inesperado: o detalhe fica no log; o cliente recebe so uma mensagem generica
    @ExceptionHandler(Exception.class)
    public final ResponseEntity<ExceptionResponse> handleAllExceptions(Exception ex, WebRequest request){
        log.error("Erro inesperado em {}", request.getDescription(false), ex);
        ExceptionResponse response = new ExceptionResponse(new Date(), "Erro interno no servidor",
                request.getDescription(false));
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(InvalidDocumentException.class)
    public final ResponseEntity<ExceptionResponse> handleInvalidDocumentException(Exception ex, WebRequest request){
        return build(ex, request, HttpStatus.UNPROCESSABLE_ENTITY);
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

    // @Valid: em vez do "Invalid request content." do Spring, as mensagens dos proprios campos (ja em portugues),
    // sem repetir e no maximo 3 de uma vez (ex.: "E-mail inválido. A senha é obrigatória.")
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        String message = ex.getBindingResult().getAllErrors().stream()
                .map(ObjectError::getDefaultMessage)
                .filter(Objects::nonNull)
                .map(String::trim)
                .map(m -> m.endsWith(".") ? m : m + ".")
                .distinct()
                .limit(3)
                .collect(Collectors.joining(" "));
        if (message.isBlank()) {
            message = "Confira os dados preenchidos e tente de novo.";
        }
        return ResponseEntity.badRequest().body(new ExceptionResponse(new Date(), message, request.getDescription(false)));
    }

    // JSON quebrado ou campo num formato que nao converte (data, numero...)
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(new ExceptionResponse(new Date(),
                "Alguns dados vieram num formato inválido. Confira e tente de novo.", request.getDescription(false)));
    }

    // arquivo acima do limite (ex.: PDF da CNH no cadastro do motorista)
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex, HttpHeaders headers,
                                                                          HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ExceptionResponse(new Date(),
                "O arquivo é grande demais. Envie um arquivo menor.", request.getDescription(false)));
    }

    private ResponseEntity<ExceptionResponse> build(Exception ex, WebRequest request, HttpStatus status){
        ExceptionResponse response = new ExceptionResponse(new Date(), ex.getMessage(),
                request.getDescription(false));
        return new ResponseEntity<>(response, status);
    }
}

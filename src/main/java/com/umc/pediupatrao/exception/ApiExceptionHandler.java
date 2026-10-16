package com.umc.pediupatrao.exception;

import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Respostas de erro padronizadas para as APIs REST. Não trata AccessDeniedException:
 * ela segue para o Spring Security, que responde 403 e registra a tentativa no log.
 */
@Order(Ordered.HIGHEST_PRECEDENCE) // precisa vir antes do WebExceptionHandler (@RestController também é @Controller)
@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<Map<String, Object>> regraNegocio(RegraNegocioException e) {
        return erro(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> naoEncontrado(RecursoNaoEncontradoException e) {
        return erro(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> conflito(OptimisticLockingFailureException e) {
        return erro(HttpStatus.CONFLICT, "O registro foi alterado por outro usuário. Recarregue e tente novamente.");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Map<String, Object>> duplicado(DuplicateKeyException e) {
        return erro(HttpStatus.CONFLICT, "Registro duplicado.");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, Object>> requisicaoInvalida(Exception e) {
        return erro(HttpStatus.BAD_REQUEST, "Requisição inválida: verifique os campos e valores enviados.");
    }

    private ResponseEntity<Map<String, Object>> erro(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "erro", mensagem));
    }
}

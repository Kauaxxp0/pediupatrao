package com.umc.pediupatrao.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * Para as telas: em vez de uma página de erro, volta para a página anterior
 * exibindo a mensagem da regra de negócio violada.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@ControllerAdvice(annotations = Controller.class)
public class WebExceptionHandler {

    @ExceptionHandler({RegraNegocioException.class, RecursoNaoEncontradoException.class})
    public String regraNegocio(RuntimeException e, HttpServletRequest request) {
        return voltarComErro(request, e.getMessage());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public String parametroInvalido(HttpServletRequest request) {
        return voltarComErro(request, "Valor inválido ou não informado. Verifique os campos do formulário.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public String conflito(HttpServletRequest request) {
        return voltarComErro(request, "O registro foi alterado por outro usuário. Confira os dados e tente novamente.");
    }

    private String voltarComErro(HttpServletRequest request, String mensagem) {
        RequestContextUtils.getOutputFlashMap(request).put("erro", mensagem);
        return "redirect:" + caminhoAnterior(request);
    }

    /** Usa apenas o caminho do Referer (nunca outro host), evitando redirecionamento aberto. */
    private String caminhoAnterior(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null) {
            try {
                URI uri = URI.create(referer);
                if (uri.getPath() != null && uri.getPath().startsWith("/")) {
                    return uri.getPath() + (uri.getQuery() == null ? "" : "?" + uri.getQuery());
                }
            } catch (IllegalArgumentException ignored) {
                // Referer malformado: cai no padrão abaixo
            }
        }
        return "/";
    }
}

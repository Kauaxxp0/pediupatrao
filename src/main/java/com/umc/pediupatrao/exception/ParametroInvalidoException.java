package com.umc.pediupatrao.exception;

/** Parâmetro de consulta inválido (ex.: data final anterior à inicial). Responde 400. */
public class ParametroInvalidoException extends RuntimeException {

    public ParametroInvalidoException(String mensagem) {
        super(mensagem);
    }
}

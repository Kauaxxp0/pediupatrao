package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.CampoAlterado;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Monta a lista de campos alterados comparando valores anteriores e novos. */
public class RegistroAlteracoes {

    private static final DateTimeFormatter FORMATO_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final List<CampoAlterado> campos = new ArrayList<>();

    /** Registra o campo somente se o valor mudou (vazio e nulo são considerados iguais). */
    public RegistroAlteracoes comparar(String campo, Object anterior, Object novo) {
        String a = texto(anterior);
        String n = texto(novo);
        if (!Objects.equals(a, n)) {
            campos.add(new CampoAlterado(campo, a, n));
        }
        return this;
    }

    /** Registra o campo sempre (usado em inclusões, exclusões e mudanças de estado). */
    public RegistroAlteracoes incluir(String campo, Object anterior, Object novo) {
        campos.add(new CampoAlterado(campo, texto(anterior), texto(novo)));
        return this;
    }

    public boolean isVazio() {
        return campos.isEmpty();
    }

    public List<CampoAlterado> lista() {
        return List.copyOf(campos);
    }

    private static String texto(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof LocalDateTime dataHora) {
            return dataHora.format(FORMATO_DATA_HORA);
        }
        String s = valor.toString().trim();
        return s.isEmpty() ? null : s;
    }
}

package com.umc.pediupatrao.dto;

/**
 * Dados aceitos no cadastro/edição de usuário. Usar um DTO (em vez da entidade)
 * impede que a requisição defina campos que não deveria (mass assignment).
 */
public class UsuarioForm {

    private String id;
    private String username;
    private String password;
    private String role;

    public UsuarioForm() {
    }

    public UsuarioForm(String id, String username, String role) {
        this.id = id;
        this.username = username;
        this.role = role;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}

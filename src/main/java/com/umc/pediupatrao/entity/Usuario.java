package com.umc.pediupatrao.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "usuarios") // Define a coleção no MongoDB
public class Usuario {
    @Id
    private String id;

    @Indexed(unique = true)
    private String username; // Nome de usuário para login

    @JsonIgnore
    private String password; // Senha criptografada (BCrypt), nunca serializada

    private String role;     // Perfil do usuário: ADMIN, GERENTE ou ATENDENTE (ver enum Perfil)

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

    @JsonIgnore
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

    public Perfil getPerfil() {
        return Perfil.de(role).orElse(null);
    }
}

package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Cria, na primeira execução, um usuário para cada perfil e um cardápio básico,
 * permitindo demonstrar o sistema. Não sobrescreve dados existentes e não registra senhas no log.
 */
@Configuration
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository usuarios, ProdutoRepository produtos,
            PasswordEncoder encoder, @Value("${app.seed.senha-padrao}") String senhaPadrao) {
        return args -> {
            criarUsuario(usuarios, encoder, "admin", Perfil.ADMIN, senhaPadrao);
            criarUsuario(usuarios, encoder, "gerente01", Perfil.GERENTE, senhaPadrao);
            criarUsuario(usuarios, encoder, "atendente01", Perfil.ATENDENTE, senhaPadrao);

            if (produtos.count() == 0) {
                produtos.save(produto("Pizza Calabresa", "Pizza", 49.90));
                produtos.save(produto("Pizza Marguerita", "Pizza", 47.50));
                produtos.save(produto("Pizza Portuguesa", "Pizza", 54.00));
                produtos.save(produto("Pizza Mussarela", "Pizza", 44.90));
                produtos.save(produto("Pizza Quatro Queijos", "Pizza", 56.90));
                produtos.save(produto("Pizza Frango com Catupiry", "Pizza", 54.90));
                produtos.save(produto("Pizza Pepperoni", "Pizza", 57.90));
                produtos.save(produto("Pizza Napolitana", "Pizza", 49.90));
                produtos.save(produto("Pizza de Chocolate", "Pizza", 52.00));
                produtos.save(produto("Esfiha de Carne", "Esfiha", 6.50));
                produtos.save(produto("Refrigerante 2L", "Bebida", 14.00));
                log.info("Cardápio de demonstração criado.");
            }
        };
    }

    private void criarUsuario(UsuarioRepository repo, PasswordEncoder encoder, String username,
            Perfil perfil, String senha) {
        repo.findByUsername(username).ifPresentOrElse(existente -> {
            // Corrige o admin criado pela versão anterior, que não possuía perfil
            if (Perfil.de(existente.getRole()).isEmpty()) {
                existente.setRole(perfil.name());
                repo.save(existente);
                log.info("Perfil {} atribuído ao usuário existente '{}'.", perfil, username);
            }
        }, () -> {
            Usuario u = new Usuario();
            u.setUsername(username);
            u.setPassword(encoder.encode(senha));
            u.setRole(perfil.name());
            repo.save(u);
            log.info("Usuário de demonstração '{}' ({}) criado.", username, perfil);
        });
    }

    private Produto produto(String nome, String tipo, double preco) {
        Produto p = new Produto();
        p.setNome(nome);
        p.setTipo(tipo);
        p.setPreco(preco);
        p.setAtivo(true);
        return p;
    }
}

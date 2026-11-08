package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Gera um histórico de pedidos de demonstração para o dashboard (uma única vez).
 * Os pedidos passam pelas mesmas regras de domínio (fluxo de status, desconto, cancelamento),
 * mas são gravados direto no repositório, sem auditoria, pois não são operações de usuários.
 * Desative com app.seed.pedidos-demo=false.
 */
@Configuration
@ConditionalOnProperty(name = "app.seed.pedidos-demo", havingValue = "true")
public class DemoDataInitializer {

    static final String MARCADOR = "[demo]";
    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);
    private static final String[] NOMES = {"Ana Lima", "Bruno Costa", "Carla Mendes", "Diego Alves", "Elaine Rocha",
        "Fábio Nunes", "Gabriela Dias", "Henrique Souza", "Isabela Martins", "João Pereira", "Larissa Gomes", "Marcos Ribeiro"};

    @Bean
    @Order(2) // depois do DataInitializer, que cria usuários e produtos
    CommandLineRunner gerarPedidosDemo(PedidoRepository pedidos, ClienteRepository clientes, ProdutoRepository produtos,
            MongoTemplate mongoTemplate, Clock clock, @Value("${app.seed.dias-demo:45}") int dias) {
        return args -> {
            boolean jaGerado = mongoTemplate.exists(
                    Query.query(Criteria.where("observacao").regex("^\\Q" + MARCADOR + "\\E")), Pedido.class);
            List<Produto> ativos = produtos.findAll().stream()
                    .filter(p -> Boolean.TRUE.equals(p.getAtivo()) && p.getPreco() != null && p.getPreco() > 0).toList();
            if (jaGerado || ativos.isEmpty()) {
                return;
            }
            List<Cliente> clientesDemo = criarClientes(clientes);
            List<Pedido> gerados = gerar(ativos, clientesDemo, LocalDateTime.now(clock), dias, new Random(2026));
            pedidos.saveAll(gerados);
            log.info("Dashboard: {} pedidos de demonstração gerados para os últimos {} dias.", gerados.size(), dias);
        };
    }

    private List<Cliente> criarClientes(ClienteRepository repo) {
        List<Cliente> lista = new ArrayList<>();
        for (int i = 0; i < NOMES.length; i++) {
            Cliente c = new Cliente();
            c.setNome(NOMES[i]);
            c.setTelefone(String.format("(11) 9%04d-%04d", 1000 + i * 731 % 9000, 2000 + i * 417 % 8000));
            c.setLogradouro("Rua Exemplo " + (i + 1));
            c.setNumero(String.valueOf(10 + i * 7));
            c.setBairro("Centro");
            c.setCidade("Mogi das Cruzes");
            c.setEstado("SP");
            lista.add(repo.save(c));
        }
        return lista;
    }

    /** Gera os pedidos em memória; separado para manter a regra de geração legível. */
    static List<Pedido> gerar(List<Produto> produtos, List<Cliente> clientes, LocalDateTime agora, int dias, Random rnd) {
        List<Pedido> lista = new ArrayList<>();
        LocalDate hoje = agora.toLocalDate();
        for (int d = dias - 1; d >= 0; d--) {
            LocalDate dia = hoje.minusDays(d);
            boolean fimDeSemana = dia.getDayOfWeek() == DayOfWeek.FRIDAY
                    || dia.getDayOfWeek() == DayOfWeek.SATURDAY || dia.getDayOfWeek() == DayOfWeek.SUNDAY;
            // Leve tendência de crescimento ao longo do período, com mais movimento no fim de semana
            int quantidade = 3 + rnd.nextInt(4) + (fimDeSemana ? 4 + rnd.nextInt(4) : 0) + (dias - d) / 12;
            for (int i = 0; i < quantidade; i++) {
                LocalDateTime entrada = dia.atTime(sortearHora(rnd), rnd.nextInt(60));
                if (entrada.isAfter(agora)) {
                    continue; // não gera pedidos "no futuro" do dia de hoje
                }
                lista.add(criarPedido(produtos, clientes, entrada, agora, rnd));
            }
        }
        return lista;
    }

    private static Pedido criarPedido(List<Produto> produtos, List<Cliente> clientes, LocalDateTime entrada,
            LocalDateTime agora, Random rnd) {
        Cliente cliente = clientes.get(rnd.nextInt(clientes.size()));
        TipoEntrega tipo = rnd.nextInt(100) < 62 ? TipoEntrega.ENTREGA : TipoEntrega.RETIRADA;

        List<ItemPedido> itens = new ArrayList<>();
        List<Produto> disponiveis = new ArrayList<>(produtos);
        int tiposDeItem = 1 + rnd.nextInt(3);
        for (int i = 0; i < tiposDeItem && !disponiveis.isEmpty(); i++) {
            Produto p = sortearProduto(disponiveis, rnd);
            disponiveis.remove(p);
            int qtd = "Esfiha".equalsIgnoreCase(p.getTipo()) ? 2 + rnd.nextInt(6) : 1 + rnd.nextInt(2);
            itens.add(new ItemPedido(p.getId(), p.getNome(), BigDecimal.valueOf(p.getPreco()), qtd));
        }

        Pedido pedido = Pedido.registrar(cliente.getId(), cliente.getNome(), tipo,
                MARCADOR + " pedido gerado para demonstração do dashboard", itens, "atendente01", "ATENDENTE", entrada);

        long minutosDesdeEntrada = java.time.Duration.between(entrada, agora).toMinutes();
        int sorteio = rnd.nextInt(100);
        if (sorteio < 8) { // cancelado antes da saída
            if (rnd.nextBoolean() && minutosDesdeEntrada > 10) {
                pedido.avancarStatus(StatusPedido.EM_PREPARACAO, "atendente01", "ATENDENTE", entrada.plusMinutes(5));
            }
            pedido.cancelar("Cliente desistiu do pedido", "gerente01", "GERENTE", entrada.plusMinutes(Math.min(10, minutosDesdeEntrada)));
            return pedido;
        }
        if (sorteio < 18) {
            pedido.aplicarDesconto(BigDecimal.valueOf(5L + rnd.nextInt(3) * 5L), "gerente01", entrada.plusMinutes(2));
        }
        // Avança pelo fluxo conforme o tempo decorrido (pedidos de hoje ficam em andamento)
        int preparo = 15 + rnd.nextInt(20);
        int entrega = 15 + rnd.nextInt(30);
        avancarSePassou(pedido, StatusPedido.EM_PREPARACAO, entrada.plusMinutes(3), agora, "atendente01", "ATENDENTE");
        avancarSePassou(pedido, StatusPedido.PRONTO, entrada.plusMinutes(3L + preparo), agora, "atendente01", "ATENDENTE");
        StatusPedido saida = tipo == TipoEntrega.ENTREGA ? StatusPedido.SAIU_PARA_ENTREGA : StatusPedido.RETIRADO;
        avancarSePassou(pedido, saida, entrada.plusMinutes(8L + preparo), agora, "atendente01", "ATENDENTE");
        avancarSePassou(pedido, StatusPedido.FINALIZADO, entrada.plusMinutes(8L + preparo + entrega), agora, "atendente01", "ATENDENTE");
        return pedido;
    }

    private static void avancarSePassou(Pedido pedido, StatusPedido destino, LocalDateTime quando, LocalDateTime agora,
            String usuario, String perfil) {
        if (!quando.isAfter(agora) && pedido.getStatus().podeAvancarPara(destino, pedido.getTipoEntrega())) {
            pedido.avancarStatus(destino, usuario, perfil, quando);
        }
    }

    /** Horários de pico: almoço (11h–14h) e jantar (18h–23h). */
    private static int sortearHora(Random rnd) {
        int r = rnd.nextInt(100);
        if (r < 25) {
            return 11 + rnd.nextInt(3);
        }
        if (r < 90) {
            return 18 + rnd.nextInt(5);
        }
        return 15 + rnd.nextInt(3);
    }

    /** Pizzas são mais pedidas que bebidas e esfihas; produtos do início da lista um pouco mais. */
    private static Produto sortearProduto(List<Produto> produtos, Random rnd) {
        List<Produto> pizzas = produtos.stream().filter(p -> "Pizza".equalsIgnoreCase(p.getTipo())).toList();
        if (!pizzas.isEmpty() && rnd.nextInt(100) < 70) {
            int idx = Math.min(pizzas.size() - 1, (int) Math.floor(Math.abs(rnd.nextGaussian()) * pizzas.size() / 2.5));
            return pizzas.get(idx);
        }
        return produtos.get(rnd.nextInt(produtos.size()));
    }
}

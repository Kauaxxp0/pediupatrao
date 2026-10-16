# PediuPatrão — Controle de acesso, gestão de pedidos e auditoria

Atividade Prática M1 (Parte 2) — Engenharia de Software.
Evolução do sistema **PediuPatrão** de uma pizzaria: perfis de acesso, fluxo controlado de pedidos,
descontos e cancelamentos exclusivos do gerente e auditoria persistente no MongoDB.

**Tecnologias:** Java 21 · Spring Boot 3.4 · Spring Security 6 · Spring Data MongoDB · Thymeleaf · JUnit 5/Mockito

---

## 1. Como executar

### Pré-requisitos
- **JDK 21**
- **MongoDB** em `localhost:27017` (MongoDB Community Server) — ou outro servidor via variável `MONGODB_URI`
- Maven não precisa estar instalado (o projeto usa o *Maven Wrapper* `mvnw`)

### Pelo NetBeans
1. `File > Open Project` e selecione a pasta `PediuPatrao`.
2. Clique em **Run** (F6). O arquivo `nbactions.xml` já executa `spring-boot:run`.
3. Acesse <http://localhost:8080>.

### Pelo terminal
```bash
./mvnw spring-boot:run
```
No Windows: `mvnw.cmd spring-boot:run`.

### Configuração do banco
| Variável           | Padrão                      | Uso                                  |
|--------------------|-----------------------------|--------------------------------------|
| `MONGODB_URI`      | `mongodb://127.0.0.1:27017` | URI de conexão (ex.: cluster Atlas)  |
| `MONGODB_DATABASE` | `pediupatrao`               | Nome do banco                        |
| `SEED_SENHA`       | `teste123`                  | Senha dos usuários de demonstração   |

> Para usar o MongoDB Atlas, o IP da máquina precisa estar liberado em *Network Access*;
> caso contrário a conexão falha com `SSLException: internal_error`.

### Usuários de demonstração
Criados automaticamente na primeira execução (com cardápio básico de produtos):

| Usuário       | Perfil    | Senha      |
|---------------|-----------|------------|
| `admin`       | ADMIN     | `teste123` |
| `gerente01`   | GERENTE   | `teste123` |
| `atendente01` | ATENDENTE | `teste123` |

> Senhas de demonstração: altere-as (ou defina `SEED_SENHA`) fora do ambiente acadêmico.

### Testes automatizados
```bash
./mvnw test
```
38 testes: regras do pedido, serviços, auditoria (incluindo detecção de adulteração) e autorização das APIs.

---

## 2. Requisitos implementados

### RF01 — Controle de usuários e permissões
| Perfil    | Pode |
|-----------|------|
| ADMIN     | administrar usuários e perfis, consultar pedidos, consultar auditoria |
| GERENTE   | gerenciar pedidos (avançar status), **aplicar descontos**, **cancelar pedidos**, consultar clientes, consultar auditoria |
| ATENDENTE | cadastrar e atualizar clientes, registrar pedidos e acompanhar o andamento |

- **Autorização no backend em duas camadas:**
  1. regras de URL em `SecurityConfig` (páginas e APIs; qualquer `/api/**` não mapeada é negada);
  2. `@PreAuthorize` nos serviços (`@EnableMethodSecurity`) — vale mesmo para uma rota nova sem regra de URL.
- Desconto e cancelamento: **somente GERENTE**, inclusive em relação ao ADMIN.
- `UsuarioDetailsService` atribui `ROLE_ADMIN`, `ROLE_GERENTE` ou `ROLE_ATENDENTE` conforme o perfil gravado;
  usuário sem perfil válido fica desabilitado.
- **Proteção contra autoelevação:** gestão de usuários exclusiva do ADMIN (a rota `/api/usuarios`, antes pública, foi fechada);
  perfil validado contra o enum `Perfil`; o ADMIN não pode alterar o próprio perfil/username nem se excluir;
  o último ADMIN não pode ser rebaixado nem removido; DTO `UsuarioForm` evita *mass assignment*.
- A API de usuários nunca devolve a senha (`UsuarioResponse` + `@JsonIgnore`).
- **CSRF habilitado** (antes estava desligado). Formulários recebem o token pelo Thymeleaf e as chamadas `fetch`
  enviam o cabeçalho `X-CSRF-TOKEN` (função `csrfHeaders()` em `layout.html`). Logout via POST.
- APIs respondem **401** (não autenticado) e **403** em JSON (perfil sem permissão ou CSRF inválido); telas exibem a página 403.
  Toda tentativa negada é registrada no log com usuário, perfil, método e URL.

### RF02 — Evolução do cadastro de pedidos
- `Pedido` agora possui itens (`ItemPedido`: produto, quantidade, preço unitário, subtotal), subtotal,
  percentual e valor de desconto, **valor total**, tipo (entrega/retirada), responsável e **data/hora de entrada**.
- **Valores calculados no servidor:** a requisição envia apenas cliente, tipo e itens (`produtoId` + `quantidade`);
  o preço vem do cadastro de produtos. Campos como `valorTotal`, `status` ou `registradoPor` enviados pelo navegador são ignorados.
- Validações: cliente existente, ao menos um item, quantidade de 1 a 50, produto ativo e com preço válido;
  itens repetidos do mesmo produto são agrupados.
- `Pedido` não tem setters para valores/status: tudo passa pelos métodos de negócio. Usa `@Version` (bloqueio otimista).

### RF03 — Entrada, saída e transições
Fluxo obrigatório (`StatusPedido`):

```
RECEBIDO → EM_PREPARACAO → PRONTO → SAIU_PARA_ENTREGA (entrega) ─┐
                                  └→ RETIRADO (retirada) ────────┴→ FINALIZADO
CANCELADO: só pela operação de cancelamento do GERENTE, antes da saída
```
- Entrada (RECEBIDO): data/hora e usuário responsável.
- Saída (SAIU_PARA_ENTREGA / RETIRADO): **data/hora e responsável obrigatórios**, registrados automaticamente.
- Finalização: data/hora e usuário.
- Transições inválidas são recusadas (ex.: finalizar pedido em preparação; pedido de retirada "sair para entrega").
- Pedido cancelado não aceita nenhuma alteração. Histórico de todas as mudanças fica no próprio pedido.

### RF04 — Cancelamentos e descontos
- **Cancelamento** (GERENTE): justificativa obrigatória (mín. 10 caracteres), apenas antes da saída; preserva itens,
  valores e histórico; registra quem, quando e o status anterior.
- **Desconto** (GERENTE): percentual, maior que 0 e **até 20%**, apenas antes da saída; recalcula o total no servidor;
  motivo opcional.
- Ambas as operações geram auditoria com valores anteriores e novos.

### RF05 — Auditoria
- Entidade `RegistroAuditoria`, repositório `AuditoriaRepository` e serviço `AuditoriaService` próprios (coleção `auditoria`).
- Auditados: inclusão de pedido, mudanças de status, descontos, cancelamentos, inclusão/alteração/exclusão de clientes
  (campo a campo, inclusive endereço e telefone) e inclusão/alteração/exclusão de usuários.
- Cada evento registra: **usuário e perfil**, **data e hora**, **tipo de operação**, **entidade e ID**,
  **campos alterados com valor anterior e novo** e **justificativa** quando exigida.
- **Consulta exclusiva de ADMIN e GERENTE** (tela `/auditoria` com filtros e paginação, e `GET /api/auditoria`).
- **Integridade:**
  - classe imutável (sem setters) e repositório só com `insert` e consultas — não existe update/delete na aplicação;
  - não há endpoints de alteração/exclusão (`PUT`/`DELETE /api/auditoria/**` → 403);
  - **cadeia de hashes SHA-256**: cada registro guarda o hash do anterior; o botão *Verificar integridade*
    (`GET /api/auditoria/verificar`) detecta registro alterado ou apagado diretamente no banco;
  - sequência com índice único.
- **Proteção de dados:** senhas nunca são auditadas (apenas `senha: [redefinida]`) nem aparecem em logs;
  telefones aparecem mascarados (`****-4321`) para o GERENTE — só o ADMIN vê o valor completo;
  auditoria de pedidos referencia o cliente pelo ID, sem copiar seus dados pessoais.

---

## 3. Principais alterações

| Área | Arquivos |
|------|----------|
| Segurança | `config/SecurityConfig` (regras por perfil, CSRF, 401/403), `service/UsuarioDetailsService`, `security/UsuarioAutenticado` |
| Domínio | `entity/Pedido` (regras de negócio), `ItemPedido`, `StatusPedido`, `TipoEntrega`, `MudancaStatus`, `Cancelamento`, `Perfil` |
| Auditoria | `entity/RegistroAuditoria`, `CampoAlterado`, `TipoOperacao`, `repository/AuditoriaRepository`, `service/AuditoriaService`, `service/RegistroAlteracoes` |
| Serviços | `PedidoService`, `ClienteService`, `UsuarioService`, `ProdutoService` com `@PreAuthorize`, validações e auditoria |
| Controladores | REST: `PedidoController`, `ClienteController`, `UsuarioController`, `AuditoriaApiController`. Telas: `HomeController` dividido em `PedidoWebController`, `ClienteWebController`, `UsuarioWebController`, `ProdutoWebController`, `AuditoriaWebController` |
| Erros | `exception/ApiExceptionHandler` (JSON 400/404/409/422) e `WebExceptionHandler` (mensagem na própria tela) |
| Telas | menu por perfil + logout, novo pedido com itens, detalhe do pedido (andamento, desconto, cancelamento, histórico), auditoria, produtos (tela corrigida), clientes com renderização no servidor |
| Configuração | `application.properties` (URI por variável de ambiente, fuso horário, seed), `DataInitializer` (um usuário por perfil + cardápio), `AppConfig` (relógio único) |

---

## 4. Demonstração — testando o bloqueio no backend

### Script automático (recomendado)
Com o sistema rodando, dê dois cliques em **`testar-permissoes.bat`** (ou rode
`powershell -ExecutionPolicy Bypass -File testar-permissoes.ps1`). O script faz login com os três perfis
direto nas APIs, sem usar a interface, e tenta 25 operações permitidas e proibidas: cancelamento e desconto
por quem não é GERENTE, autoelevação, acesso à auditoria e aos usuários, transição inválida, requisição sem
token CSRF, desconto acima de 20%, alteração de pedido cancelado, exclusão de auditoria e envio de um total
falso. Para cada tentativa, mostra o código HTTP (401/403/422/200) e se o resultado foi o esperado.
As operações executadas também aparecem na tela de **Auditoria**.

### Pelo console do navegador
Com um usuário logado, abra o console do navegador (F12) em qualquer página do sistema e execute
(troque `ID_DO_PEDIDO` por um ID real exibido na tela):

```js
// Logado como ATENDENTE ou ADMIN → 403 (somente GERENTE pode cancelar)
fetch('/api/pedidos/ID_DO_PEDIDO/cancelar', {
  method: 'POST',
  headers: csrfHeaders({'Content-Type': 'application/json'}),
  body: JSON.stringify({justificativa: 'Tentativa sem permissão'})
}).then(r => r.json()).then(console.log);

// Desconto acima do limite, logado como GERENTE → 422
fetch('/api/pedidos/ID_DO_PEDIDO/desconto', {
  method: 'POST',
  headers: csrfHeaders({'Content-Type': 'application/json'}),
  body: JSON.stringify({percentual: 50})
}).then(r => r.json()).then(console.log);

// Transição inválida (RECEBIDO → FINALIZADO) → 422
fetch('/api/pedidos/ID_DO_PEDIDO/status?status=FINALIZADO', {method: 'PUT', headers: csrfHeaders()})
  .then(r => r.json()).then(console.log);

// Sem token CSRF → 403 "Token CSRF ausente ou inválido."
fetch('/api/pedidos/ID_DO_PEDIDO/status?status=EM_PREPARACAO', {method: 'PUT'})
  .then(r => r.json()).then(console.log);

// ATENDENTE tentando ver auditoria ou usuários → 403
fetch('/api/auditoria').then(r => console.log(r.status));
fetch('/api/usuarios').then(r => console.log(r.status));
```

Também é possível digitar a URL de uma tela proibida (ex.: `/usuarios` logado como GERENTE) e ver a página **403**.

<#
    Testa as permissões do PediuPatrão direto nas APIs (backend), sem usar a interface.
    Faz login com cada perfil, tenta operações permitidas e proibidas e mostra o resultado.

    Uso: com o sistema rodando (Run no NetBeans), dê dois cliques em testar-permissoes.bat
         ou execute:  powershell -ExecutionPolicy Bypass -File testar-permissoes.ps1
#>
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Senha = "teste123"
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$script:total = 0
$script:aprovados = 0

function Ler-Utf8($stream) {
    if (-not $stream) { return '' }
    if ($stream.CanSeek) { $stream.Position = 0 }
    $leitor = New-Object System.IO.StreamReader($stream, [Text.Encoding]::UTF8)
    return $leitor.ReadToEnd()
}

# Chamada HTTP que devolve status e corpo, inclusive para respostas 4xx
function Invoke-Api {
    param($Sessao, [string]$Metodo, [string]$Caminho, $Corpo = $null, [switch]$SemCsrf)
    $headers = @{ Accept = 'application/json' }
    if ($Sessao) {
        # A WebSession do PowerShell guarda cabeçalhos de chamadas anteriores: remove o token antes de decidir
        [void]$Sessao.Web.Headers.Remove($Sessao.CsrfHeader)
        if (-not $SemCsrf) { $headers[$Sessao.CsrfHeader] = $Sessao.CsrfToken }
    }
    $params = @{ Uri = "$BaseUrl$Caminho"; Method = $Metodo; Headers = $headers; UseBasicParsing = $true }
    if ($Sessao) { $params.WebSession = $Sessao.Web }
    if ($null -ne $Corpo) {
        $params.ContentType = 'application/json; charset=utf-8'
        $params.Body = [Text.Encoding]::UTF8.GetBytes(($Corpo | ConvertTo-Json -Depth 5 -Compress))
    }
    try {
        $r = Invoke-WebRequest @params
        $status = [int]$r.StatusCode
        $texto = Ler-Utf8 $r.RawContentStream
    } catch [System.Net.WebException] {
        $resp = $_.Exception.Response
        if (-not $resp) { throw }
        $status = [int]$resp.StatusCode
        $texto = Ler-Utf8 $resp.GetResponseStream()
    }
    $obj = $null
    if ($texto) { try { $obj = $texto | ConvertFrom-Json } catch { } }
    [pscustomobject]@{ Status = $status; Corpo = $obj }
}

# Login pelo formulário (com token CSRF), guardando a sessão e o token para as chamadas seguintes
function Entrar([string]$Usuario) {
    $web = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $pagina = Invoke-WebRequest "$BaseUrl/login" -WebSession $web -UseBasicParsing
    $csrf = [regex]::Match($pagina.Content, 'name="_csrf"[^>]*value="([^"]+)"').Groups[1].Value
    $r = Invoke-WebRequest "$BaseUrl/login" -Method Post -WebSession $web -UseBasicParsing `
        -Body @{ username = $Usuario; password = $Senha; _csrf = $csrf }
    if ($r.BaseResponse.ResponseUri.AbsolutePath -like '*login*') {
        throw "Falha no login de '$Usuario'. Verifique a senha."
    }
    [pscustomobject]@{
        Usuario    = $Usuario
        Web        = $web
        CsrfToken  = [regex]::Match($r.Content, 'name="_csrf" content="([^"]+)"').Groups[1].Value
        CsrfHeader = [regex]::Match($r.Content, 'name="_csrf_header" content="([^"]+)"').Groups[1].Value
    }
}

function Rotulo([int]$status) {
    switch ($status) {
        200 { 'PERMITIDO' } 201 { 'PERMITIDO' } 204 { 'PERMITIDO' }
        401 { 'NAO AUTENTICADO' } 403 { 'BLOQUEADO' } 422 { 'RECUSADO (regra)' }
        default { "HTTP $status" }
    }
}

function Verificar([string]$Quem, [string]$Acao, $Resp, [int[]]$Esperado, [string]$Detalhe = '') {
    $script:total++
    $ok = $Esperado -contains $Resp.Status
    if ($ok) { $script:aprovados++ }
    if (-not $Detalhe -and $Resp.Corpo -and $Resp.Corpo.erro) { $Detalhe = $Resp.Corpo.erro }
    $linha = '{0,-12} {1,-44} {2,3} {3,-17}' -f $Quem, $Acao, $Resp.Status, (Rotulo $Resp.Status)
    Write-Host $linha -NoNewline
    if ($ok) { Write-Host ' OK ' -ForegroundColor Black -BackgroundColor Green -NoNewline }
    else { Write-Host ' FALHOU ' -ForegroundColor White -BackgroundColor Red -NoNewline }
    if ($Detalhe) { Write-Host "  $Detalhe" -ForegroundColor DarkGray } else { Write-Host '' }
}

function Titulo([string]$texto) {
    Write-Host ''
    Write-Host "=== $texto ===" -ForegroundColor Cyan
}

# ---------------------------------------------------------------------------
try { Invoke-WebRequest "$BaseUrl/login" -UseBasicParsing | Out-Null }
catch {
    Write-Host "O sistema nao esta respondendo em $BaseUrl." -ForegroundColor Red
    Write-Host "Inicie o projeto no NetBeans (Run) e rode este teste novamente."
    exit 1
}

Write-Host ''
Write-Host 'PediuPatrao - teste de permissoes no backend (APIs)' -ForegroundColor Yellow
Write-Host ('{0,-12} {1,-44} {2,3} {3,-17}' -f 'USUARIO', 'TENTATIVA', 'HTTP', 'RESULTADO')

$atendente = Entrar 'atendente01'
$gerente = Entrar 'gerente01'
$admin = Entrar 'admin'

# ----- Preparação: o atendente registra os pedidos usados nos testes -----
Titulo 'Preparacao (ATENDENTE registra pedidos)'
$clientes = (Invoke-Api $atendente GET '/api/clientes').Corpo
if (-not $clientes) {
    $novo = Invoke-Api $atendente POST '/api/clientes' @{ nome = 'Cliente Teste'; telefone = '(11) 90000-0000';
        logradouro = 'Rua de Teste'; numero = '1'; bairro = 'Centro'; cidade = 'Sao Paulo'; estado = 'SP' }
    Verificar 'atendente01' 'cadastrar cliente' $novo @(201)
    $clientes = @($novo.Corpo)
}
$cliente = @($clientes)[0]
$produtos = @((Invoke-Api $atendente GET '/api/produtos').Corpo | Where-Object { $_.ativo -and $_.preco -gt 0 })
if ($produtos.Count -lt 2) { throw 'Cadastre ao menos 2 produtos ativos antes de rodar o teste.' }
$p1 = $produtos[0]; $p2 = $produtos[1]
$esperado = [math]::Round($p1.preco * 2 + $p2.preco, 2)

# Envia campos que o servidor deve ignorar (total, status e responsável)
$r = Invoke-Api $atendente POST '/api/pedidos' @{
    clienteId = $cliente.id; tipoEntrega = 'ENTREGA'
    itens = @(@{ produtoId = $p1.id; quantidade = 2 }, @{ produtoId = $p2.id; quantidade = 1 })
    valorTotal = 0.01; status = 'FINALIZADO'; registradoPor = 'hacker'
}
$pedido = $r.Corpo
Verificar 'atendente01' 'registrar pedido (enviando total=0,01)' $r @(201) `
    ("total calculado no servidor: R$ {0:N2} | status {1} | por {2}" -f $pedido.valorTotal, $pedido.status, $pedido.registradoPor)
$script:total++
if ([math]::Abs($pedido.valorTotal - $esperado) -lt 0.01 -and $pedido.status -eq 'RECEBIDO' -and $pedido.registradoPor -eq 'atendente01') {
    $script:aprovados++
    Write-Host ('{0,-12} {1,-44}' -f '', 'valores do navegador ignorados') -NoNewline
    Write-Host ' OK ' -ForegroundColor Black -BackgroundColor Green
} else {
    Write-Host 'valores enviados pelo navegador NAO foram ignorados' -ForegroundColor Red
}
$pedido2 = (Invoke-Api $atendente POST '/api/pedidos' @{
    clienteId = $cliente.id; tipoEntrega = 'RETIRADA'; itens = @(@{ produtoId = $p1.id; quantidade = 1 }, @{ produtoId = $p2.id; quantidade = 1 })
}).Corpo
$idPedido = $pedido.id
$idPedido2 = $pedido2.id

# ----- Sem login -----
Titulo 'Sem login'
Verificar 'anonimo' 'listar pedidos' (Invoke-Api $null GET '/api/pedidos') @(401)

# ----- ATENDENTE -----
Titulo 'ATENDENTE'
Verificar 'atendente01' 'cancelar pedido' (Invoke-Api $atendente POST "/api/pedidos/$idPedido/cancelar" @{ justificativa = 'tentativa sem permissao' }) @(403)
Verificar 'atendente01' 'aplicar desconto de 10%' (Invoke-Api $atendente POST "/api/pedidos/$idPedido/desconto" @{ percentual = 10 }) @(403)
Verificar 'atendente01' 'consultar auditoria' (Invoke-Api $atendente GET '/api/auditoria') @(403)
Verificar 'atendente01' 'listar usuarios' (Invoke-Api $atendente GET '/api/usuarios') @(403)
Verificar 'atendente01' 'criar usuario ADMIN (autoelevacao)' (Invoke-Api $atendente POST '/api/usuarios' @{ username = 'hacker'; password = '123456'; role = 'ADMIN' }) @(403)
Verificar 'atendente01' 'pular etapa: RECEBIDO -> FINALIZADO' (Invoke-Api $atendente PUT "/api/pedidos/$idPedido/status?status=FINALIZADO") @(422)
Verificar 'atendente01' 'alterar status SEM token CSRF' (Invoke-Api $atendente PUT "/api/pedidos/$idPedido/status?status=EM_PREPARACAO" -SemCsrf) @(403)
Verificar 'atendente01' 'avancar: RECEBIDO -> EM_PREPARACAO' (Invoke-Api $atendente PUT "/api/pedidos/$idPedido/status?status=EM_PREPARACAO") @(200)

# ----- ADMIN -----
Titulo 'ADMIN'
Verificar 'admin' 'aplicar desconto de 10%' (Invoke-Api $admin POST "/api/pedidos/$idPedido/desconto" @{ percentual = 10 }) @(403)
Verificar 'admin' 'cancelar pedido' (Invoke-Api $admin POST "/api/pedidos/$idPedido/cancelar" @{ justificativa = 'admin tentando cancelar' }) @(403)
Verificar 'admin' 'registrar pedido' (Invoke-Api $admin POST '/api/pedidos' @{ clienteId = $cliente.id; tipoEntrega = 'ENTREGA'; itens = @(@{ produtoId = $p1.id; quantidade = 1 }) }) @(403)
Verificar 'admin' 'consultar pedidos' (Invoke-Api $admin GET '/api/pedidos') @(200)
$idAdmin = (@((Invoke-Api $admin GET '/api/usuarios').Corpo) | Where-Object { $_.username -eq 'admin' }).id
Verificar 'admin' 'alterar o proprio perfil para GERENTE' (Invoke-Api $admin PUT "/api/usuarios/$idAdmin" @{ username = 'admin'; role = 'GERENTE' }) @(422)

# ----- GERENTE -----
Titulo 'GERENTE'
Verificar 'gerente01' 'listar usuarios' (Invoke-Api $gerente GET '/api/usuarios') @(403)
Verificar 'gerente01' 'alterar dados de cliente' (Invoke-Api $gerente PUT "/api/clientes/$($cliente.id)" @{ nome = 'Alterado'; telefone = '(11) 91111-1111' }) @(403)
Verificar 'gerente01' 'desconto de 25% (limite e 20%)' (Invoke-Api $gerente POST "/api/pedidos/$idPedido/desconto" @{ percentual = 25 }) @(422)
$r = Invoke-Api $gerente POST "/api/pedidos/$idPedido/desconto" @{ percentual = 10; motivo = 'Teste automatizado' }
Verificar 'gerente01' 'desconto de 10%' $r @(200) ("novo total: R$ {0:N2}" -f $r.Corpo.valorTotal)
Verificar 'gerente01' 'cancelar sem justificativa' (Invoke-Api $gerente POST "/api/pedidos/$idPedido2/cancelar" @{ justificativa = 'nao' }) @(422)
Verificar 'gerente01' 'cancelar com justificativa' (Invoke-Api $gerente POST "/api/pedidos/$idPedido2/cancelar" @{ justificativa = 'Cliente desistiu (teste automatizado)' }) @(200)
Verificar 'gerente01' 'alterar status de pedido cancelado' (Invoke-Api $gerente PUT "/api/pedidos/$idPedido2/status?status=EM_PREPARACAO") @(422)
Verificar 'gerente01' 'apagar registro de auditoria' (Invoke-Api $gerente DELETE '/api/auditoria/1') @(403)
$r = Invoke-Api $gerente GET '/api/auditoria/verificar'
Verificar 'gerente01' 'verificar integridade da auditoria' $r @(200) $r.Corpo.mensagem

# ----- Resumo -----
Write-Host ''
$cor = if ($script:aprovados -eq $script:total) { 'Green' } else { 'Red' }
Write-Host ("Resultado: {0} de {1} verificacoes OK" -f $script:aprovados, $script:total) -ForegroundColor $cor
Write-Host "Pedidos usados no teste: $idPedido e $idPedido2 (veja em Pedidos e na Auditoria)." -ForegroundColor DarkGray

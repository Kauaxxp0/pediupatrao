<#
    Testa os endpoints da API do dashboard com o sistema rodando e o banco real.
    Faz login como GERENTE, chama cada endpoint (com e sem filtros), testa os erros
    de validação e o bloqueio de acesso do ATENDENTE. Gera também um relatório em
    docs/evidencias/teste-api-dashboard.txt.

    Uso: com o sistema rodando, dê dois cliques em testar-dashboard-api.bat
#>
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Senha = "teste123"
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$script:total = 0
$script:aprovados = 0
$script:relatorio = New-Object System.Collections.Generic.List[string]

function Ler-Utf8($stream) {
    if (-not $stream) { return '' }
    if ($stream.CanSeek) { $stream.Position = 0 }
    return (New-Object System.IO.StreamReader($stream, [Text.Encoding]::UTF8)).ReadToEnd()
}

function Invoke-Api($Sessao, [string]$Caminho) {
    $params = @{ Uri = "$BaseUrl$Caminho"; Method = 'GET'; Headers = @{ Accept = 'application/json' }; UseBasicParsing = $true }
    if ($Sessao) { $params.WebSession = $Sessao }
    $inicio = Get-Date
    try {
        $r = Invoke-WebRequest @params
        $status = [int]$r.StatusCode; $texto = Ler-Utf8 $r.RawContentStream
    } catch [System.Net.WebException] {
        $resp = $_.Exception.Response
        if (-not $resp) { throw }
        $status = [int]$resp.StatusCode; $texto = Ler-Utf8 $resp.GetResponseStream()
    }
    $ms = [int]((Get-Date) - $inicio).TotalMilliseconds
    $obj = $null
    if ($texto) { try { $obj = $texto | ConvertFrom-Json } catch { } }
    [pscustomobject]@{ Status = $status; Corpo = $obj; Ms = $ms }
}

function Entrar([string]$Usuario) {
    $web = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $pagina = Invoke-WebRequest "$BaseUrl/login" -WebSession $web -UseBasicParsing
    $csrf = [regex]::Match($pagina.Content, 'name="_csrf"[^>]*value="([^"]+)"').Groups[1].Value
    $r = Invoke-WebRequest "$BaseUrl/login" -Method Post -WebSession $web -UseBasicParsing `
        -Body @{ username = $Usuario; password = $Senha; _csrf = $csrf }
    if ($r.BaseResponse.ResponseUri.AbsolutePath -like '*login*') { throw "Falha no login de '$Usuario'." }
    return $web
}

function Registrar([string]$linha, [string]$cor = 'Gray') {
    Write-Host $linha -ForegroundColor $cor
    $script:relatorio.Add($linha)
}

function Verificar([string]$Teste, $Resp, [int]$Esperado, [string]$Detalhe) {
    $script:total++
    $ok = $Resp.Status -eq $Esperado
    if ($ok) { $script:aprovados++ }
    if (-not $Detalhe -and $Resp.Corpo -and $Resp.Corpo.erro) { $Detalhe = $Resp.Corpo.erro }
    $marca = if ($ok) { 'OK    ' } else { 'FALHOU' }
    Registrar ('[{0}] {1,-58} HTTP {2} ({3} ms)' -f $marca, $Teste, $Resp.Status, $Resp.Ms) $(if ($ok) { 'Green' } else { 'Red' })
    if ($Detalhe) { Registrar ('         -> ' + $Detalhe) 'DarkGray' }
}

function Moeda($v) { 'R$ {0:N2}' -f [double]$v }

# ---------------------------------------------------------------------------
try { Invoke-WebRequest "$BaseUrl/login" -UseBasicParsing | Out-Null }
catch { Write-Host "O sistema nao esta respondendo em $BaseUrl. Inicie o projeto (Run) e tente de novo." -ForegroundColor Red; exit 1 }

Registrar "PediuPatrao - teste da API do dashboard ($(Get-Date -Format 'dd/MM/yyyy HH:mm:ss'))" 'Yellow'
$gerente = Entrar 'gerente01'
$atendente = Entrar 'atendente01'

Registrar ''
Registrar '=== Endpoints (GERENTE, periodo padrao: ultimos 30 dias) ===' 'Cyan'
$r = Invoke-Api $gerente '/api/dashboard/resumo'
Verificar 'GET /api/dashboard/resumo' $r 200 ("faturamento {0} | {1} pedidos | ticket medio {2} | cancelamento {3}% | variacao {4}%" -f (Moeda $r.Corpo.faturamento), $r.Corpo.totalPedidos, (Moeda $r.Corpo.ticketMedio), $r.Corpo.taxaCancelamento, $r.Corpo.comparativo.variacaoFaturamento)

$r = Invoke-Api $gerente '/api/dashboard/vendas?agrupamento=DIA'
Verificar 'GET /api/dashboard/vendas?agrupamento=DIA' $r 200 ("{0} pontos na serie (dias sem venda aparecem com zero)" -f @($r.Corpo.pontos).Count)

$r = Invoke-Api $gerente '/api/dashboard/vendas?agrupamento=SEMANA'
Verificar 'GET /api/dashboard/vendas?agrupamento=SEMANA' $r 200 ("{0} semanas" -f @($r.Corpo.pontos).Count)

$r = Invoke-Api $gerente '/api/dashboard/vendas-por-hora'
$pico = @($r.Corpo.horas) | Sort-Object pedidos -Descending | Select-Object -First 1
Verificar 'GET /api/dashboard/vendas-por-hora' $r 200 ("horario de pico: {0}h com {1} pedidos" -f $pico.hora, $pico.pedidos)

$r = Invoke-Api $gerente '/api/dashboard/situacao-pedidos'
Verificar 'GET /api/dashboard/situacao-pedidos' $r 200 ("{0} pedidos, {1} em andamento, tempo medio ate a saida: {2} min" -f $r.Corpo.total, $r.Corpo.emAndamento, $r.Corpo.tempoMedioAteSaidaMinutos)

$r = Invoke-Api $gerente '/api/dashboard/produtos-mais-vendidos?limite=3'
$top = @($r.Corpo.produtos) | ForEach-Object { "$($_.posicao). $($_.nome) ($($_.quantidade) un)" }
Verificar 'GET /api/dashboard/produtos-mais-vendidos?limite=3' $r 200 ($top -join ' | ')

$r = Invoke-Api $gerente '/api/dashboard/produtos-mais-vendidos?ordenarPor=FATURAMENTO&limite=3'
Verificar 'GET .../produtos-mais-vendidos?ordenarPor=FATURAMENTO' $r 200 ("1o por faturamento: {0} ({1})" -f $r.Corpo.produtos[0].nome, (Moeda $r.Corpo.produtos[0].faturamento))

$r = Invoke-Api $gerente '/api/dashboard/pedidos-recentes?limite=5'
Verificar 'GET /api/dashboard/pedidos-recentes?limite=5' $r 200 ("{0} pedidos retornados" -f @($r.Corpo).Count)

$r = Invoke-Api $gerente '/api/dashboard/filtros'
Verificar 'GET /api/dashboard/filtros' $r 200 ("{0} status, {1} tipos de entrega, padrao {2} a {3}" -f @($r.Corpo.status).Count, @($r.Corpo.tiposEntrega).Count, $r.Corpo.padrao.inicio, $r.Corpo.padrao.fim)

Registrar ''
Registrar '=== Filtros (RF05) ===' 'Cyan'
$hoje = Get-Date
$ini7 = $hoje.AddDays(-6).ToString('yyyy-MM-dd'); $fim = $hoje.ToString('yyyy-MM-dd')
$r = Invoke-Api $gerente "/api/dashboard/resumo?inicio=$ini7&fim=$fim"
Verificar "resumo com periodo de 7 dias ($ini7 a $fim)" $r 200 ("{0} pedidos, faturamento {1}" -f $r.Corpo.totalPedidos, (Moeda $r.Corpo.faturamento))
$r = Invoke-Api $gerente '/api/dashboard/resumo?tipoEntrega=RETIRADA'
Verificar 'resumo filtrando tipoEntrega=RETIRADA' $r 200 ("entrega: {0} | retirada: {1}" -f $r.Corpo.pedidosEntrega, $r.Corpo.pedidosRetirada)
$r = Invoke-Api $gerente '/api/dashboard/resumo?status=CANCELADO'
Verificar 'resumo filtrando status=CANCELADO' $r 200 ("{0} pedidos cancelados, faturamento {1} (cancelados nao faturam)" -f $r.Corpo.pedidosCancelados, (Moeda $r.Corpo.faturamento))

Registrar ''
Registrar '=== Tratamento de erros (RF07) ===' 'Cyan'
Verificar 'fim anterior ao inicio' (Invoke-Api $gerente '/api/dashboard/resumo?inicio=2026-11-08&fim=2026-11-01') 400
Verificar 'periodo maior que 366 dias' (Invoke-Api $gerente '/api/dashboard/resumo?inicio=2024-01-01&fim=2026-01-01') 400
Verificar 'status inexistente (status=ENTREGUE)' (Invoke-Api $gerente '/api/dashboard/resumo?status=ENTREGUE') 400
Verificar 'data em formato errado (inicio=08/11/2026)' (Invoke-Api $gerente '/api/dashboard/resumo?inicio=08/11/2026') 400
Verificar 'limite fora da faixa (limite=100)' (Invoke-Api $gerente '/api/dashboard/produtos-mais-vendidos?limite=100') 400

Registrar ''
Registrar '=== Controle de acesso ===' 'Cyan'
Verificar 'sem login' (Invoke-Api $null '/api/dashboard/resumo') 401
Verificar 'ATENDENTE tentando acessar o dashboard' (Invoke-Api $atendente '/api/dashboard/resumo') 403

Registrar ''
$cor = if ($script:aprovados -eq $script:total) { 'Green' } else { 'Red' }
Registrar ("Resultado: {0} de {1} testes OK" -f $script:aprovados, $script:total) $cor

$pasta = Join-Path $PSScriptRoot 'docs\evidencias'
New-Item -ItemType Directory -Force $pasta | Out-Null
[IO.File]::WriteAllLines((Join-Path $pasta 'teste-api-dashboard.txt'), $script:relatorio, (New-Object Text.UTF8Encoding $true))
Write-Host "Relatorio salvo em docs\evidencias\teste-api-dashboard.txt" -ForegroundColor DarkGray

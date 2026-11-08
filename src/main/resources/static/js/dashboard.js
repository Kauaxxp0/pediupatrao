/*
 * Dashboard administrativo (RF06): todos os dados vêm da API /api/dashboard.
 * Cada bloco é carregado de forma independente: se um endpoint falhar,
 * apenas aquele bloco mostra o erro e o restante da tela continua funcionando (RF07).
 */
(function () {
    'use strict';

    const API = '/api/dashboard';
    const INTERVALO_ATUALIZACAO_MS = 60000;
    const CHAVE_AUTO = 'dashboard.autoAtualizar';

    const CORES_STATUS = {
        RECEBIDO: '#6c757d', EM_PREPARACAO: '#ffc107', PRONTO: '#0dcaf0', SAIU_PARA_ENTREGA: '#0d6efd',
        RETIRADO: '#6610f2', FINALIZADO: '#198754', CANCELADO: '#dc3545'
    };
    const CLASSES_STATUS = {
        RECEBIDO: 'bg-secondary', EM_PREPARACAO: 'bg-warning text-dark', PRONTO: 'bg-info text-dark',
        SAIU_PARA_ENTREGA: 'bg-primary', RETIRADO: 'bg-primary', FINALIZADO: 'bg-success', CANCELADO: 'bg-danger'
    };

    const $ = id => document.getElementById(id);
    const moeda = v => Number(v || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
    const numero = v => Number(v || 0).toLocaleString('pt-BR');
    const porcento = v => Number(v || 0).toLocaleString('pt-BR', { maximumFractionDigits: 1 }) + '%';
    const graficos = {};
    let timer = null;

    // ---------- Comunicação com a API ----------

    class ErroApi extends Error {
        constructor(status, mensagem) { super(mensagem); this.status = status; }
    }

    async function buscar(caminho, extras) {
        const params = new URLSearchParams(filtrosAtuais());
        Object.entries(extras || {}).forEach(([k, v]) => v && params.set(k, v));
        let resp;
        try {
            resp = await fetch(`${API}/${caminho}?${params}`, { headers: { Accept: 'application/json' } });
        } catch (e) {
            throw new ErroApi(0, 'Não foi possível conectar ao servidor. Verifique se o sistema está em execução.');
        }
        if (resp.status === 401) { // sessão expirada
            window.location.href = '/login?expirado';
            throw new ErroApi(401, 'Sessão expirada.');
        }
        const corpo = await resp.json().catch(() => null);
        if (!resp.ok) {
            throw new ErroApi(resp.status, (corpo && corpo.erro) || `Erro ${resp.status} ao consultar ${caminho}.`);
        }
        return corpo;
    }

    // ---------- Filtros (RF05) ----------

    function filtrosAtuais() {
        const f = { inicio: $('fInicio').value, fim: $('fFim').value };
        if ($('fTipo').value) f.tipoEntrega = $('fTipo').value;
        if ($('fStatus').value) f.status = $('fStatus').value;
        return f;
    }

    const isoLocal = d => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);

    function aplicarAtalho(atalho) {
        const hoje = new Date();
        let inicio = new Date(hoje);
        if (atalho === '7') inicio.setDate(hoje.getDate() - 6);
        else if (atalho === '30') inicio.setDate(hoje.getDate() - 29);
        else if (atalho === 'mes') inicio = new Date(hoje.getFullYear(), hoje.getMonth(), 1);
        $('fInicio').value = isoLocal(inicio);
        $('fFim').value = isoLocal(hoje);
        carregarTudo();
    }

    function validarFiltros() {
        const erro = $('erroFiltro');
        const { inicio, fim } = filtrosAtuais();
        let msg = '';
        if (!inicio || !fim) msg = 'Informe a data inicial e a data final.';
        else if (fim < inicio) msg = 'A data final não pode ser anterior à data inicial.';
        erro.textContent = msg;
        erro.classList.toggle('d-none', !msg);
        return !msg;
    }

    async function carregarOpcoes() {
        const opcoes = await buscar('filtros');
        const preencher = (select, lista) => lista.forEach(o => select.add(new Option(o.descricao, o.valor)));
        preencher($('fTipo'), opcoes.tiposEntrega);
        preencher($('fStatus'), opcoes.status);
        $('fInicio').value = opcoes.padrao.inicio;
        $('fFim').value = opcoes.padrao.fim;
    }

    // ---------- Estados visuais ----------

    function carregando(cardId, ativo) {
        const card = $(cardId);
        let overlay = card.querySelector('.carregando');
        if (ativo && !overlay) {
            overlay = document.createElement('div');
            overlay.className = 'carregando';
            overlay.innerHTML = '<div class="spinner-border text-primary" role="status"><span class="visually-hidden">Carregando</span></div>';
            card.appendChild(overlay);
        } else if (!ativo && overlay) {
            overlay.remove();
        }
    }

    function mensagemNoCard(cardId, texto, erro) {
        const area = $(cardId).querySelector('.area-grafico');
        let msg = $(cardId).querySelector('.estado');
        if (!texto) {
            if (msg) msg.remove();
            if (area) area.classList.remove('d-none');
            return;
        }
        if (!msg) {
            msg = document.createElement('div');
            msg.className = 'estado';
            $(cardId).querySelector('.card-body').prepend(msg);
        }
        msg.className = 'estado' + (erro ? ' erro' : '');
        msg.innerHTML = `<i class="bi ${erro ? 'bi-exclamation-triangle' : 'bi-inbox'} fs-3 d-block mb-1"></i>`;
        msg.append(document.createTextNode(texto));
        if (area) area.classList.add('d-none');
    }

    function desenhar(id, config) {
        if (graficos[id]) graficos[id].destroy();
        graficos[id] = new Chart($(id), config);
    }

    async function bloco(cardId, carregar) {
        carregando(cardId, true);
        try {
            mensagemNoCard(cardId, null);
            await carregar();
        } catch (e) {
            mensagemNoCard(cardId, e.message, true);
            throw e;
        } finally {
            carregando(cardId, false);
        }
    }

    // ---------- RF01 — Indicadores ----------

    function variacao(elId, valor, sufixo) {
        const el = $(elId);
        if (valor === null || valor === undefined) {
            el.className = 'variacao text-muted';
            el.textContent = 'sem base no período anterior';
            return;
        }
        const sobe = valor >= 0;
        el.className = 'variacao ' + (sobe ? 'text-success' : 'text-danger');
        el.innerHTML = `<i class="bi ${sobe ? 'bi-arrow-up-right' : 'bi-arrow-down-right'}"></i> `;
        el.append(document.createTextNode(`${porcento(Math.abs(valor))} ${sufixo}`));
    }

    async function carregarResumo() {
        const erro = $('erroResumo');
        try {
            const r = await buscar('resumo');
            $('kFaturamento').textContent = moeda(r.faturamento);
            $('kPedidos').textContent = numero(r.totalPedidos);
            $('kTicket').textContent = moeda(r.ticketMedio);
            $('kCancelados').textContent = numero(r.pedidosCancelados);
            $('vCancelados').textContent = `${porcento(r.taxaCancelamento)} dos pedidos`;
            $('kDescontos').textContent = moeda(r.descontosConcedidos);
            $('vItens').textContent = `${numero(r.itensVendidos)} itens vendidos`;
            $('kAndamento').textContent = numero(r.pedidosEmAndamento);
            const totalTipo = r.pedidosEntrega + r.pedidosRetirada;
            $('vTipo').textContent = `${numero(r.pedidosEntrega)} entrega · ${numero(r.pedidosRetirada)} retirada`;
            $('barEntrega').style.width = totalTipo ? (100 * r.pedidosEntrega / totalTipo) + '%' : '0%';
            $('barRetirada').style.width = totalTipo ? (100 * r.pedidosRetirada / totalTipo) + '%' : '0%';
            const c = r.comparativo;
            variacao('vFaturamento', c.variacaoFaturamento, 'vs. período anterior');
            variacao('vPedidos', c.variacaoPedidos, 'vs. período anterior');
            variacao('vTicket', c.variacaoTicketMedio, 'vs. período anterior');
            erro.classList.add('d-none');
        } catch (e) {
            // Não mantém valores antigos na tela quando a consulta falha
            ['kFaturamento', 'kPedidos', 'kTicket', 'kCancelados', 'kDescontos', 'kAndamento']
                .forEach(id => { $(id).textContent = '—'; });
            ['vFaturamento', 'vPedidos', 'vTicket', 'vCancelados', 'vItens', 'vTipo']
                .forEach(id => { $(id).textContent = ''; });
            $('barEntrega').style.width = '0%';
            $('barRetirada').style.width = '0%';
            erro.textContent = 'Indicadores indisponíveis: ' + e.message;
            erro.classList.remove('d-none');
            throw e;
        }
    }

    // ---------- RF02 — Vendas ----------

    async function carregarVendas() {
        await bloco('cardVendas', async () => {
            const s = await buscar('vendas', { agrupamento: $('fAgrupamento').value });
            if (!s.pontos.some(p => p.pedidos > 0)) {
                mensagemNoCard('cardVendas', 'Nenhuma venda no período selecionado.');
                return;
            }
            desenhar('gVendas', {
                data: {
                    labels: s.pontos.map(p => p.rotulo),
                    datasets: [
                        { type: 'bar', label: 'Faturamento (R$)', data: s.pontos.map(p => p.faturamento),
                          backgroundColor: 'rgba(25,135,84,.55)', yAxisID: 'y' },
                        { type: 'line', label: 'Pedidos', data: s.pontos.map(p => p.pedidos), borderColor: '#0d6efd',
                          backgroundColor: '#0d6efd', tension: .3, yAxisID: 'y1' }
                    ]
                },
                options: {
                    maintainAspectRatio: false, interaction: { mode: 'index', intersect: false },
                    plugins: { tooltip: { callbacks: { label: ctx => ctx.dataset.yAxisID === 'y'
                        ? ` Faturamento: ${moeda(ctx.raw)}` : ` Pedidos: ${ctx.raw}` } } },
                    scales: {
                        y: { beginAtZero: true, ticks: { callback: v => moeda(v).replace(',00', '') } },
                        y1: { beginAtZero: true, position: 'right', grid: { drawOnChartArea: false }, ticks: { precision: 0 } }
                    }
                }
            });
        });
    }

    async function carregarHoras() {
        await bloco('cardHoras', async () => {
            const h = await buscar('vendas-por-hora');
            const comPedidos = h.horas.filter(p => p.pedidos > 0);
            if (!comPedidos.length) {
                mensagemNoCard('cardHoras', 'Nenhum pedido no período selecionado.');
                return;
            }
            // Mostra apenas a faixa de funcionamento (primeira à última hora com pedidos)
            const ini = comPedidos[0].hora, fim = comPedidos[comPedidos.length - 1].hora;
            const faixa = h.horas.filter(p => p.hora >= ini && p.hora <= fim);
            const max = Math.max(...faixa.map(p => p.pedidos));
            desenhar('gHoras', {
                type: 'bar',
                data: {
                    labels: faixa.map(p => `${p.hora}h`),
                    datasets: [{ label: 'Pedidos', data: faixa.map(p => p.pedidos),
                        backgroundColor: faixa.map(p => p.pedidos === max ? '#fd7e14' : 'rgba(13,110,253,.6)') }]
                },
                options: {
                    maintainAspectRatio: false, plugins: { legend: { display: false },
                        tooltip: { callbacks: { afterLabel: ctx => `Faturamento: ${moeda(faixa[ctx.dataIndex].faturamento)}` } } },
                    scales: { y: { beginAtZero: true, ticks: { precision: 0 } } }
                }
            });
        });
    }

    // ---------- RF03 — Situação dos pedidos ----------

    const minutos = m => m === null || m === undefined ? '—' : (m >= 60 ? `${Math.floor(m / 60)}h ${m % 60}min` : `${m} min`);

    async function carregarSituacao() {
        $('tSaida').textContent = '—';
        $('tFinal').textContent = '—';
        await bloco('cardStatus', async () => {
            const s = await buscar('situacao-pedidos');
            $('tSaida').textContent = minutos(s.tempoMedioAteSaidaMinutos);
            $('tFinal').textContent = minutos(s.tempoMedioAteFinalizacaoMinutos);
            const comValor = s.porStatus.filter(x => x.quantidade > 0);
            if (!comValor.length) {
                mensagemNoCard('cardStatus', 'Nenhum pedido no período selecionado.');
                return;
            }
            desenhar('gStatus', {
                type: 'doughnut',
                data: {
                    labels: comValor.map(x => x.descricao),
                    datasets: [{ data: comValor.map(x => x.quantidade), backgroundColor: comValor.map(x => CORES_STATUS[x.status]) }]
                },
                options: {
                    maintainAspectRatio: false, cutout: '60%',
                    plugins: { legend: { position: 'right', labels: { boxWidth: 12, font: { size: 11 } } },
                        tooltip: { callbacks: { label: ctx => ` ${ctx.label}: ${ctx.raw} (${porcento(comValor[ctx.dataIndex].percentual)})` } } }
                }
            });
        });
    }

    async function carregarRecentes() {
        const corpo = $('tRecentes');
        try {
            const lista = await buscar('pedidos-recentes', { limite: '8' });
            corpo.replaceChildren();
            if (!lista.length) {
                corpo.innerHTML = '<tr><td colspan="6" class="estado">Nenhum pedido no período selecionado.</td></tr>';
                return;
            }
            lista.forEach(p => {
                const tr = document.createElement('tr');
                tr.style.cursor = 'pointer';
                tr.addEventListener('click', () => { window.location.href = `/pedidos/${encodeURIComponent(p.id)}`; });
                const data = new Date(p.dataHoraEntrada);
                const celulas = [
                    data.toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }),
                    p.clienteNome || '—',
                    p.tipoEntrega === 'ENTREGA' ? 'Entrega' : 'Retirada',
                    String(p.itens),
                    moeda(p.valorTotal)
                ];
                celulas.forEach((texto, i) => {
                    const td = document.createElement('td');
                    td.textContent = texto; // textContent evita injeção de HTML com dados do banco
                    if (i === 0) td.className = 'ps-3';
                    if (i === 3) td.className = 'text-center';
                    if (i === 4) td.className = 'text-end';
                    tr.appendChild(td);
                });
                const tdStatus = document.createElement('td');
                tdStatus.className = 'pe-3';
                const badge = document.createElement('span');
                badge.className = 'badge ' + (CLASSES_STATUS[p.status] || 'bg-secondary');
                badge.textContent = p.statusDescricao;
                tdStatus.appendChild(badge);
                tr.appendChild(tdStatus);
                corpo.appendChild(tr);
            });
        } catch (e) {
            corpo.innerHTML = '';
            const tr = document.createElement('tr');
            const td = document.createElement('td');
            td.colSpan = 6;
            td.className = 'estado erro';
            td.textContent = e.message;
            tr.appendChild(td);
            corpo.appendChild(tr);
            throw e;
        }
    }

    // ---------- RF04 — Produtos ----------

    async function carregarProdutos() {
        await bloco('cardProdutos', async () => {
            const ordem = document.querySelector('input[name="ordem"]:checked').value;
            const r = await buscar('produtos-mais-vendidos', { limite: '8', ordenarPor: ordem });
            if (!r.produtos.length) {
                mensagemNoCard('cardProdutos', 'Nenhum produto vendido no período selecionado.');
                return;
            }
            const porValor = ordem === 'FATURAMENTO';
            desenhar('gProdutos', {
                type: 'bar',
                data: {
                    labels: r.produtos.map(p => `${p.posicao}º ${p.nome}`),
                    datasets: [{ label: porValor ? 'Faturamento (R$)' : 'Quantidade',
                        data: r.produtos.map(p => porValor ? p.faturamento : p.quantidade),
                        backgroundColor: porValor ? 'rgba(25,135,84,.65)' : 'rgba(111,66,193,.65)' }]
                },
                options: {
                    indexAxis: 'y', maintainAspectRatio: false,
                    plugins: { legend: { display: false }, tooltip: { callbacks: {
                        label: ctx => {
                            const p = r.produtos[ctx.dataIndex];
                            return ` ${numero(p.quantidade)} un · ${moeda(p.faturamento)} · ${porcento(p.participacao)} do total`;
                        } } } },
                    scales: { x: { beginAtZero: true, ticks: { precision: 0 } } }
                }
            });
        });
    }

    // ---------- RF07 — Atualização ----------

    async function carregarTudo() {
        if (!validarFiltros()) return;
        $('btnAtualizar').disabled = true;
        const resultados = await Promise.allSettled([
            carregarResumo(), carregarVendas(), carregarSituacao(), carregarHoras(), carregarProdutos(), carregarRecentes()
        ]);
        $('btnAtualizar').disabled = false;
        const falhas = resultados.filter(r => r.status === 'rejected');
        const erro400 = falhas.find(f => f.reason && f.reason.status === 400);
        if (erro400) {
            $('erroFiltro').textContent = erro400.reason.message;
            $('erroFiltro').classList.remove('d-none');
        }
        const hora = new Date().toLocaleTimeString('pt-BR');
        $('ultimaAtualizacao').textContent = falhas.length
            ? `Atualizado às ${hora} (${falhas.length} bloco(s) com erro)`
            : `Atualizado às ${hora}`;
    }

    function configurarAutoAtualizacao() {
        clearInterval(timer);
        if ($('autoAtualizar').checked) {
            timer = setInterval(() => { if (!document.hidden) carregarTudo(); }, INTERVALO_ATUALIZACAO_MS);
        }
        try { localStorage.setItem(CHAVE_AUTO, $('autoAtualizar').checked ? '1' : '0'); } catch (e) { /* sem storage */ }
    }

    // ---------- Inicialização ----------

    async function iniciar() {
        try { if (localStorage.getItem(CHAVE_AUTO) === '0') $('autoAtualizar').checked = false; } catch (e) { /* sem storage */ }
        if (typeof Chart === 'undefined') {
            $('ultimaAtualizacao').textContent = 'Biblioteca de gráficos indisponível (sem internet?).';
        } else {
            Chart.defaults.font.family = getComputedStyle(document.body).fontFamily;
        }
        try {
            await carregarOpcoes();
        } catch (e) {
            const hoje = new Date();
            const ini = new Date(hoje); ini.setDate(hoje.getDate() - 29);
            $('fInicio').value = isoLocal(ini);
            $('fFim').value = isoLocal(hoje);
        }

        $('formFiltros').addEventListener('submit', ev => { ev.preventDefault(); carregarTudo(); });
        $('btnAtualizar').addEventListener('click', carregarTudo);
        $('btnLimpar').addEventListener('click', () => { $('fTipo').value = ''; $('fStatus').value = ''; aplicarAtalho('30'); });
        document.querySelectorAll('[data-atalho]').forEach(b => b.addEventListener('click', () => aplicarAtalho(b.dataset.atalho)));
        $('fAgrupamento').addEventListener('change', () => carregarVendas().catch(() => {}));
        document.querySelectorAll('input[name="ordem"]').forEach(r => r.addEventListener('change', () => carregarProdutos().catch(() => {})));
        $('autoAtualizar').addEventListener('change', configurarAutoAtualizacao);

        configurarAutoAtualizacao();
        carregarTudo();
    }

    iniciar();
})();

'use strict';

(() => {
  const $ = (id) => document.getElementById(id);
  const state = {
    file: null,
    previewUrl: null,
    busy: false,
    documents: [],
    config: { extractionMode: 'LOCAL', maxFileSizeMb: 10, maxPages: 5, maxAgeDays: 90 },
    historyRequest: 0,
    detailRequest: 0
  };
  const views = ['documentos', 'envios', 'resultados', 'detalhe', 'ajuda'];

  function icon(name) {
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    const use = document.createElementNS('http://www.w3.org/2000/svg', 'use');
    use.setAttribute('href', '#i-' + name);
    svg.setAttribute('aria-hidden', 'true');
    svg.append(use);
    return svg;
  }

  function element(tag, className, text) {
    const el = document.createElement(tag);
    if (className) el.className = className;
    if (text !== undefined && text !== null) el.textContent = String(text);
    return el;
  }

  function showError(message) {
    $('alert-text').textContent = message;
    $('global-alert').hidden = false;
    $('global-alert').scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  }

  function hideError() {
    $('global-alert').hidden = true;
    $('alert-text').textContent = '';
  }

  async function request(url, options = {}) {
    let response;
    try {
      response = await fetch(url, { ...options, headers: { Accept: 'application/json', ...options.headers } });
    } catch {
      throw new Error('Não foi possível conectar ao servidor. Confira se o Spring Boot está rodando e tente novamente.');
    }
    let body;
    try {
      body = await response.json();
    } catch {
      throw new Error(response.status === 413
        ? 'O arquivo excede o limite de tamanho permitido.'
        : 'O servidor não retornou uma resposta válida. Tente novamente em instantes.');
    }
    if (!response.ok) {
      throw new Error(body.mensagem || body.message || 'Não foi possível concluir a operação. Tente novamente.');
    }
    return body;
  }

  function closeMenu() {
    $('sidebar').classList.remove('open');
    $('sidebar-scrim').hidden = true;
    $('menu-button').setAttribute('aria-expanded', 'false');
    $('menu-button').setAttribute('aria-label', 'Abrir menu');
  }

  function showView(name) {
    const selected = views.includes(name) ? name : 'documentos';
    views.forEach((view) => { $('view-' + view).hidden = view !== selected; });
    document.querySelectorAll('[data-view]').forEach((link) => {
      const active = link.dataset.view === selected || (selected === 'detalhe' && link.dataset.view === 'envios');
      link.classList.toggle('active', active);
      if (active) link.setAttribute('aria-current', 'page');
      else link.removeAttribute('aria-current');
    });
    closeMenu();
    document.title = ({ documentos: 'Enviar comprovante', envios: 'Meus envios', resultados: 'Resultados', detalhe: 'Resultado da validação', ajuda: 'Como funciona' }[selected]) + ' · ValidaDoc';
  }

  async function route() {
    const hash = window.location.hash.slice(1) || 'documentos';
    const match = hash.match(/^documento\/([A-Za-z0-9-]+)$/);
    if (match) {
      const currentRequest = ++state.detailRequest;
      const existing = state.documents.find((item) => String(item.id) === match[1]);
      if (existing && existing.dados && Array.isArray(existing.regras)) {
        renderDetail(existing);
        return;
      }
      showView('envios');
      try {
        const result = await request('/api/documentos/' + encodeURIComponent(match[1]));
        if (currentRequest !== state.detailRequest || window.location.hash.slice(1) !== hash) return;
        renderDetail(result);
      } catch (error) {
        if (currentRequest !== state.detailRequest) return;
        showError(error.message);
        loadHistory();
      }
      return;
    }
    state.detailRequest++;
    showView(hash);
    if (hash === 'envios' || hash === 'resultados') await loadHistory();
  }

  function goUpload() {
    hideError();
    window.location.hash = 'documentos';
    showView('documentos');
    window.scrollTo({ top: 0, behavior: 'smooth' });
    if (!state.busy) $('expected-name').focus({ preventScroll: true });
  }

  function formatSize(bytes) {
    return bytes >= 1024 * 1024
      ? (bytes / (1024 * 1024)).toLocaleString('pt-BR', { maximumFractionDigits: 2 }) + ' MB'
      : Math.max(1, Math.round(bytes / 1024)).toLocaleString('pt-BR') + ' KB';
  }

  function dateTime(value) {
    if (!value) return 'Data não informada';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(date);
  }

  function calendarDate(value) {
    if (!value) return null;
    const match = String(value).match(/^(\d{4})-(\d{2})-(\d{2})$/);
    return match ? match[3] + '/' + match[2] + '/' + match[1] : String(value);
  }

  function updateSubmit() {
    $('process-button').disabled = state.busy || !state.file || !$('expected-name').value.trim();
  }

  function revokePreview() {
    if (state.previewUrl) URL.revokeObjectURL(state.previewUrl);
    state.previewUrl = null;
    $('preview-image').removeAttribute('src');
    $('file-preview').hidden = true;
    $('file-preview').open = false;
  }

  function clearSelection() {
    state.file = null;
    $('file-input').value = '';
    $('selected-file').hidden = true;
    $('file-name').textContent = '';
    $('file-size').textContent = '';
    revokePreview();
    updateSubmit();
  }

  function selectFile(file) {
    if (state.busy) return;
    hideError();
    clearSelection();
    if (!file) return;
    if (!/\.(pdf|png|jpe?g)$/i.test(file.name)) {
      showError('Formato não permitido. Selecione um arquivo PDF, PNG ou JPG.');
      return;
    }
    const acceptedTypes = ['application/pdf', 'image/png', 'image/jpeg'];
    if (file.type && !acceptedTypes.includes(file.type)) {
      showError('O tipo do arquivo não corresponde a um PDF, PNG ou JPG.');
      return;
    }
    if (file.size === 0) {
      showError('O arquivo está vazio. Selecione outro documento.');
      return;
    }
    if (file.size > state.config.maxFileSizeMb * 1024 * 1024) {
      showError('O arquivo deve ter no máximo ' + state.config.maxFileSizeMb + ' MB.');
      return;
    }
    state.file = file;
    $('file-name').textContent = file.name;
    $('file-size').textContent = formatSize(file.size) + ' · ' + (file.name.split('.').pop() || '').toUpperCase();
    $('selected-file').hidden = false;
    if (/\.(png|jpe?g)$/i.test(file.name)) {
      state.previewUrl = URL.createObjectURL(file);
      $('preview-image').src = state.previewUrl;
      $('file-preview').hidden = false;
    }
    updateSubmit();
  }

  function setBusy(busy) {
    state.busy = busy;
    $('processing-notice').hidden = !busy;
    $('upload-form').setAttribute('aria-busy', String(busy));
    $('dropzone').setAttribute('aria-disabled', String(busy));
    $('dropzone').tabIndex = busy ? -1 : 0;
    ['expected-name', 'document-type', 'file-input', 'remove-file', 'cancel-button'].forEach((id) => { $(id).disabled = busy; });
    $('process-button').replaceChildren(document.createTextNode(busy ? 'Processando…' : 'Processar documento'));
    // Uma só requisição executa o fluxo: a interface não presume a etapa interna do servidor.
    if (!busy) $('process-button').append(icon('arrow'));
    for (let index = 1; index <= 4; index++) {
      const step = $('step-' + index);
      step.classList.toggle('current', busy ? index === 2 : index === 1);
      step.classList.toggle('done', busy && index === 1);
      step.classList.toggle('processing', busy && index === 2);
      if ((busy && index === 2) || (!busy && index === 1)) step.setAttribute('aria-current', 'step');
      else step.removeAttribute('aria-current');
    }
    updateSubmit();
  }

  async function submitDocument(event) {
    event.preventDefault();
    if (state.busy || !state.file) return;
    const expectedName = $('expected-name').value.trim();
    if (!expectedName) {
      showError('Informe o nome completo do titular para conferir o documento.');
      $('expected-name').focus();
      return;
    }
    hideError();
    const form = new FormData();
    form.append('arquivo', state.file);
    form.append('nomeEsperado', expectedName);
    setBusy(true);
    try {
      const result = await request('/api/documentos', { method: 'POST', body: form });
      if (!result || !result.id || !result.status) throw new Error('A resposta do servidor está incompleta. Confira o histórico antes de reenviar.');
      state.documents = [result, ...state.documents.filter((item) => item.id !== result.id)].slice(0, 100);
      renderHistory();
      clearSelection();
      window.location.hash = 'documento/' + encodeURIComponent(result.id);
      renderDetail(result);
      window.scrollTo({ top: 0, behavior: 'smooth' });
      $('main-content').focus({ preventScroll: true });
    } catch (error) {
      showError(error.message);
    } finally {
      setBusy(false);
    }
  }

  function emptyState(title, description, actionText, action) {
    const wrapper = element('div', 'empty-state');
    wrapper.append(icon('inbox'), element('h3', '', title), element('p', '', description));
    if (actionText && action) {
      const button = element('button', 'button button-primary', actionText);
      button.type = 'button';
      button.append(icon('arrow'));
      button.addEventListener('click', action);
      wrapper.append(button);
    }
    return wrapper;
  }

  function statusChip(status) {
    const approved = status === 'APROVADO';
    const chip = element('span', 'status-chip ' + (approved ? 'approved' : 'rejected'));
    chip.append(icon(approved ? 'check' : 'x'), document.createTextNode(approved ? 'Aprovado' : 'Reprovado'));
    return chip;
  }

  function historyTable(documents) {
    const wrapper = element('div', 'table-wrap');
    const table = element('table', 'history-table');
    const caption = element('caption', 'visually-hidden', 'Documentos enviados, data e resultado da validação');
    const head = document.createElement('thead');
    const headerRow = document.createElement('tr');
    ['DOCUMENTO', 'ENVIADO EM', 'RESULTADO', 'DETALHES'].forEach((title) => {
      const th = element('th', '', title);
      th.scope = 'col';
      headerRow.append(th);
    });
    head.append(headerRow);
    const body = document.createElement('tbody');
    documents.forEach((item) => {
      const row = document.createElement('tr');
      const fileCell = document.createElement('td');
      const fileBox = element('div', 'table-document');
      const fileText = document.createElement('div');
      fileText.append(element('strong', '', item.nomeArquivo || 'Documento'), element('span', '', item.dados?.nome || 'Titular não identificado'));
      fileBox.append(icon('document'), fileText);
      fileCell.append(fileBox);
      const dateCell = element('td', 'table-date', dateTime(item.criadoEm));
      const statusCell = document.createElement('td');
      statusCell.append(statusChip(item.status));
      const actionCell = document.createElement('td');
      const link = element('a', 'button table-detail', 'Ver análise');
      link.href = '#documento/' + encodeURIComponent(item.id);
      link.setAttribute('aria-label', 'Ver análise de ' + (item.nomeArquivo || 'documento'));
      link.append(icon('chevron'));
      actionCell.append(link);
      row.append(fileCell, dateCell, statusCell, actionCell);
      body.append(row);
    });
    table.append(caption, head, body);
    wrapper.append(table);
    return wrapper;
  }

  function renderHistory() {
    const documents = state.documents;
    $('history-count').textContent = String(documents.length);
    $('history-total').textContent = String(documents.length);
    $('stat-total').textContent = String(documents.length);
    $('stat-approved').textContent = String(documents.filter((item) => item.status === 'APROVADO').length);
    $('stat-rejected').textContent = String(documents.filter((item) => item.status === 'REPROVADO').length);
    ['history-content', 'results-content'].forEach((id) => {
      $(id).replaceChildren(documents.length
        ? historyTable(documents)
        : emptyState('Seu primeiro documento começa aqui', 'Você ainda não tem envios. Envie um comprovante para acompanhar a análise e o resultado.', 'Enviar documento', goUpload));
    });
  }

  async function loadHistory() {
    const requestNumber = ++state.historyRequest;
    $('refresh-history').disabled = true;
    if (!state.documents.length) {
      ['history-content', 'results-content'].forEach((id) => {
        const loading = element('div', 'empty-state');
        loading.setAttribute('role', 'status');
        loading.append(element('span', 'spinner'), element('p', '', 'Carregando seus documentos…'));
        $(id).replaceChildren(loading);
      });
    }
    try {
      const documents = await request('/api/documentos');
      if (requestNumber !== state.historyRequest) return;
      if (!Array.isArray(documents)) throw new Error('Não foi possível interpretar o histórico retornado pelo servidor.');
      state.documents = documents;
      renderHistory();
    } catch (error) {
      if (requestNumber !== state.historyRequest) return;
      if (state.documents.length) {
        showError(error.message);
      } else {
        ['history-content', 'results-content'].forEach((id) => {
          $(id).replaceChildren(emptyState('Não conseguimos carregar o histórico', error.message, 'Tentar novamente', loadHistory));
        });
      }
    } finally {
      if (requestNumber === state.historyRequest) $('refresh-history').disabled = false;
    }
  }

  function renderDetail(result) {
    const approved = result.status === 'APROVADO';
    const data = result.dados || {};
    const rules = Array.isArray(result.regras) ? result.regras : [];
    $('result-file-caption').textContent = result.nomeArquivo || 'Documento enviado';
    $('result-banner').classList.toggle('rejected', !approved);
    $('result-status-icon').setAttribute('href', approved ? '#i-check' : '#i-x');
    $('result-status-title').textContent = approved ? 'Documento aprovado' : 'Documento reprovado';
    $('result-status-description').textContent = approved
      ? 'Os dados encontrados atendem às regras de validação do projeto.'
      : 'Algumas regras não foram atendidas. Confira os detalhes abaixo.';
    $('result-date').textContent = dateTime(result.criadoEm);
    $('extraction-method').textContent = result.metodoExtracao || 'EXTRAÇÃO';
    $('extraction-method').title = result.metodoExtracao || '';
    $('rules-count').textContent = rules.filter((rule) => rule.aprovada).length + ' / ' + rules.length + ' ATENDIDAS';
    const fields = [['Nome', data.nome], ['CPF', data.cpf], ['Endereço', data.endereco], ['Cidade', data.cidade], ['Estado', data.estado], ['Emissão', calendarDate(data.dataEmissao)]];
    const fieldNodes = fields.map(([label, value]) => {
      const row = document.createElement('div');
      const present = value !== null && value !== undefined && String(value).trim() !== '';
      row.append(element('dt', '', label), element('dd', present ? '' : 'missing', present ? value : 'Não identificado'));
      return row;
    });
    $('extracted-data').replaceChildren(...fieldNodes);
    $('validation-rules').replaceChildren(...rules.map((rule) => {
      const row = document.createElement('li');
      const mark = element('span', 'rule-icon' + (rule.aprovada ? '' : ' failed'));
      mark.setAttribute('aria-label', rule.aprovada ? 'Atendida' : 'Não atendida');
      mark.append(icon(rule.aprovada ? 'check' : 'x'));
      const text = document.createElement('div');
      text.append(element('strong', '', rule.descricao || rule.codigo || 'Regra de validação'), element('p', '', rule.detalhe || (rule.aprovada ? 'Regra atendida.' : 'Regra não atendida.')));
      row.append(mark, text);
      return row;
    }));
    $('raw-text').textContent = result.textoExtraido || 'Nenhum texto foi extraído deste documento.';
    const warnings = Array.isArray(result.avisos) ? result.avisos : [];
    $('result-warnings').replaceChildren(...warnings.map((warning) => element('p', '', warning)));
    $('result-warnings').hidden = !warnings.length;
    showView('detalhe');
  }

  async function loadConfig() {
    try {
      const config = await request('/api/config');
      state.config = { ...state.config, ...config };
      [['[data-max-size]', state.config.maxFileSizeMb], ['[data-max-pages]', state.config.maxPages], ['[data-max-age]', state.config.maxAgeDays]].forEach(([selector, value]) => {
        document.querySelectorAll(selector).forEach((node) => { node.textContent = String(value); });
      });
      $('dropzone').setAttribute('aria-label', 'Selecionar arquivo PDF, PNG ou JPG de até ' + state.config.maxFileSizeMb + ' megabytes');
      const useAi = state.config.extractionMode === 'OPENAI';
      $('mode-title').textContent = useAi ? 'Extração com inteligência artificial' : 'Extração local, sem chave de IA';
      $('mode-description').textContent = useAi
        ? 'A integração OpenAI está configurada. O texto extraído é enviado para a IA organizar os campos; as regras são aplicadas no Java.'
        : 'O extrator local procura campos e padrões no texto. Ele funciona melhor com documentos de layout previsível, como o exemplo do projeto. A integração com IA é opcional.';
      $('mode-chip').textContent = useAi ? 'OPENAI' : 'LOCAL';
      $('help-extraction-mode').textContent = useAi ? 'OpenAI · IA' : 'Extrator local · Regex';
      updateSubmit();
    } catch (error) {
      $('mode-title').textContent = 'Configuração indisponível';
      $('mode-description').textContent = error.message;
      $('mode-chip').textContent = 'OFFLINE';
      showError(error.message);
    }
  }

  $('upload-form').addEventListener('submit', submitDocument);
  $('expected-name').addEventListener('input', updateSubmit);
  $('file-input').addEventListener('change', (event) => selectFile(event.target.files[0]));
  $('remove-file').addEventListener('click', () => { clearSelection(); $('dropzone').focus(); });
  $('cancel-button').addEventListener('click', () => {
    if (state.busy) return;
    clearSelection();
    $('expected-name').value = '';
    hideError();
    $('expected-name').focus();
  });
  $('dismiss-alert').addEventListener('click', hideError);
  $('dropzone').addEventListener('click', () => { if (!state.busy) $('file-input').click(); });
  $('dropzone').addEventListener('keydown', (event) => {
    if (!state.busy && (event.key === 'Enter' || event.key === ' ')) {
      event.preventDefault();
      $('file-input').click();
    }
  });
  let dragDepth = 0;
  $('dropzone').addEventListener('dragenter', (event) => { event.preventDefault(); if (!state.busy) { dragDepth++; $('dropzone').classList.add('dragover'); } });
  $('dropzone').addEventListener('dragover', (event) => { event.preventDefault(); event.dataTransfer.dropEffect = state.busy ? 'none' : 'copy'; });
  $('dropzone').addEventListener('dragleave', (event) => { event.preventDefault(); dragDepth--; if (dragDepth <= 0) { dragDepth = 0; $('dropzone').classList.remove('dragover'); } });
  $('dropzone').addEventListener('drop', (event) => {
    event.preventDefault();
    dragDepth = 0;
    $('dropzone').classList.remove('dragover');
    if (state.busy) return;
    if (event.dataTransfer.files.length !== 1) {
      showError('Envie um documento por vez. Selecione apenas um arquivo.');
      return;
    }
    selectFile(event.dataTransfer.files[0]);
  });
  // Impede o navegador de trocar a página pelo arquivo solto fora da área de envio.
  window.addEventListener('dragover', (event) => { if (event.dataTransfer.types.includes('Files')) event.preventDefault(); });
  window.addEventListener('drop', (event) => { if (event.dataTransfer.types.includes('Files')) event.preventDefault(); });
  $('preview-image').addEventListener('error', () => { $('file-preview').hidden = true; });
  $('refresh-history').addEventListener('click', () => { hideError(); loadHistory(); });
  $('back-to-history').addEventListener('click', () => { window.location.hash = 'envios'; });
  document.querySelectorAll('[data-go-upload]').forEach((button) => button.addEventListener('click', goUpload));
  $('menu-button').addEventListener('click', () => {
    const open = !$('sidebar').classList.contains('open');
    $('sidebar').classList.toggle('open', open);
    $('sidebar-scrim').hidden = !open;
    $('menu-button').setAttribute('aria-expanded', String(open));
    $('menu-button').setAttribute('aria-label', open ? 'Fechar menu' : 'Abrir menu');
  });
  $('sidebar-scrim').addEventListener('click', closeMenu);
  document.addEventListener('keydown', (event) => { if (event.key === 'Escape') closeMenu(); });
  window.addEventListener('hashchange', route);
  window.addEventListener('beforeunload', (event) => {
    if (!state.busy) return;
    event.preventDefault();
    event.returnValue = '';
  });
  $('step-1').setAttribute('aria-current', 'step');
  renderHistory();
  route();
  loadConfig();
  if (!['#envios', '#resultados'].includes(window.location.hash)) loadHistory();
})();

const $ = (q) => document.querySelector(q);
const $$ = (q) => Array.from(document.querySelectorAll(q));

let pc = null;
let dc = null;
let micStream = null;
let micMuted = false;
let config = null;
let toolMeta = new Map();
const handledCalls = new Set();
let liveAssistantLine = null;

function token() { return localStorage.getItem('kutalp_access_token') || ''; }
function authHeaders(extra = {}) {
  const h = { ...extra };
  if (token()) h['X-Kutalp-Token'] = token();
  return h;
}
function toast(msg) {
  const el = $('#toast');
  el.textContent = msg;
  el.classList.add('show');
  clearTimeout(el._timer);
  el._timer = setTimeout(() => el.classList.remove('show'), 2400);
}
function escapeHtml(s='') {
  return String(s).replace(/[&<>'"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
}
async function api(path, options={}) {
  const headers = authHeaders(options.headers || {});
  if (options.body && !(options.body instanceof FormData) && !headers['Content-Type']) headers['Content-Type'] = 'application/json';
  const r = await fetch(path, { ...options, headers });
  if (!r.ok) {
    let detail = await r.text();
    try { detail = JSON.parse(detail).detail || detail; } catch (_) {}
    throw new Error(`${r.status}: ${detail}`);
  }
  const ct = r.headers.get('content-type') || '';
  return ct.includes('application/json') ? r.json() : r.text();
}

function setStatus(online, text) {
  const p = $('#statusPill');
  p.classList.toggle('online', online);
  p.classList.toggle('offline', !online);
  p.textContent = text || (online ? 'ONLINE' : 'OFFLINE');
}
function addLiveLine(text, cls='system') {
  const box = $('#liveTranscript');
  if (box.querySelector('.muted')) box.innerHTML = '';
  const div = document.createElement('div');
  div.className = `line ${cls}`;
  div.textContent = text;
  box.appendChild(div);
  box.scrollTop = box.scrollHeight;
  return div;
}
function appendAssistantDelta(delta) {
  if (!liveAssistantLine || !document.body.contains(liveAssistantLine)) {
    liveAssistantLine = addLiveLine('', 'assistant');
  }
  liveAssistantLine.textContent += delta;
  $('#liveTranscript').scrollTop = $('#liveTranscript').scrollHeight;
}
function finishAssistantLine(finalText) {
  if (finalText && (!liveAssistantLine || !liveAssistantLine.textContent.trim())) {
    liveAssistantLine = addLiveLine(finalText, 'assistant');
  }
  liveAssistantLine = null;
}

async function loadConfig() {
  try {
    config = await api('/api/config');
    toolMeta = new Map(config.tools.map(t => [t.name, t]));
    $('#realtimeModel').textContent = config.realtime_model;
    $('#voiceName').textContent = config.voice;
    $('#toolCount').textContent = String(config.tools.length);
    $('#accessToken').value = token();
    $('#systemInfo').innerHTML = [
      ['Sürüm', config.version],
      ['Text model', config.text_model],
      ['Realtime model', config.realtime_model],
      ['API', config.api_configured ? 'aktif' : 'anahtar yok'],
      ['Yerel erişim kilidi', config.access_token_required ? 'aktif' : 'kapalı'],
    ].map(([k,v]) => `<div class="system-row"><span>${escapeHtml(k)}</span><strong>${escapeHtml(v)}</strong></div>`).join('');
    setStatus(config.api_configured, config.api_configured ? 'HAZIR' : 'OFFLINE');
    $('#resolvedPredictions').textContent = config.prediction_metrics.resolved_predictions ?? 0;
    $('#avgBrier').textContent = config.prediction_metrics.average_brier_score == null ? '—' : Number(config.prediction_metrics.average_brier_score).toFixed(3);
  } catch (e) {
    setStatus(false, 'KİLİTLİ');
    $('#systemInfo').innerHTML = `<div class="muted">${escapeHtml(e.message)}</div>`;
  }
}

async function connectRealtime() {
  if (pc) return disconnectRealtime();
  try {
    setStatus(false, 'BAĞLANIYOR');
    pc = new RTCPeerConnection();
    dc = pc.createDataChannel('oai-events');
    dc.onopen = () => {
      setStatus(true, 'CANLI');
      $('#connectBtn').textContent = 'Oturumu kapat';
      $('#muteBtn').disabled = false;
      $('#realtimeText').disabled = false;
      $('#realtimeSend').disabled = false;
      $('#orb').classList.add('live');
      addLiveLine('Canlı KUTALP oturumu açıldı.', 'system');
    };
    dc.onmessage = (ev) => {
      try { handleRealtimeEvent(JSON.parse(ev.data)); }
      catch (e) { console.error('Realtime event parse error', e, ev.data); }
    };
    dc.onclose = () => setStatus(false, 'KAPALI');

    const remoteAudio = $('#remoteAudio');
    pc.ontrack = (e) => { remoteAudio.srcObject = e.streams[0]; };

    micStream = await navigator.mediaDevices.getUserMedia({
      audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true }
    });
    pc.addTrack(micStream.getAudioTracks()[0], micStream);

    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);
    const sdpResponse = await fetch('/session', {
      method: 'POST',
      headers: authHeaders({'Content-Type': 'application/sdp'}),
      body: offer.sdp,
    });
    if (!sdpResponse.ok) throw new Error(`${sdpResponse.status}: ${await sdpResponse.text()}`);
    const answer = { type: 'answer', sdp: await sdpResponse.text() };
    await pc.setRemoteDescription(answer);
  } catch (e) {
    toast(`Ses bağlantısı kurulamadı: ${e.message}`);
    disconnectRealtime();
  }
}

function disconnectRealtime() {
  if (micStream) micStream.getTracks().forEach(t => t.stop());
  if (dc) try { dc.close(); } catch (_) {}
  if (pc) try { pc.close(); } catch (_) {}
  micStream = null; dc = null; pc = null; micMuted = false; liveAssistantLine = null;
  $('#connectBtn').textContent = 'Canlı oturumu başlat';
  $('#muteBtn').textContent = 'Mikrofonu kapat';
  $('#muteBtn').disabled = true;
  $('#realtimeText').disabled = true;
  $('#realtimeSend').disabled = true;
  $('#orb').classList.remove('live', 'muted');
  setStatus(config?.api_configured, config?.api_configured ? 'HAZIR' : 'OFFLINE');
}

function toggleMute() {
  if (!micStream) return;
  micMuted = !micMuted;
  micStream.getAudioTracks().forEach(t => t.enabled = !micMuted);
  $('#muteBtn').textContent = micMuted ? 'Mikrofonu aç' : 'Mikrofonu kapat';
  $('#orb').classList.toggle('muted', micMuted);
}

function functionCallFromEvent(event) {
  if (event.type === 'response.output_item.done' && event.item?.type === 'function_call') return event.item;
  if (event.type === 'conversation.item.done' && event.item?.type === 'function_call') return event.item;
  if (event.type === 'response.function_call_arguments.done') {
    return { name: event.name, call_id: event.call_id, arguments: event.arguments || '{}' };
  }
  return null;
}

function handleRealtimeEvent(event) {
  if (event.type === 'response.output_audio_transcript.delta' || event.type === 'response.output_text.delta') {
    appendAssistantDelta(event.delta || '');
  }
  if (event.type === 'response.output_audio_transcript.done' || event.type === 'response.output_text.done') {
    finishAssistantLine(event.transcript || event.text || '');
  }
  if (event.type === 'conversation.item.input_audio_transcription.completed') {
    addLiveLine(`Sen: ${event.transcript || ''}`, 'system');
  }
  if (event.type === 'error') {
    addLiveLine(`Realtime hata: ${event.error?.message || JSON.stringify(event.error || event)}`, 'system');
  }
  const call = functionCallFromEvent(event);
  if (call?.call_id && call?.name && !handledCalls.has(call.call_id)) {
    handledCalls.add(call.call_id);
    prepareToolCall(call);
  }
}

async function prepareToolCall(call) {
  let args = {};
  try { args = typeof call.arguments === 'string' ? JSON.parse(call.arguments || '{}') : (call.arguments || {}); }
  catch (e) {
    return sendToolOutput(call.call_id, {ok:false, error:`Geçersiz araç argümanı: ${e.message}`});
  }
  const meta = toolMeta.get(call.name);
  if (!meta) return sendToolOutput(call.call_id, {ok:false, error:`Bilinmeyen araç: ${call.name}`});
  addLiveLine(`Araç isteği: ${call.name}`, 'tool');
  if (meta.requires_approval) {
    showApproval({ ...call, args, meta });
  } else {
    const result = await executeTool(call.name, args, true);
    sendToolOutput(call.call_id, result);
  }
}

function showApproval(call) {
  const wrap = document.createElement('div');
  wrap.className = 'approval-card';
  wrap.innerHTML = `
    <strong>Onay gerekiyor: ${escapeHtml(call.name)}</strong>
    <div class="muted">${escapeHtml(call.meta.description)}</div>
    <pre>${escapeHtml(JSON.stringify(call.args, null, 2))}</pre>
    <div class="actions">
      <button class="primary approve">Onayla</button>
      <button class="danger reject">Reddet</button>
    </div>`;
  $('#approvalArea').prepend(wrap);
  wrap.querySelector('.approve').onclick = async () => {
    wrap.remove();
    const result = await executeTool(call.name, call.args, true);
    sendToolOutput(call.call_id, result);
  };
  wrap.querySelector('.reject').onclick = async () => {
    wrap.remove();
    try { await executeTool(call.name, call.args, false); } catch (_) {}
    sendToolOutput(call.call_id, {ok:false, denied_by_user:true, message:'Kullanıcı işlemi onaylamadı.'});
  };
}

async function executeTool(name, args, approved) {
  try {
    return await api('/api/tools/execute', {
      method: 'POST',
      body: JSON.stringify({name, arguments: args, approved}),
    });
  } catch (e) {
    return {ok:false, error:e.message};
  }
}

function sendToolOutput(callId, result) {
  if (!dc || dc.readyState !== 'open') return;
  dc.send(JSON.stringify({
    type: 'conversation.item.create',
    item: { type: 'function_call_output', call_id: callId, output: JSON.stringify(result) }
  }));
  dc.send(JSON.stringify({ type: 'response.create' }));
  addLiveLine(`Araç sonucu: ${result.ok ? 'başarılı' : 'başarısız'}`, 'tool');
  refreshAll();
}

function sendRealtimeText(text) {
  if (!dc || dc.readyState !== 'open' || !text.trim()) return;
  dc.send(JSON.stringify({
    type: 'conversation.item.create',
    item: { type: 'message', role: 'user', content: [{type:'input_text', text: text.trim()}] }
  }));
  dc.send(JSON.stringify({type:'response.create'}));
  addLiveLine(`Sen: ${text.trim()}`, 'system');
}

async function sendTextChat(message) {
  const log = $('#chatLog');
  const user = document.createElement('div');
  user.className = 'message user'; user.textContent = message; log.appendChild(user);
  const wait = document.createElement('div');
  wait.className = 'message assistant'; wait.textContent = 'Analiz ediliyor…'; log.appendChild(wait);
  log.scrollTop = log.scrollHeight;
  try {
    const res = await api('/api/chat', {
      method:'POST',
      body: JSON.stringify({
        message,
        scientific: $('#scientificToggle').checked,
        use_red_team: $('#redTeamToggle').checked,
      }),
    });
    wait.textContent = res.reply || '';
    if (res.red_team) {
      const red = document.createElement('div');
      red.className = 'message red'; red.textContent = `RED TEAM\n${res.red_team}`; log.appendChild(red);
    }
  } catch (e) { wait.textContent = `Hata: ${e.message}`; }
  log.scrollTop = log.scrollHeight;
}

async function loadMemories() {
  const list = $('#memoryList');
  try {
    const items = await api('/api/memories');
    list.innerHTML = items.length ? items.map(m => `
      <div class="list-item">
        <div><strong>${escapeHtml(m.kind)}</strong> · ${escapeHtml(m.text)}<div class="meta">#${m.id} · ${escapeHtml(m.created_at)}</div></div>
        <button class="danger memdel" data-id="${m.id}">Sil</button>
      </div>`).join('') : '<div class="muted">Henüz hafıza yok.</div>';
    $$('.memdel').forEach(b => b.onclick = async () => {
      if (!confirm('Bu hafıza silinsin mi?')) return;
      await api(`/api/memories/${b.dataset.id}`, {method:'DELETE'}); loadMemories();
    });
  } catch (e) { list.innerHTML = `<div class="muted">${escapeHtml(e.message)}</div>`; }
}

async function loadTasks() {
  const list = $('#taskList');
  try {
    const items = await api('/api/tasks?status=open');
    list.innerHTML = items.length ? items.map(t => `
      <div class="list-item"><div><strong>#${t.id} ${escapeHtml(t.title)}</strong><div>${escapeHtml(t.notes || '')}</div><div class="meta">${escapeHtml(t.due_at || 'tarih yok')}</div></div></div>`).join('') : '<div class="muted">Açık görev yok.</div>';
  } catch (e) { list.innerHTML = `<div class="muted">${escapeHtml(e.message)}</div>`; }
}

async function loadPredictions() {
  const list = $('#predictionList');
  try {
    const res = await api('/api/predictions?status=open');
    $('#resolvedPredictions').textContent = res.metrics.resolved_predictions ?? 0;
    $('#avgBrier').textContent = res.metrics.average_brier_score == null ? '—' : Number(res.metrics.average_brier_score).toFixed(3);
    list.innerHTML = res.items.length ? res.items.map(p => `
      <div class="list-item"><div><strong>#${p.id} · ${(p.probability*100).toFixed(0)}%</strong><div>${escapeHtml(p.statement)}</div><div class="meta">${escapeHtml(p.due_at || 'son tarih yok')}</div></div></div>`).join('') : '<div class="muted">Açık tahmin yok.</div>';
  } catch (e) { list.innerHTML = `<div class="muted">${escapeHtml(e.message)}</div>`; }
}

async function refreshAll() { await Promise.allSettled([loadMemories(), loadTasks(), loadPredictions(), loadConfig()]); }

$$('.tab').forEach(btn => btn.onclick = () => {
  $$('.tab').forEach(b => b.classList.toggle('active', b === btn));
  $$('.tabpage').forEach(p => p.classList.toggle('active', p.id === btn.dataset.tab));
});
$('#connectBtn').onclick = connectRealtime;
$('#muteBtn').onclick = toggleMute;
$('#realtimeTextForm').onsubmit = (e) => { e.preventDefault(); const v=$('#realtimeText'); sendRealtimeText(v.value); v.value=''; };
$('#chatForm').onsubmit = (e) => { e.preventDefault(); const v=$('#chatInput'); if (v.value.trim()) { sendTextChat(v.value.trim()); v.value=''; } };
$('#memoryForm').onsubmit = async (e) => {
  e.preventDefault();
  const text = $('#memoryText').value.trim(); if (!text) return;
  if (!confirm('Bu bilgiyi KUTALP uzun dönem hafızasına kaydetsin mi?')) return;
  await api('/api/memories', {method:'POST', body:JSON.stringify({text, kind:$('#memoryKind').value})});
  $('#memoryText').value=''; toast('Hafızaya kaydedildi'); loadMemories();
};
$('#refreshTasks').onclick = loadTasks;
$('#refreshPredictions').onclick = loadPredictions;
$('#saveToken').onclick = () => { localStorage.setItem('kutalp_access_token', $('#accessToken').value.trim()); toast('Yerel erişim anahtarı kaydedildi'); loadConfig(); };
$('#reloadConfig').onclick = refreshAll;

window.addEventListener('beforeunload', disconnectRealtime);
if ('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js').catch(console.error);
refreshAll();

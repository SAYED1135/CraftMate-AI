// Mocked end-to-end harness for the CraftMate Worker (no real Supabase/Gemini).
// Run: node worker/tests/mock-harness.mjs
const fs = await import('node:fs/promises');
const workerSrc = await fs.readFile(new URL('../src/index.js', import.meta.url), 'utf8');
let mode = 'missing';
const ownerId = '11111111-1111-4111-8111-111111111111';
const serverId = '22222222-2222-4222-8222-222222222222';
const plain = 'task-token-secret';
async function sha(t) { const h = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(t)); return [...new Uint8Array(h)].map(b => b.toString(16).padStart(2, '0')).join(''); }
const tokenHash = await sha(plain);
const expires = new Date(Date.now() + 3600000).toISOString();
const server = { id: serverId, owner_id: ownerId, name: 'Control Test', host_provider: 'play_hosting', server_software: 'Paper', mc_version: '1.21.4', status: 'ONLINE', connector_version: '0.6.2', address: null, link_method: 'bot', bot_username: null, bot_verify_status: 'none', bot_verified_at: null, environment_snapshot: { server_version: 'Paper 1.21.4', online_players: 1, max_players: 20, plugins: [], capabilities: {} }, last_heartbeat_at: new Date().toISOString(), control_enabled: true, control_mode: 'full_instant', control_expires_at: expires, control_activated_at: new Date().toISOString() };
function J(x, s = 200) { return new Response(JSON.stringify(x), { status: s, headers: { 'content-type': 'application/json' } }); }
const realFetch = global.fetch;
global.fetch = async (url, opts = {}) => {
  const u = String(url);
  if (u.includes('/auth/v1/user')) return J({ id: ownerId, email: 'owner@example.com' });
  if (u.includes('/rest/v1/servers?')) { if ((opts.method || 'GET') === 'PATCH') { try { Object.assign(server, JSON.parse(opts.body)); } catch {} } return J([server]); }
  if (u.includes('/rest/v1/connector_tokens?')) {
    if (u.includes('token_hash=eq.')) return J([{ id: 'tok', server_id: serverId, token_hash: tokenHash, claimed_at: new Date().toISOString(), is_revoked: false }]);
    return J([{ id: 'tok', claimed_at: new Date().toISOString() }]);
  }
  if (u.includes('/rest/v1/server_tasks')) {
    if (mode === 'missing') return J({ message: 'column missing' }, 400);
    if ((opts.method || 'GET') === 'POST') { const b = JSON.parse(opts.body); return J([{ id: '33333333-3333-4333-8333-333333333333', ...b }], 201); }
    return J([]);
  }
  if (u.includes('generativelanguage')) {
    const raw = String(opts.body || '');
    if (raw.includes('TOOLTIME') && raw.includes('functionDeclarations')) return J({ candidates: [{ content: { parts: [{ functionCall: { name: 'set_time', args: { preset: 'NIGHT' } } }] } }] });
    if (raw.includes('TOOLMULTI') && raw.includes('functionDeclarations')) return J({ candidates: [{ content: { parts: [{ functionCall: { name: 'set_weather', args: { preset: 'RAIN' } } }, { functionCall: { name: 'set_difficulty', args: { difficulty: 'HARD' } } }] } }] });
    if (raw.includes('TOOLBAD') && raw.includes('functionDeclarations')) return J({ candidates: [{ content: { parts: [{ functionCall: { name: 'set_time', args: { preset: 'BRUNCH' } } }] } }] });
    return J({ candidates: [{ content: { parts: [{ text: 'mock answer' }] } }] });
  }
  if (u.includes('/rest/v1/')) {
    if ((opts.method || 'GET') === 'PATCH') { try { Object.assign(server, JSON.parse(opts.body)); } catch {} return J([{ id: serverId }]); }
    if ((opts.method || 'GET') === 'POST') return J([{ id: 'x' }], 201);
    return J([]);
  }
  return realFetch(url, opts);
};
const mod = await import('data:text/javascript;base64,' + Buffer.from(workerSrc).toString('base64'));
const env = { SUPABASE_URL: 'https://db.test', SUPABASE_SERVICE_ROLE_KEY: 'service', GEMINI_API_KEY: 'key', BOT_RUNNER_SECRET: 'runner-secret', BOT_CREDENTIALS_KEY: Buffer.from('0123456789abcdef0123456789abcdef').toString('base64') };
const ctx = { waitUntil() {} };
async function call(path, body, auth, headers = {}) {
  return await mod.default.fetch(new Request('https://worker' + path, { method: 'POST', headers: { 'content-type': 'application/json', authorization: auth ? 'Bearer ' + auth : '', ...headers }, body: JSON.stringify(body) }), env, ctx);
}
let n = 0; function ok(v, m) { n++; if (!v) throw new Error(m); console.log('PASS', n, m); }
let r = await call('/v1/control/status', { server_id: serverId }); let j = await r.json(); ok(r.status === 401 && j.error.code === 'CM-CTRL-401', 'control status unauth');
r = await call('/v1/plugin/tasks/poll', { server_id: serverId, connector_token: plain }); j = await r.json(); ok(r.status === 200 && j.ok && j.task === null && j.tasks_ready === false, 'plugin poll degrades pre-migration');
server.control_enabled = false; server.control_mode = 'off'; server.control_expires_at = null;
r = await call('/v1/tasks/create', { server_id: serverId, type: 'GET_RECENT_LOGS' }, 'user-token'); j = await r.json(); ok(r.status === 403 && j.error.code === 'CM-CTRL-403', 'task create blocks while control off');
mode = 'ready'; server.control_enabled = true; server.control_mode = 'full_instant'; server.control_expires_at = expires;
r = await call('/v1/tasks/create', { server_id: serverId, type: 'GET_RECENT_LOGS', payload: { max_lines: 999 } }, 'user-token'); j = await r.json(); ok(r.status === 200 && j.task.status === 'QUEUED' && j.task.payload.max_lines === 250, 'task create clamps logs');
r = await call('/v1/tasks/create', { server_id: serverId, type: 'SHELL_COMMAND' }, 'user-token'); j = await r.json(); ok(r.status === 422 && j.error.code === 'CM-TASK-422', 'unknown task type rejected');
r = await call('/v1/tasks/create', { server_id: serverId, type: 'SET_TIME', payload: { preset: 'DAY' } }, 'user-token'); j = await r.json(); ok(r.status === 200 && j.task.risk === 'write', 'write task queued with risk');
r = await call('/v1/tasks/create', { server_id: serverId, type: 'SET_TIME', payload: { preset: 'BRUNCH' } }, 'user-token'); j = await r.json(); ok(r.status === 422, 'invalid preset rejected');
server.control_enabled = false; server.control_mode = 'off'; server.control_expires_at = null;
r = await call('/v1/ai/server-chat', { server_id: serverId, question: 'خلي الوقت نهار', locale: 'ar' }, 'user-token'); j = await r.json(); ok(r.status === 200 && j.action_required === 'enable_control', 'chat command asks to enable control');
server.control_enabled = true; server.control_mode = 'full_instant'; server.control_expires_at = expires;
r = await call('/v1/ai/server-chat', { server_id: serverId, question: 'خلي الوقت نهار', locale: 'ar' }, 'user-token'); j = await r.json(); ok(r.status === 200 && j.task && j.task.type === 'SET_TIME', 'Arabic direct command queues task');
r = await call('/v1/ai/server-chat', { server_id: serverId, question: '/time set day', locale: 'en' }, 'user-token'); j = await r.json(); ok(r.status === 200 && j.task && j.task.type === 'SET_TIME', '/time set day maps to task');
r = await call('/v1/ai/server-chat', { server_id: serverId, question: 'TOOLTIME لو سمحت', locale: 'ar' }, 'user-token'); j = await r.json(); ok(r.status === 200 && j.planned_by === 'model' && j.task && j.task.type === 'SET_TIME', 'model function call queues task');
r = await call('/v1/ai/server-chat', { server_id: serverId, question: 'TOOLMULTI لو سمحت', locale: 'ar' }, 'user-token'); j = await r.json(); ok(r.status === 200 && Array.isArray(j.tasks) && j.tasks.length === 2, 'model multi-call queues two tasks');
r = await call('/v1/ai/server-chat', { server_id: serverId, question: 'TOOLBAD لو سمحت', locale: 'ar' }, 'user-token'); j = await r.json(); ok(r.status === 200 && !j.task && typeof j.answer === 'string', 'invalid model call falls back to text');
r = await call('/v1/ai/server-chat', { server_id: serverId, question: 'ما حالة سيرفري الآن؟', locale: 'ar' }, 'user-token'); j = await r.json(); ok(r.status === 200 && !j.task && typeof j.answer === 'string', 'normal question stays text');
// Bot connection-account path
r = await call('/v1/bot/identity', { server_id: serverId }); j = await r.json(); ok(r.status === 401 && j.error.code === 'CM-BOT-401', 'bot identity requires login');
r = await call('/v1/bot/identity', { server_id: serverId }, 'user-token'); j = await r.json(); ok(r.status === 200 && /^CraftMate_[A-Z0-9]{5}$/.test(j.bot_username || ''), 'bot identity assigns username');
r = await call('/v1/bot/identity', { server_id: serverId, email: 'botconn@example.com', password: 'SecretPw-123', requested_username: 'MyHelper_7' }, 'user-token'); j = await r.json(); ok(r.status === 200 && j.bot_username === 'MyHelper_7' && j.bot_verify_status === 'waiting_server', 'identity v2 approves requested name + waiting state');
ok(typeof server.bot_password_enc === 'string' && server.bot_password_enc.startsWith('v1.') && !server.bot_password_enc.includes('SecretPw-123'), 'connection password stored encrypted only');
ok(server.bot_email === 'botconn@example.com', 'connection email stored');
r = await call('/v1/bot/request-verify', { server_id: serverId, address: 'bad address!' }, 'user-token'); j = await r.json(); ok(r.status === 422 && j.error.code === 'CM-BOT-422', 'bad address rejected');
r = await call('/v1/bot/request-verify', { server_id: serverId, address: 'mc.example.net:25565' }, 'user-token'); j = await r.json(); ok(r.status === 200 && j.bot_verify_status === 'requested', 'verify requested');
server.bot_verify_status = 'requested'; server.address = 'mc.example.net:25565'; server.bot_username = 'CraftMate_T1';
r = await call('/v1/runner/jobs/next', {}); j = await r.json(); ok(r.status === 401 && j.error.code === 'CM-BOT-401', 'runner rejects missing secret');
r = await call('/v1/runner/jobs/next', {}, null, { 'x-runner-secret': 'runner-secret' }); j = await r.json(); ok(r.status === 200 && j.job && j.job.kind === 'verify' && j.job.bot_username === 'CraftMate_T1', 'runner picks up verify job');
ok(j.job.bot_password === 'SecretPw-123', 'runner receives decrypted password only via secret channel');
r = await call('/v1/runner/jobs/verify-result', { server_id: serverId, ok: true, op_verified: true, detail: 'The time is 1000' }, null, { 'x-runner-secret': 'runner-secret' }); j = await r.json(); ok(r.status === 200 && j.verified === true, 'verify result accepted');
console.log('ALL MOCK TESTS PASSED', n);

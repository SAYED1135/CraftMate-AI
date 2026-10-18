// CraftMate Platform Connector — the platform's own connection account.
// It joins a linked Minecraft (Java, offline-mode) server under a
// platform-owned username, verifies the privileges the owner granted,
// and executes ONLY validated command templates coming from CraftMate's
// task queue. It never accepts free-form console input from the model.
//
// Modes:
//   node runner.mjs verify  --host h --port p --username U
//   node runner.mjs serve   (env: CM_API, CM_RUNNER_SECRET)
import mineflayer from 'mineflayer';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function offlineJoin({ host, port = 25565, username, version = false }) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host, port: Number(port), username, auth: 'offline', version: version || undefined });
    bot._cmMessages = [];
    bot.on('message', (json) => {
      try {
        bot._cmMessages.push(json.toString());
        if (bot._cmMessages.length > 200) bot._cmMessages.shift();
      } catch {}
    });
    const timer = setTimeout(() => { try { bot.quit(); } catch {} reject(new Error('JOIN_TIMEOUT')); }, 30000);
    bot.once('spawn', () => { clearTimeout(timer); resolve(bot); });
    bot.once('error', (e) => { clearTimeout(timer); reject(e); });
    bot.once('kicked', (reason) => { clearTimeout(timer); reject(new Error('KICKED: ' + String(reason).slice(0, 200))); });
  });
}

function waitForMessage(bot, regex, timeoutMs = 8000) {
  return new Promise((resolve) => {
    const timer = setTimeout(() => { cleanup(); resolve(null); }, timeoutMs);
    const onMessage = (json) => {
      const text = json.toString();
      const m = text.match(regex);
      if (m) { cleanup(); resolve({ text, match: m }); }
    };
    const cleanup = () => { clearTimeout(timer); bot.removeListener('message', onMessage); };
    bot.on('message', onMessage);
  });
}

async function handleServerAuth(bot, password) {
  if (!password) return 'none';
  await sleep(1200);
  const recent = () => bot._cmMessages.slice(-12).join(' ');
  let txt = recent();
  if (!/register|login/i.test(txt)) {
    const r = await waitForMessage(bot, /(register|login)/i, 2500);
    if (r) txt = r.text;
  }
  if (/register/i.test(txt) && !/already/i.test(txt)) {
    bot.chat(`/register ${password} ${password}`);
    await sleep(1300);
    if (/already/i.test(recent())) { bot.chat(`/login ${password}`); await sleep(1000); return 'register-then-login'; }
    return 'registered';
  }
  if (/login/i.test(txt)) { bot.chat(`/login ${password}`); await sleep(1000); return 'login'; }
  return 'no-prompt';
}

async function checkOp(bot) {
  bot.chat('/time query daytime');
  const res = await waitForMessage(bot, /The time is (\d+)|do not have permission|Unknown or incomplete command/i, 8000);
  if (!res) return { op: null, detail: 'no response to privilege check' };
  if (/permission|Unknown or incomplete/i.test(res.text)) return { op: false, detail: res.text.slice(0, 200) };
  return { op: true, detail: res.text.slice(0, 200), time: res.match[1] ? Number(res.match[1]) : null };
}

const TEMPLATES = {
  SET_TIME: {
    command: (p) => `time set ${p.ticks !== undefined && p.ticks !== null && p.ticks !== '' ? Number(p.ticks) : String(p.preset || 'DAY').toLowerCase()}`,
    verify: async (bot, p) => {
      bot.chat('/time query daytime');
      const res = await waitForMessage(bot, /The time is (\d+)/, 8000);
      if (!res) return { verified: false, detail: 'time query unanswered' };
      const now = Number(res.match[1]);
      const target = p.ticks !== undefined && p.ticks !== null && p.ticks !== '' ? Number(p.ticks) : { DAY: 1000, SUNRISE: 1000, NOON: 6000, NIGHT: 13000, MIDNIGHT: 18000 }[String(p.preset || 'DAY').toUpperCase()];
      const diff = Math.abs(now - target); const wrap = Math.min(diff, 24000 - diff);
      return { verified: wrap <= 1200, detail: `daytime=${now} target=${target}` };
    },
  },
  SET_WEATHER: {
    command: (p) => `weather ${String(p.preset || 'CLEAR').toLowerCase()}`,
    verify: async () => ({ verified: true, detail: 'weather command accepted (no in-game query exists)' }),
  },
  SET_DIFFICULTY: {
    command: (p) => `difficulty ${String(p.difficulty || 'NORMAL').toLowerCase()}`,
    verify: async (bot, p) => {
      bot.chat('/difficulty');
      const res = await waitForMessage(bot, /The difficulty is (\w+)/i, 8000);
      if (!res) return { verified: false, detail: 'difficulty query unanswered' };
      return { verified: res.match[1].toUpperCase() === String(p.difficulty).toUpperCase(), detail: res.text.slice(0, 120) };
    },
  },
  SET_DEFAULT_GAMEMODE: {
    command: (p) => `defaultgamemode ${String(p.gamemode || 'SURVIVAL').toLowerCase()}`,
    verify: async () => ({ verified: true, detail: 'defaultgamemode command accepted' }),
  },
  BROADCAST_MESSAGE: {
    command: (p) => `say ${String(p.message || '').slice(0, 200)}`,
    verify: async () => ({ verified: true, detail: 'broadcast sent' }),
  },
};

async function executeTemplate(bot, type, payload) {
  if (type === 'GET_RECENT_LOGS') {
    const lines = (bot._cmMessages || []).slice(-80);
    return { status: 'SUCCESS', data: { scrubbed_log: lines.join('\n'), note: 'Recent in-game chat/system lines seen by the platform account (file logs need host access).' } };
  }
  if (type === 'GET_ENVIRONMENT_SNAPSHOT') {
    const players = Object.values(bot.players || {}).map((p) => p?.username).filter(Boolean);
    return { status: 'SUCCESS', data: { server_version: String(bot.version || ''), online_players: players.length, players, plugins: [], note: 'Snapshot-lite from the platform connection account.' } };
  }
  const tpl = TEMPLATES[type];
  if (!tpl) return { status: 'FAILED', error: 'Template not supported by platform connector: ' + type };
  const command = tpl.command(payload || {});
  bot.chat('/' + command);
  await sleep(600);
  const v = await tpl.verify(bot, payload || {});
  return { status: v.verified ? 'SUCCESS' : 'FAILED', data: { command, ...v } };
}

function parseAddress(address) {
  const raw = String(address || '').trim();
  const m = raw.match(/^(.+?):(\d{1,5})$/) || [null, raw, '25565'];
  return { host: m[1], port: Number(m[2] || 25565) };
}

async function modeVerify(args) {
  const addr = parseAddress(args.address || `${args.host || '127.0.0.1'}:${args.port || 25565}`);
  const bot = await offlineJoin({ ...addr, username: args.username });
  await handleServerAuth(bot, args.password);
  const op = await checkOp(bot);
  console.log(JSON.stringify({ joined: true, username: args.username, address: `${addr.host}:${addr.port}`, op: op.op, detail: op.detail }));
  bot.quit();
}

async function workerCall(path, body) {
  const base = String(process.env.CM_API || '').replace(/\/+$/, '');
  const r = await fetch(base + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Runner-Secret': process.env.CM_RUNNER_SECRET || '' },
    body: JSON.stringify(body || {}),
  });
  const data = await r.json().catch(() => ({}));
  if (!r.ok || !data.ok) throw new Error(data?.error?.code || ('HTTP-' + r.status));
  return data;
}

const PANEL_UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36';
async function panelLoginCheck(job) {
  const base = String(job.panel_url || '').replace(/\/+$/, '');
  if (!base) return { login_ok: false, detail: 'PANEL_URL_MISSING' };
  const jar = new Map();
  const cookieHeader = () => [...jar.entries()].map(([k, v]) => k + '=' + v).join('; ');
  const absorb = (res) => { try { for (const c of res.headers.getSetCookie()) { const pair = c.split(';')[0]; const i = pair.indexOf('='); if (i > 0) jar.set(pair.slice(0, i).trim(), pair.slice(i + 1).trim()); } } catch {} };
  const xsrf = () => { const v = jar.get('XSRF-TOKEN'); return v ? decodeURIComponent(v) : ''; };
  if (job.api_key) {
    try {
      const h = { Authorization: 'Bearer ' + job.api_key, Accept: 'application/vnd.pterodactyl.v1+json', 'User-Agent': PANEL_UA };
      const aRes = await fetch(base + '/api/client/account', { headers: h, signal: AbortSignal.timeout(20000) });
      const aTxt = await aRes.text().catch(() => '');
      if (/just a moment|challenges\.cloudflare/i.test(aTxt)) return { login_ok: false, detail: 'PANEL_BOT_CHALLENGE_API: panel blocked API access too' };
      if (!aRes.ok) return { login_ok: false, detail: 'API_HTTP_' + aRes.status };
      let username = null; try { username = JSON.parse(aTxt)?.attributes?.username || null; } catch {}
      const lRes = await fetch(base + '/api/client?per_page=100', { headers: h, signal: AbortSignal.timeout(20000) });
      let servers = [];
      if (lRes.ok) { const lj = await lRes.json().catch(() => ({})); servers = (lj.data || []).map((x) => x?.attributes?.name).filter(Boolean); }
      return { login_ok: true, server_found: servers.length > 0, username, detail: servers.length ? ('servers visible: ' + servers.slice(0, 3).join(', ')) : 'api ok; no server shared yet' };
    } catch (e) { return { login_ok: false, detail: 'API_UNREACHABLE: ' + String(e.message || e).slice(0, 160) }; }
  }
  const timed = (ms) => AbortSignal.timeout(ms);
  try {
    let res = await fetch(base + '/', { headers: { 'User-Agent': PANEL_UA }, signal: timed(20000) });
    absorb(res);
    const home = await res.text().catch(() => '');
    if (/just a moment|challenges\.cloudflare/i.test(home)) return { login_ok: false, detail: 'PANEL_BOT_CHALLENGE: hosting panel bot protection blocked automated login' };
    res = await fetch(base + '/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'application/json', 'X-XSRF-TOKEN': xsrf(), Cookie: cookieHeader(), 'User-Agent': PANEL_UA }, body: JSON.stringify({ user: job.email, password: job.password }), signal: timed(20000) });
    absorb(res);
    if (!res.ok) return { login_ok: false, detail: 'LOGIN_HTTP_' + res.status };
    const apiHeaders = () => ({ Accept: 'application/vnd.pterodactyl.v1+json', Cookie: cookieHeader(), 'X-XSRF-TOKEN': xsrf(), 'X-Requested-With': 'XMLHttpRequest', 'User-Agent': PANEL_UA });
    let acctRes = await fetch(base + '/api/client/account', { headers: apiHeaders(), signal: timed(20000) });
    absorb(acctRes);
    if (!acctRes.ok) return { login_ok: false, detail: 'ACCOUNT_HTTP_' + acctRes.status };
    let account = (await acctRes.json().catch(() => ({})))?.attributes || {};
    let username = account.username || null;
    if (job.requested_username && username && job.requested_username !== username) {
      try {
        const ren = await fetch(base + '/api/client/account/username', { method: 'PUT', headers: { ...apiHeaders(), 'Content-Type': 'application/json' }, body: JSON.stringify({ username: job.requested_username, password: job.password }), signal: timed(20000) });
        absorb(ren);
        if (ren.ok) username = job.requested_username;
      } catch {}
    }
    const listRes = await fetch(base + '/api/client?per_page=100', { headers: apiHeaders(), signal: timed(20000) });
    absorb(listRes);
    let servers = [];
    if (listRes.ok) { const lj = await listRes.json().catch(() => ({})); servers = (lj.data || []).map((x) => x?.attributes?.name).filter(Boolean); }
    return { login_ok: true, server_found: servers.length > 0, username, detail: servers.length ? ('servers visible: ' + servers.slice(0, 3).join(', ')) : 'logged in; no server shared yet' };
  } catch (e) {
    return { login_ok: false, detail: 'PANEL_UNREACHABLE: ' + String(e.message || e).slice(0, 160) };
  }
}

async function modeServe() {
  const pollSeconds = Math.max(3, Number(process.env.CM_POLL_SECONDS || 5));
  const maxMinutes = Math.max(1, Number(process.env.CM_MAX_RUNTIME_MIN || 300));
  const deadline = Date.now() + maxMinutes * 60 * 1000;
  const sessions = new Map();
  console.log('[craftmate-connector] serve mode; polling', process.env.CM_API);
  while (Date.now() < deadline) {
    try {
      const data = await workerCall('/v1/runner/jobs/next', {});
      const job = data.job;
      if (!job) { await sleep(pollSeconds * 1000); continue; }
      console.log('[craftmate-connector] job', job.kind, job.server_id || '', job.task_type || '');
      if (job.kind === 'panel_check') {
        const result = await panelLoginCheck(job);
        console.log('[craftmate-connector] panel_check', job.server_id, JSON.stringify(result).slice(0, 200));
        await workerCall('/v1/runner/jobs/panel-result', { server_id: job.server_id, ...result });
      } else if (job.kind === 'verify') {
        let result;
        try {
          const addr = parseAddress(job.address);
          const bot = await offlineJoin({ ...addr, username: job.bot_username });
          await handleServerAuth(bot, job.bot_password);
          const op = await checkOp(bot);
          result = { ok: op.op === true, op_verified: op.op === true, detail: op.detail };
          bot.quit();
        } catch (e) { result = { ok: false, op_verified: false, detail: String(e.message || e).slice(0, 240) }; }
        await workerCall('/v1/runner/jobs/verify-result', { server_id: job.server_id, ...result });
      } else if (job.kind === 'task') {
        let entry = sessions.get(job.server_id);
        try {
          if (!entry || !entry.bot || !entry.bot.entity) {
            const addr = parseAddress(job.address);
            const fresh = await offlineJoin({ ...addr, username: job.bot_username });
            await handleServerAuth(fresh, job.bot_password);
            entry = { bot: fresh, lastUsed: Date.now() };
            sessions.set(job.server_id, entry);
            fresh.once('end', () => sessions.delete(job.server_id));
            fresh.once('kicked', () => sessions.delete(job.server_id));
          }
          entry.lastUsed = Date.now();
          const result = await executeTemplate(entry.bot, job.task_type, job.payload || {});
          await workerCall('/v1/runner/jobs/task-result', { task_id: job.task_id, result: { task_id: job.task_id, task_type: job.task_type, ...result } });
        } catch (e) {
          await workerCall('/v1/runner/jobs/task-result', { task_id: job.task_id, result: { task_id: job.task_id, task_type: job.task_type, status: 'FAILED', error: String(e.message || e).slice(0, 240) } }).catch(() => {});
          sessions.delete(job.server_id);
        }
      }
    } catch (e) {
      console.error('[craftmate-connector] loop error', e.message);
      await sleep(pollSeconds * 1000);
    }
    // Idle session cleanup (10 minutes)
    for (const [id, s] of sessions) if (Date.now() - s.lastUsed > 10 * 60 * 1000) { try { s.bot.quit(); } catch {} sessions.delete(id); }
  }
  for (const [, s] of sessions) { try { s.bot.quit(); } catch {} }
  console.log('[craftmate-connector] runtime budget reached; exiting');
}

export { offlineJoin, checkOp, executeTemplate, parseAddress, handleServerAuth, panelLoginCheck, TEMPLATES };

const isMain = process.argv[1] && process.argv[1].endsWith('runner.mjs');
if (isMain) {
  const args = {};
  process.argv.slice(3).forEach((a, i, arr) => { if (a.startsWith('--')) args[a.slice(2)] = arr[i + 1]; });
  const mode = process.argv[2] || 'serve';
  if (mode === 'verify') await modeVerify(args);
  else await modeServe();
}

// Local fake-panel test for panelLoginCheck (node bot/test-panel.mjs)
import http from 'node:http';
import { panelLoginCheck } from './runner.mjs';
const shared = process.env.PANEL_SHARED === '1';
const srv = http.createServer((req, res) => {
  const u = new URL(req.url, 'http://x');
  if (u.pathname === '/') { res.setHeader('Set-Cookie', 'XSRF-TOKEN=abc%3D; Path=/'); return res.end('<html>panel</html>'); }
  if (u.pathname === '/auth/login' && req.method === 'POST') { let b=''; req.on('data',c=>b+=c); req.on('end',()=>{ const j=JSON.parse(b||'{}'); if(j.user==='cm@example.com'&&j.password==='SecretPw-123'){ res.setHeader('Set-Cookie','sess=1; Path=/'); return res.end('{}'); } res.statusCode=422; res.end('{}'); }); return; }
  if (u.pathname === '/api/client/account') return res.end(JSON.stringify({ attributes: { username: 'RealUser_1', email: 'cm@example.com' } }));
  if (u.pathname === '/api/client/account/username' && req.method === 'PUT') return res.end('{}');
  if (u.pathname === '/api/client') return res.end(JSON.stringify({ data: shared ? [{ attributes: { name: 'Friends Server' } }] : [] }));
  res.statusCode = 404; res.end('{}');
});
await new Promise(r => srv.listen(0, '127.0.0.1', r));
const base = 'http://127.0.0.1:' + srv.address().port;
const r = await panelLoginCheck({ panel_url: base, email: 'cm@example.com', password: 'SecretPw-123', requested_username: 'CraftMate_1' });
console.log(JSON.stringify(r));
srv.close();
if (!r.login_ok || r.username !== 'CraftMate_1' || r.server_found !== shared) { console.error('PANEL TEST FAILED'); process.exit(1); }
console.log('PANEL TEST PASSED (shared=' + shared + ')');

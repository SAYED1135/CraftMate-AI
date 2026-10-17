import { offlineJoin, checkOp, executeTemplate } from './runner.mjs';
const bot = await offlineJoin({ host: '127.0.0.1', port: 25599, username: 'CraftMateT1' });
console.log('JOINED as', bot.username, 'version', bot.version);
console.log('OP CHECK:', JSON.stringify(await checkOp(bot)));
for (const [type, payload] of [
  ['SET_TIME', { preset: 'NIGHT' }],
  ['SET_WEATHER', { preset: 'CLEAR' }],
  ['SET_DIFFICULTY', { difficulty: 'PEACEFUL' }],
  ['BROADCAST_MESSAGE', { message: 'مرحبًا من CraftMate' }],
]) {
  const r = await executeTemplate(bot, type, payload);
  console.log('EXEC', type, JSON.stringify(r));
}
bot.quit();
process.exit(0);

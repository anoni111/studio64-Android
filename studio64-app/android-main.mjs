// Punto de entrada en Android: lo ejecuta nodejs-mobile. Las variables (PORT, HOST, STUDIO_SETUP=off, HOME, TMPDIR) las pone MainActivity.
process.on('uncaughtException', (e) => console.error('[android] uncaughtException:', e && e.stack || e));
process.on('unhandledRejection', (e) => console.error('[android] unhandledRejection:', e && e.stack || e));
try { process.chdir(new URL('.', import.meta.url).pathname); } catch {}
process.env.STUDIO_SETUP = 'off';
process.env.HOST = '127.0.0.1';
process.env.PORT = process.env.PORT || '8064';
await import('./server/index.js');

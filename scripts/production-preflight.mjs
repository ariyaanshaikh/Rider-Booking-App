import { pathToFileURL } from 'node:url';

// Only field names/results are returned. Never print environment values or tokens.
export function checkConfiguration(env, mode = 'backend') {
  if (!['backend', 'admin', 'rider'].includes(mode)) throw Error('Use backend, admin or rider');
  const results = [];
  const check = (name, valid) => results.push({ name, ok: Boolean(valid) });
  const present = name => Boolean(env[name]?.trim()) && !/replace[-_ ]?with|your[-_ ]|placeholder|changeme/i.test(env[name]);
  const https = name => {
    try { const url = new URL(env[name]); return url.protocol === 'https:' && !url.username && !url.password && !url.search && !url.hash; }
    catch { return false; }
  };
  if (mode === 'backend') {
    check('SPRING_PROFILES_ACTIVE includes prod', env.SPRING_PROFILES_ACTIVE?.split(',').map(s => s.trim()).includes('prod'));
    check('JWT_SECRET (at least 32 bytes)', present('JWT_SECRET') && Buffer.byteLength(env.JWT_SECRET || '') >= 32);
    check('SPRING_DATASOURCE_URL (PostgreSQL JDBC)', /^jdbc:postgresql:\/\//.test(env.SPRING_DATASOURCE_URL || ''));
    for (const key of ['SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD', 'GOOGLE_MAPS_API_KEY', 'SPRING_MAIL_USERNAME', 'SPRING_MAIL_PASSWORD']) check(key, present(key));
    check('APP_PUBLIC_BASE_URL (HTTPS)', https('APP_PUBLIC_BASE_URL'));
    check('APP_MAIL_FROM (email)', /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(env.APP_MAIL_FROM || env.SPRING_MAIL_USERNAME || ''));
    check('SMTP authentication enabled', (env.SPRING_MAIL_SMTP_AUTH || 'true') === 'true');
    check('SMTP STARTTLS required', (env.SPRING_MAIL_STARTTLS_ENABLE || 'true') === 'true' && (env.SPRING_MAIL_STARTTLS_REQUIRED || 'true') === 'true');
    check('EXPO_PUSH_ENABLED', env.EXPO_PUSH_ENABLED === 'true');
    check('APP_SHARED_MESSAGING_ENABLED', (env.APP_SHARED_MESSAGING_ENABLED || 'true') === 'true');
    check('Razorpay deferred safely', (env.RAZORPAY_ENABLED || 'false') === 'false' && (env.RAZORPAY_ROUTE_ENABLED || 'false') === 'false');
  } else {
    check('EXPO_PUBLIC_API_URL (HTTPS)', https('EXPO_PUBLIC_API_URL'));
    check('EXPO_PUBLIC_EAS_PROJECT_ID (UUID)', /^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i.test(env.EXPO_PUBLIC_EAS_PROJECT_ID || ''));
    check('EXPO_PUBLIC_GOOGLE_MAPS_API_KEY', present('EXPO_PUBLIC_GOOGLE_MAPS_API_KEY'));
    check('No private keys in EXPO_PUBLIC variables', !Object.keys(env).some(key => key.startsWith('EXPO_PUBLIC_') && /SECRET|PASSWORD|PRIVATE|ACCESS_TOKEN|SERVICE_ACCOUNT/i.test(key) && env[key]));
  }
  return results;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const results = checkConfiguration(process.env, process.argv[2] || 'backend');
  for (const { name, ok } of results) console.log(`${ok ? 'PASS' : 'MISSING/INVALID'} ${name}`);
  console.log('Configuration checks only: does not prove provider delivery, backup restoration, deployment or device behavior.');
  process.exitCode = results.every(result => result.ok) ? 0 : 1;
}

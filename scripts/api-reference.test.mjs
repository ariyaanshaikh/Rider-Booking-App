import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';

const root = new URL('../', import.meta.url);
const reference = readFileSync(new URL('docs/API_REFERENCE.md', root), 'utf8');
const controllers = new URL('src/main/java/com/ariyaan/Demo/Controller/', root);
const blocked = path => ['/demo', '/sendJWT', '/register/updateUser'].includes(path) || path.startsWith('/otp/');

test('current API reference covers every nonblocked controller mapping', () => {
  let count = 0;
  for (const filename of readdirSync(controllers).filter(name => name.endsWith('.java'))) {
    const source = readFileSync(new URL(filename, controllers), 'utf8')
      .replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
    const prefix = source.match(/@RequestMapping\("([^"]*)"\)/)?.[1] || '';
    for (const match of source.matchAll(/@(Get|Post|Put|Delete|Patch)Mapping(?:\(\s*"([^"]*)"\s*\))?/g)) {
      const path = '/' + [prefix, match[2] || ''].join('/').split('/').filter(Boolean).join('/');
      if (blocked(path)) continue;
      const method = match[1].toUpperCase();
      assert.ok(reference.includes(path), `${filename}: missing ${method} ${path}`);
      const escaped = path.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      assert.match(reference, new RegExp(`(?:\\|\\s*${method}\\s*\\|\\s*${escaped}\\s*\\||${method} ${escaped})`), `${filename}: missing method ${method} ${path}`);
      count++;
    }
  }
  assert.ok(count >= 40, `Unexpectedly few mappings discovered: ${count}`);
});

test('latest generator uses the canonical reference and updated contracts remain explicit', () => {
  const generator = readFileSync(new URL('docs/build_rider_booking_reference_latest.py', root), 'utf8');
  assert.ok(generator.includes('SOURCE = ROOT / "API_REFERENCE.md"'));
  for (const term of ['AWAITING_PAYMENT', 'PAID invoice', '/ride-requests/{rideId}/decline', 'platform ios', 'DeviceNotRegistered', 'production_schema_history', 'not a claim that it is deployed']) {
    assert.ok(reference.includes(term), `Missing contract distinction: ${term}`);
  }
});

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { checkConfiguration } from './production-preflight.mjs';

const backend = {
  SPRING_PROFILES_ACTIVE: 'prod', JWT_SECRET: 'a-test-only-key-that-is-at-least-32-bytes',
  SPRING_DATASOURCE_URL: 'jdbc:postgresql://db.example.com/rides', SPRING_DATASOURCE_USERNAME: 'test',
  SPRING_DATASOURCE_PASSWORD: 'test-password', GOOGLE_MAPS_API_KEY: 'test-map-key',
  SPRING_MAIL_USERNAME: 'sender@example.com', SPRING_MAIL_PASSWORD: 'test-mail-password',
  APP_PUBLIC_BASE_URL: 'https://api.example.com', EXPO_PUSH_ENABLED: 'true'
};
test('accepts complete non-payment backend settings without requiring Razorpay keys', () => {
  assert.ok(checkConfiguration(backend).every(result => result.ok));
});
test('rejects missing secrets, insecure URLs, disabled push and premature payment enablement', () => {
  for (const [key, value] of Object.entries({ JWT_SECRET: '', APP_PUBLIC_BASE_URL: 'http://example.com', EXPO_PUSH_ENABLED: 'false', RAZORPAY_ENABLED: 'true', SPRING_MAIL_STARTTLS_REQUIRED: 'false' }))
    assert.ok(checkConfiguration({ ...backend, [key]: value }).some(result => !result.ok), key);
});
test('validates mobile identity and prevents accidentally public server credentials', () => {
  const mobile = { EXPO_PUBLIC_API_URL: 'https://api.example.com', EXPO_PUBLIC_EAS_PROJECT_ID: '12345678-1234-1234-1234-123456789012', EXPO_PUBLIC_GOOGLE_MAPS_API_KEY: 'test-map-key' };
  for (const mode of ['admin', 'rider']) {
    assert.ok(checkConfiguration(mobile, mode).every(result => result.ok));
    assert.ok(checkConfiguration({ ...mobile, EXPO_PUBLIC_RAZORPAY_KEY_SECRET: 'do-not-expose' }, mode).some(result => !result.ok));
    assert.ok(checkConfiguration({ ...mobile, EXPO_PUBLIC_EAS_PROJECT_ID: 'replace-with-your-project-id' }, mode).some(result => !result.ok));
  }
});
test('results never contain credential values', () => {
  const output = JSON.stringify(checkConfiguration(backend));
  for (const key of ['JWT_SECRET', 'SPRING_DATASOURCE_PASSWORD', 'SPRING_MAIL_PASSWORD']) assert.ok(!output.includes(backend[key]));
});

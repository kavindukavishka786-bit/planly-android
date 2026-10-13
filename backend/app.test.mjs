import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { createHandler, validatePlan } from './app.mjs';

const token = 'test-only-access-token-01234567890123456789';
const steps = [{ title: 'Outline key points', minutes: 15, notes: 'Choose three clear ideas.' }];
async function withServer(t, handler) {
  const server = createServer(handler);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => new Promise(resolve => { server.close(resolve); server.closeAllConnections(); }));
  return `http://127.0.0.1:${server.address().port}`;
}
function request(base, data, auth = token) { return fetch(`${base}/api/plan`, {
  method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${auth}` }, body: JSON.stringify(data)
}); }
const successful = async () => new Response(JSON.stringify({ status: 'completed', steps: [{ type: 'model_output', content: [{ type: 'text', text: JSON.stringify({ steps }) }] }] }));

test('unauthorized calls never reach Gemini', async t => {
  let called = false;
  const base = await withServer(t, createHandler({ token, apiKey: 'test-key' }, { fetchImpl: async () => { called = true; return successful(); } }));
  assert.equal((await request(base, { goal: 'Prepare presentation' }, 'wrong')).status, 401);
  assert.equal(called, false);
});
test('unconfigured service fails closed', async t => {
  const base = await withServer(t, createHandler({}));
  assert.equal((await request(base, { goal: 'Prepare presentation' })).status, 503);
});
test('sends only the requested goal, disables provider storage, validates result', async t => {
  const base = await withServer(t, createHandler({ token, apiKey: 'test-key' }, { fetchImpl: async (url, options) => {
    assert.equal(url, 'https://generativelanguage.googleapis.com/v1beta/interactions');
    const body = JSON.parse(options.body);
    assert.equal(body.model, 'gemini-3.5-flash-lite'); assert.equal(body.store, false);
    assert.equal(body.input, 'Prepare presentation'); assert.equal(body.tools, undefined);
    assert.equal(JSON.stringify(body).includes('private-task-not-sent'), false);
    return successful();
  } }));
  const response = await request(base, { goal: 'Prepare presentation', tasks: ['private-task-not-sent'] });
  assert.equal(response.status, 200); assert.deepEqual(await response.json(), { steps });
});
test('invalid goal fails before provider call', async t => {
  let called = false;
  const base = await withServer(t, createHandler({ token, apiKey: 'test-key' }, { fetchImpl: async () => { called = true; return successful(); } }));
  assert.equal((await request(base, { goal: 'x' })).status, 400); assert.equal(called, false);
});
test('malformed model output is rejected without exposing provider data', async t => {
  const base = await withServer(t, createHandler({ token, apiKey: 'test-key' }, { fetchImpl: async () => new Response('{"status":"completed","steps":[]}') }));
  const response = await request(base, { goal: 'Prepare presentation' });
  assert.equal(response.status, 502); assert.equal(JSON.stringify(await response.json()).includes('test-key'), false);
});
test('rate limit stops extra provider requests', async t => {
  const base = await withServer(t, createHandler({ token, apiKey: 'test-key', perMinute: 1 }, { fetchImpl: successful }));
  assert.equal((await request(base, { goal: 'Prepare presentation' })).status, 200);
  assert.equal((await request(base, { goal: 'Prepare presentation' })).status, 429);
});
test('plan validation rejects unsupported durations and oversized output', () => {
  assert.throws(() => validatePlan({ steps: [{ title: 'Read', minutes: -1, notes: '' }] }));
  assert.throws(() => validatePlan({ steps: new Array(9).fill(steps[0]) }));
  assert.throws(() => validatePlan({ steps: [{ title: '', minutes: 15, notes: '' }] }));
});
test('provider throttle maps to a retryable status', async t => {
  const base = await withServer(t, createHandler({ token, apiKey: 'test-key' }, { fetchImpl: async () => new Response('{}', { status: 429 }) }));
  assert.equal((await request(base, { goal: 'Prepare presentation' })).status, 429);
});

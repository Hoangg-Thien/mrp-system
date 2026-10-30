import test from 'node:test'
import assert from 'node:assert/strict'
import { singleFlight } from './singleFlight.js'

test('coalesces same-tick submissions and permits a new submission after settlement', async () => {
  let calls = 0
  let finish
  const submit = singleFlight(() => { calls++; return new Promise(resolve => { finish = resolve }) })
  const first = submit()
  assert.equal(submit(), first)
  await Promise.resolve()
  assert.equal(calls, 1)
  finish('saved')
  assert.equal(await first, 'saved')
  const next = submit()
  await Promise.resolve()
  assert.equal(calls, 2)
  finish('again')
  await next
})

test('releases the guard after failure so retry remains possible', async () => {
  let calls = 0
  const submit = singleFlight(async () => { if (++calls === 1) throw new Error('failed'); return 'ok' })
  await assert.rejects(submit(), /failed/)
  assert.equal(await submit(), 'ok')
})

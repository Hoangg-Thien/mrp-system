import test from 'node:test'
import assert from 'node:assert/strict'
import { fetchAllPages } from './allPages.js'

test('loads every page with at most 100 rows, including the last option', async () => {
  const calls = []
  const result = await fetchAllPages(async (page, size) => {
    calls.push([page, size])
    return { totalPages: 3, content: Array.from({ length: page === 2 ? 1 : 100 }, (_, i) => page * 100 + i) }
  })
  assert.deepEqual(calls, [[0, 100], [1, 100], [2, 100]])
  assert.equal(result.content.length, 201)
  assert.equal(result.content.at(-1), 200)
})

test('does not return a partial options list if a later page fails', async () => {
  await assert.rejects(fetchAllPages(async page => {
    if (page === 1) throw new Error('unavailable')
    return { totalPages: 2, content: [1] }
  }), /unavailable/)
})

// Browser checks with mocked APIs; no application database is used.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import { createServer } from 'vite'

const root = fileURLToPath(new URL('../../', import.meta.url))
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || path.join(root, 'backend/target/ui-test-tools/node_modules/playwright'))
const origin = 'http://127.0.0.1:15275'
const server = await createServer({
  root: path.join(root, 'frontend'),
  configFile: path.join(root, 'frontend/vite.config.js'),
  define: { 'import.meta.env.VITE_API_BASE_URL': JSON.stringify(origin) },
  server: { host: '127.0.0.1', port: 15275, strictPort: true },
})
let browser
const errors = []
const submitted = []
try {
  await server.listen()
  browser = await chromium.launch({ headless: true })
  const context = await browser.newContext()
  await context.addInitScript(() => sessionStorage.setItem('mrp_token', 'regression-only'))
  const page = await context.newPage()
  page.on('pageerror', (error) => errors.push(error.message))
  await page.route((url) => url.pathname.startsWith('/api/'), async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    switch (url.pathname) {
      case '/api/auth/me':
        return route.fulfill({ json: { id: 1, profileId: 1, username: 'regression', fullName: 'Regression', role: 'STAFF' } })
      case '/api/products':
        return route.fulfill({ json: { content: [{ id: 10, name: 'Regression Product', unit: 'piece' }], totalPages: 1, totalElements: 1 } })
      case '/api/fg-issue-requests':
        if (request.method() === 'POST') {
          submitted.push(request.postDataJSON())
          return route.fulfill({ json: { id: 77, hasStockShortage: false } })
        }
        return route.fulfill({ json: { content: [], totalPages: 0, totalElements: 0 } })
      default:
        errors.push(`Unexpected API: ${url.pathname}`)
        return route.fulfill({ status: 500, json: { detail: 'Unexpected API' } })
    }
  })
  await page.goto(`${origin}/staff/fg-requests`)
  await page.waitForLoadState('networkidle')
  const dialog = page.getByRole('dialog', { name: 'Lập phiếu đề nghị xuất bán thành phẩm', exact: true })
  const openRequest = async () => {
    await page.getByRole('button', { name: /Lập đề nghị xuất bán mới/ }).click()
    await dialog.getByLabel('Sản phẩm', { exact: true }).click()
    await page.locator('.ant-select-item-option').filter({ hasText: 'Regression Product' }).click()
  }
  const note = dialog.getByLabel('Thông tin khách hàng / Mã đơn hàng / Ghi chú', { exact: true })
  const send = dialog.getByRole('button', { name: 'Gửi đề nghị (PENDING)', exact: true })
  for (const invalid of ['', '   ', '\t\n\r ']) {
    await openRequest()
    await note.fill(invalid)
    await send.click()
    await dialog.getByText('Vui lòng nhập thông tin khách hàng hoặc đơn hàng', { exact: true }).waitFor()
    assert.equal(submitted.length, 0, 'Blank notes must be blocked before sending the API request')
    await dialog.getByRole('button', { name: 'Hủy', exact: true }).click()
    await dialog.waitFor({ state: 'hidden' })
    console.log(`PASS frontend rejects ${JSON.stringify(invalid)} without calling the create API`)
  }
  await openRequest()
  await note.fill('  Khách hàng ABC - Đơn DH001  ')
  const created = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/fg-issue-requests'
    && response.request().method() === 'POST')
  await send.click()
  assert.equal((await created).status(), 200)
  await dialog.waitFor({ state: 'hidden' })
  assert.equal(submitted.length, 1)
  assert.equal(submitted[0].note, 'Khách hàng ABC - Đơn DH001')
  assert.deepEqual(submitted[0].items, [{ productId: 10, quantity: 1 }])
  assert.deepEqual(errors, [])
  console.log('PASS a valid note is trimmed and submitted once; the dialog closes after success')
} finally {
  await browser?.close()
  await server.close()
}

// Real UI components with mock API responses; no application database is used.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import { mkdir } from 'node:fs/promises'
import { createServer } from 'vite'

const root = fileURLToPath(new URL('../../', import.meta.url))
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || path.join(root, 'backend/target/ui-test-tools/node_modules/playwright'))
const origin = 'http://127.0.0.1:15279'
const evidence = path.join(root, 'tmp/mir-warning-evidence')
const server = await createServer({
  root: path.join(root, 'frontend'), configFile: path.join(root, 'frontend/vite.config.js'),
  define: { 'import.meta.env.VITE_API_BASE_URL': JSON.stringify(origin) },
  server: { host: '127.0.0.1', port: 15279, strictPort: true },
})
const errors = []
let browser
try {
  await mkdir(evidence, { recursive: true })
  await server.listen()
  browser = await chromium.launch({ headless: true })
  for (const role of ['STAFF', 'WAREHOUSE_MANAGER']) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    await context.addInitScript(() => sessionStorage.setItem('mrp_token', 'mir-warning-regression-only'))
    for (const fixture of [
      { status: 'PENDING', shortage: true },
      { status: 'PENDING', shortage: false },
      // Deliberately retain a stale true flag to test the UI status guard as well.
      { status: 'APPROVED', shortage: true },
      { status: 'REJECTED', shortage: true },
      { status: 'CANCELLED', shortage: true },
    ]) {
      const page = await context.newPage()
      page.setDefaultTimeout(12000)
      page.on('pageerror', (error) => errors.push(error.message))
      await page.route((url) => url.pathname.startsWith('/api/'), async (route) => {
        const url = new URL(route.request().url())
        const item = {
          id: 10, productionRequestId: 1, requestedBy: 5, requestedByName: 'Nhân viên kiểm tra',
          reviewedBy: fixture.status === 'PENDING' ? null : 2,
          reviewedByName: fixture.status === 'PENDING' ? null : 'Quản lý kiểm tra',
          status: fixture.status, itemCount: 1, requestedAt: '2026-11-18T10:00:00',
          reviewedAt: fixture.status === 'PENDING' ? null : '2026-11-18T10:01:00',
          hasStockShortage: fixture.shortage, warning: fixture.shortage ? 'Kho hiện tại thiếu NVL' : null,
          items: [{ id: 1, materialId: 101, materialCode: 'NVL_GO_CUNG', materialName: 'Gỗ Cứng', unit: 'm3',
            quantity: fixture.shortage ? 2000 : 20, currentStock: 30 }],
        }
        const paged = (content) => route.fulfill({ json: { content, totalPages: content.length ? 1 : 0, totalElements: content.length } })
        switch (url.pathname) {
          case '/api/auth/me': return route.fulfill({ json: { id: 1, profileId: role === 'STAFF' ? 5 : 2, username: 'regression', fullName: 'Kiểm tra cảnh báo', role } })
          case '/api/staff': return route.fulfill({ json: [{ id: 5, fullName: 'Nhân viên kiểm tra' }] })
          case '/api/production-requests': return paged([])
          case '/api/material-issue-requests': return paged([item])
          case '/api/material-issue-requests/10': return route.fulfill({ json: item })
          default:
            errors.push(`Unexpected API: ${url.pathname}`)
            return route.fulfill({ status: 500, json: { detail: 'Unexpected API' } })
        }
      })
      await page.goto(`${origin}${role === 'STAFF' ? '/staff/material-requests' : '/warehouse/material-issues'}`)
      await page.getByRole('button', { name: /Chi tiết/ }).first().click()
      const dialog = page.getByRole('dialog', { name: 'Chi tiết phiếu xuất NVL #10', exact: true })
      await dialog.getByRole('cell', { name: 'Gỗ Cứng', exact: true }).waitFor()
      const warning = dialog.getByText('Cảnh báo tồn kho', { exact: true })
      if (role === 'WAREHOUSE_MANAGER') {
        const pending = fixture.status === 'PENDING'
        for (const name of ['Tồn kho hiện tại', 'Khả năng đáp ứng']) {
          assert.equal(await dialog.getByRole('columnheader', { name, exact: true }).count(), pending ? 1 : 0)
        }
        const quantityTitle = fixture.status === 'APPROVED' ? 'Số lượng đã xuất' : 'Số lượng xin xuất'
        await dialog.getByRole('columnheader', { name: quantityTitle, exact: true }).waitFor()
        if (pending) await dialog.getByText(fixture.shortage ? /^Thiếu / : 'Đủ tồn kho').waitFor()
      }
      if (fixture.status === 'PENDING' && fixture.shortage) await warning.waitFor()
      else assert.equal(await warning.count(), 0, `${role} ${fixture.status} must not show a shortage warning`)
      if (fixture.status === 'APPROVED') await page.screenshot({ path: path.join(evidence, `${role.toLowerCase()}-approved.png`), fullPage: true, animations: 'disabled' })
      console.log(`PASS ${role} ${fixture.status} shortage=${fixture.shortage}`)
      await page.close()
    }
    await context.close()
  }
  assert.deepEqual(errors, [])
  console.log('PASS 10 UI cases; no browser exceptions or unexpected API calls')
} finally {
  await browser?.close()
  await server.close()
}

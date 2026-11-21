// Browser regression checks with mocked APIs; no application database is used.
// Reuses the Playwright installation used by the existing QA scripts.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import { createServer } from 'vite'

const root = fileURLToPath(new URL('../../', import.meta.url))
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || path.join(root, 'backend/target/ui-test-tools/node_modules/playwright'))
const origin = 'http://127.0.0.1:15274'
const server = await createServer({
  root: path.join(root, 'frontend'),
  configFile: path.join(root, 'frontend/vite.config.js'),
  define: { 'import.meta.env.VITE_API_BASE_URL': JSON.stringify(origin) },
  server: { host: '127.0.0.1', port: 15274, strictPort: true },
})
let browser
const errors = []
try {
  await server.listen()
  browser = await chromium.launch({ headless: true })
  for (const role of ['WAREHOUSE_MANAGER', 'ADMIN']) {
    const context = await browser.newContext()
    await context.addInitScript(() => sessionStorage.setItem('mrp_token', 'regression-only'))
    const page = await context.newPage()
    page.on('pageerror', (error) => errors.push(error.message))
    let stock = 2, incoming = 0, reserved = 0, planCalls = 0, reportCalls = 0, rejectPlan = false, rejectReport = false
    await page.route((url) => url.pathname.startsWith('/api/'), async (route) => {
      const url = new URL(route.request().url())
      const json = (body, status = 200) => route.fulfill({ status, json: body })
      switch (url.pathname) {
        case '/api/auth/me':
          return json({ id: 1, profileId: 1, username: 'regression', fullName: 'Regression', role })
        case '/api/products':
          return json({ content: [{ id: 10, name: 'Regression Product', unit: 'piece' }], totalPages: 1, totalElements: 1 })
        case '/api/mrp/plan': {
          planCalls++
          if (rejectPlan) return json({ detail: 'Regression temporary failure' }, 409)
          const targetQuantity = Number(url.searchParams.get('quantity'))
          const available = stock - reserved + incoming
          return json({ productId: 10, targetQuantity, fgOnHand: stock, fgReserved: reserved, fgIncoming: incoming,
            available, toProduce: Math.max(0, targetQuantity - available), materials: [] })
        }
        case '/api/reports/stock':
          reportCalls++
          if (rejectReport) return json({ detail: 'Report temporary failure' }, 500)
          return json([{ id: 10, code: 'REG', name: `Snapshot ${stock}`, unit: 'piece', openingBalance: 0,
            inQuantity: stock, outQuantity: 0, adjustInQuantity: 0, adjustOutQuantity: 0, closingBalance: stock }])
        default:
          errors.push(`Unexpected API: ${url.pathname}`)
          return json({ detail: 'Unexpected regression API' }, 500)
      }
    })

    if (role === 'WAREHOUSE_MANAGER') {
      await page.goto(`${origin}/warehouse/planning`)
      await page.waitForLoadState('networkidle')
      await page.getByLabel('Sản phẩm mục tiêu', { exact: true }).click()
      await page.locator('.ant-select-item-option').filter({ hasText: 'Regression Product' }).click()
      const quantity = page.getByLabel('Số lượng muốn có sẵn', { exact: true })
      const calculate = page.getByRole('button', { name: /Tính toán MRP/ })
      const submit = async (amount, expectedStock) => {
        await quantity.fill(String(amount))
        await quantity.press('Tab')
        const before = planCalls
        const [response] = await Promise.all([
          page.waitForResponse((res) => new URL(res.url()).pathname === '/api/mrp/plan', { timeout: 10000 }),
          calculate.click({ timeout: 7000 }),
        ])
        assert.equal(response.status(), 200)
        await page.getByText(`Khả dụng dự kiến = Tồn thực (${expectedStock})`, { exact: false }).waitFor()
        assert.equal(planCalls, before + 1, 'Every explicit calculation must make one fresh API request')
      }
      await submit(10, 2)
      stock = 7 // Simulate a warehouse change made by another user.
      await submit(10, 7)
      console.log('PASS MRP refreshes identical inputs and displays the new stock')
      await submit(20, 7)
      stock = 4
      await submit(10, 4)
      console.log('PASS MRP refreshes when returning to previously cached inputs')
      rejectPlan = true
      const failed = page.waitForResponse((res) => new URL(res.url()).pathname === '/api/mrp/plan')
      await calculate.click()
      assert.equal((await failed).status(), 409)
      await page.getByText('Regression temporary failure', { exact: true }).waitFor()
      rejectPlan = false
      stock = 6
      await submit(10, 6)
      console.log('PASS MRP retries identical inputs after an API failure')
      stock = 0
      incoming = 100
      await submit(100, 0)
      await page.getByText('Cần chờ hoàn thành sản xuất để đáp ứng mục tiêu', { exact: true }).waitFor()
      await page.getByText('Không phát sinh nhu cầu NVL bổ sung', { exact: true }).waitFor()
      assert.equal(await page.getByText('Không cần hành động bổ sung', { exact: true }).count(), 0)
      console.log('PASS unfinished production shows projected stock and a waiting action')

      stock = 100
      await submit(100, 100)
      await page.getByText('Tồn khả dụng hiện tại đã đủ đáp ứng mục tiêu', { exact: true }).waitFor()
      await page.getByText('Không cần hành động bổ sung', { exact: true }).waitFor()

      reserved = 20
      await submit(100, 100)
      await page.getByText('Cần chờ hoàn thành sản xuất để đáp ứng mục tiêu', { exact: true }).waitFor()
      assert.ok((await page.getByRole('alert').innerText()).includes('còn thiếu 20'))
      console.log('PASS current availability accounts for reserved stock')
    }

    const area = role === 'ADMIN' ? 'admin' : 'warehouse'
    rejectReport = true
    await page.goto(`${origin}/${area}/reports`)
    await page.getByText('Report temporary failure', { exact: true }).waitFor()
    const showReport = page.getByRole('button', { name: /Xem báo cáo/ })
    await showReport.waitFor({ state: 'visible' })
    assert.equal(await page.getByRole('button', { name: /Xuất file CSV/ }).isDisabled(), true)
    rejectReport = false
    const initialRetry = page.waitForResponse((res) => new URL(res.url()).pathname === '/api/reports/stock')
    await page.getByRole('button', { name: 'Thử lại', exact: true }).click()
    assert.equal((await initialRetry).status(), 200)
    await page.getByRole('cell', { name: `Snapshot ${stock}`, exact: true }).waitFor()
    console.log(`PASS ${role} report keeps filters after an initial API failure`)
    const refreshReport = async () => {
      stock++
      const before = reportCalls
      const response = page.waitForResponse((res) => new URL(res.url()).pathname === '/api/reports/stock')
      await showReport.click()
      assert.equal((await response).status(), 200)
      await page.getByRole('cell', { name: `Snapshot ${stock}`, exact: true }).waitFor()
      assert.equal(reportCalls, before + 1)
    }
    await refreshReport()
    await refreshReport()
    console.log(`PASS ${role} report refreshes the same filters on every click`)
    await page.getByLabel('Kỳ báo cáo', { exact: true }).click()
    await page.locator('.ant-select-item-option').filter({ hasText: 'Theo năm' }).click()
    await refreshReport()
    await page.getByLabel('Kỳ báo cáo', { exact: true }).click()
    await page.locator('.ant-select-item-option').filter({ hasText: 'Theo tháng' }).click()
    await refreshReport()
    console.log(`PASS ${role} report refreshes when returning to a previously cached period`)
    rejectReport = true
    const failedReport = page.waitForResponse((res) => new URL(res.url()).pathname === '/api/reports/stock')
    await showReport.click()
    assert.equal((await failedReport).status(), 500)
    await page.getByText('Report temporary failure', { exact: true }).waitFor()
    await showReport.waitFor({ state: 'visible' })
    assert.equal(await page.getByRole('button', { name: /Xuất file CSV/ }).isDisabled(), true)
    assert.equal(await page.getByRole('cell', { name: `Snapshot ${stock}`, exact: true }).count(), 0)
    rejectReport = false
    const recovered = page.waitForResponse((res) => new URL(res.url()).pathname === '/api/reports/stock')
    await page.getByRole('button', { name: 'Thử lại', exact: true }).click()
    assert.equal((await recovered).status(), 200)
    await page.getByRole('cell', { name: `Snapshot ${stock}`, exact: true }).waitFor()
    console.log(`PASS ${role} report keeps filters on API errors and recovers without exporting stale data`)
    rejectReport = true
    const failedAgain = page.waitForResponse((res) => new URL(res.url()).pathname === '/api/reports/stock')
    await showReport.click()
    assert.equal((await failedAgain).status(), 500)
    await page.getByText('Report temporary failure', { exact: true }).waitFor()
    await page.getByLabel('Kỳ báo cáo', { exact: true }).click()
    await page.locator('.ant-select-item-option').filter({ hasText: 'Theo năm' }).click()
    rejectReport = false
    await refreshReport()
    console.log(`PASS ${role} report can change filters after an API failure`)
    await context.close()
  }
  assert.deepEqual(errors, [])
  console.log('PASS no browser exceptions or unexpected API calls')
} finally {
  await browser?.close()
  await server.close()
}

// Browser regression checks with mocked APIs; no application database is used.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import { mkdir, readFile } from 'node:fs/promises'
import path from 'node:path'
import { createServer } from 'vite'

const root = fileURLToPath(new URL('../../', import.meta.url))
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || path.join(root, 'backend/target/ui-test-tools/node_modules/playwright'))
const origin = 'http://127.0.0.1:15276'
const output = path.join(root, 'tmp/stock-report-fixes-qa')
const materials = Array.from({ length: 12 }, (_, index) => ({
  id: index + 1, code: index ? `M${index + 1}` : 'M,"1"',
  name: index ? `KG${index + 1}` : 'Ván "A", loại 1\nDòng 2', unit: 'kg',
  openingBalance: 0.001, inQuantity: index + 1.124, outQuantity: 0,
  adjustInQuantity: 0, adjustOutQuantity: 0, closingBalance: index + 1.125,
}))
materials.push({ id: 13, code: 'L13', name: 'LIT13', unit: 'lít', openingBalance: 3,
  inQuantity: 997, outQuantity: 0, adjustInQuantity: 0, adjustOutQuantity: 0, closingBalance: 1000 })
const products = [
  { id: 20, code: null, name: 'TP01', unit: 'cái', openingBalance: 1, closingBalance: 1 },
  { id: 21, code: null, name: 'TP02', unit: 'cái', openingBalance: 2, closingBalance: 2 },
  { id: 22, code: null, name: 'TP03', unit: 'bộ', openingBalance: 3, closingBalance: 3 },
]
const server = await createServer({
  root: path.join(root, 'frontend'), configFile: path.join(root, 'frontend/vite.config.js'),
  define: { 'import.meta.env.VITE_API_BASE_URL': JSON.stringify(origin) },
  server: { host: '127.0.0.1', port: 15276, strictPort: true },
})
let browser
const errors = []
try {
  await mkdir(output, { recursive: true })
  await server.listen()
  browser = await chromium.launch({ headless: true })
  for (const role of ['ADMIN', 'WAREHOUSE_MANAGER']) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1100 } })
    await context.addInitScript(() => sessionStorage.setItem('mrp_token', 'regression-only'))
    const page = await context.newPage()
    page.setDefaultTimeout(10000)
    page.on('pageerror', (error) => errors.push(error.message))
    let reportCalls = 0, emptyReport = false
    await page.route((url) => url.pathname.startsWith('/api/'), async (route) => {
      const url = new URL(route.request().url())
      if (url.pathname === '/api/auth/me') {
        return route.fulfill({ json: { id: 1, profileId: 1, username: 'regression', fullName: 'Regression', role } })
      }
      if (url.pathname === '/api/reports/stock') {
        reportCalls++
        return route.fulfill({ json: emptyReport ? [] : url.searchParams.get('kind') === 'PRODUCT' ? products : materials })
      }
      errors.push(`Unexpected API: ${url.pathname}`)
      return route.fulfill({ status: 500, json: { detail: 'Unexpected API' } })
    })
    const area = role === 'ADMIN' ? 'admin' : 'warehouse'
    await page.goto(`${origin}/${area}/reports`)
    await page.getByRole('cell', { name: 'KG10', exact: true }).waitFor()
    const detail = page.locator('.stock-report-detail')
    assert.equal(await detail.getByRole('columnheader', { name: 'Mã', exact: true }).count(), 1)
    assert.equal(await detail.getByRole('columnheader').count(), 10)
    const opening = page.locator('.stock-report-summary')
    assert.deepEqual(await opening.locator('td[data-field="openingBalance"]').allTextContents(), ['0,012', '3'])
    assert.deepEqual(await opening.locator('tbody th').allTextContents(), ['kg', 'lít'])
    const footer = page.locator('.ant-table-summary')
    assert.equal(await footer.locator('tr').count(), 2)
    assert.match(await footer.innerText(), /Tổng \(kg\)/)
    assert.match(await footer.innerText(), /0,012/)
    assert.match(await footer.innerText(), /Tổng \(lít\)/)
    assert.equal(await page.getByText('Tổng cộng toàn kho:', { exact: true }).count(), 0)
    console.log(`PASS ${role} groups totals by unit across all pages and retains material thousandths`)
    await page.screenshot({ path: path.join(output, `${area}-initial.png`), fullPage: true })

    const ticks = page.locator('.recharts-xAxis-tick-labels .recharts-cartesian-axis-tick-value')
    await ticks.first().waitFor()
    assert.deepEqual(await ticks.allTextContents(), ['KG12', 'KG11', 'KG10', 'KG9', 'KG8', 'KG7', 'KG6', 'KG5', 'KG4', 'KG3'])
    await page.getByLabel('Đơn vị biểu đồ', { exact: true }).click()
    await page.locator('.ant-select-item-option').filter({ hasText: 'lít' }).click()
    await page.getByText('Top 10 theo tồn cuối kỳ (lít)', { exact: true }).waitFor()
    assert.deepEqual(await ticks.allTextContents(), ['LIT13'])
    console.log(`PASS ${role} ranks all report rows by closing stock within the selected unit`)

    const beforeDraft = reportCalls
    await page.getByText('Thành phẩm', { exact: true }).click()
    await page.getByText('Bảng số liệu chi tiết – Nguyên vật liệu', { exact: true }).waitFor()
    assert.equal(await detail.getByRole('columnheader', { name: 'Mã', exact: true }).count(), 1)
    assert.match(await opening.innerText(), /0,012/)
    assert.equal(reportCalls, beforeDraft)
    const materialDownload = page.waitForEvent('download')
    await page.getByRole('button', { name: /Xuất file CSV/ }).click()
    const materialFile = await materialDownload
    assert.match(materialFile.suggestedFilename(), /MATERIAL/)
    const materialPath = path.join(output, `${area}-material.csv`)
    await materialFile.saveAs(materialPath)
    const csv = await readFile(materialPath, 'utf8')
    assert.ok(csv.startsWith('\uFEFF'))
    assert.ok(csv.includes('1,"M,""1""","Ván ""A"", loại 1\nDòng 2",kg,0.001,1.124,0,0,0,1.125'))
    await page.screenshot({ path: path.join(output, `${area}-material.png`), fullPage: true })
    console.log(`PASS ${role} draft filters keep material labels, precision and CSV; CSV escapes quotes, commas and newlines`)

    const applied = page.waitForResponse((response) => new URL(response.url()).searchParams.get('kind') === 'PRODUCT')
    await page.getByRole('button', { name: /Xem báo cáo/ }).click()
    assert.equal((await applied).status(), 200)
    await page.getByRole('cell', { name: 'TP01', exact: true }).waitFor()
    await page.getByText('Bảng số liệu chi tiết – Thành phẩm', { exact: true }).waitFor()
    assert.equal(await detail.getByRole('columnheader', { name: 'Mã', exact: true }).count(), 0)
    assert.equal(await detail.getByRole('columnheader').count(), 9)
    assert.equal(await footer.locator('tr').first().locator('td').first().getAttribute('colspan'), '3')
    await page.getByText('Top 10 theo tồn cuối kỳ (bộ)', { exact: true }).waitFor()
    assert.deepEqual(await opening.locator('td[data-field="openingBalance"]').allTextContents(), ['3', '3'])
    assert.deepEqual(await opening.locator('tbody th').allTextContents(), ['bộ', 'cái'])
    const productDownload = page.waitForEvent('download')
    await page.getByRole('button', { name: /Xuất file CSV/ }).click()
    const productFile = await productDownload
    assert.match(productFile.suggestedFilename(), /PRODUCT/)
    const productPath = path.join(output, `${area}-product.csv`)
    await productFile.saveAs(productPath)
    const productCsv = await readFile(productPath, 'utf8')
    assert.ok(productCsv.startsWith('\uFEFFID,Ten,DVT,'))
    assert.ok(productCsv.includes('20,TP01,cái,1,0,0,0,0,1'))
    await page.screenshot({ path: path.join(output, `${area}-product.png`), fullPage: true })
    await page.locator('.collapse-btn').click()
    await page.setViewportSize({ width: 390, height: 844 })
    await page.screenshot({ path: path.join(output, `${area}-mobile.png`), fullPage: true })
    console.log(`PASS ${role} applied product filters use integer quantities and keep units separate`)
    await page.setViewportSize({ width: 1440, height: 1100 })
    emptyReport = true
    const emptyResponse = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/reports/stock')
    await page.getByRole('button', { name: /Xem báo cáo/ }).click()
    assert.equal((await emptyResponse).status(), 200)
    await page.getByText('Không có biến động nào trong kỳ được chọn', { exact: true }).waitFor()
    assert.equal(await page.locator('.ant-statistic-content').count(), 0)
    assert.equal(await page.getByRole('button', { name: /Xuất file CSV/ }).isDisabled(), true)
    assert.equal(await page.locator('.recharts-wrapper').count(), 0)
    console.log(`PASS ${role} empty reports disable CSV and show no numeric totals or chart`)
    await context.close()
  }
  const layoutContext = await browser.newContext({ viewport: { width: 1920, height: 960 } })
  await layoutContext.addInitScript(() => sessionStorage.setItem('mrp_token', 'layout-regression'))
  const layoutPage = await layoutContext.newPage()
  layoutPage.on('pageerror', (error) => errors.push(error.message))
  const sixUnits = ['bộ', 'cái', 'kg/m3', 'lít', 'm3', 'tấm'].map((unit, index) => ({
    id: index + 1, code: `NVL${index + 1}`, name: `Nguyên vật liệu ${index + 1}`, unit,
    openingBalance: 0, inQuantity: 10, outQuantity: 100, adjustInQuantity: 1000, adjustOutQuantity: 0, closingBalance: 910,
  }))
  await layoutPage.route((url) => url.pathname.startsWith('/api/'), (route) => route.fulfill({
    json: new URL(route.request().url()).pathname === '/api/auth/me'
      ? { id: 1, profileId: 1, username: 'layout', fullName: 'Quản trị viên', role: 'ADMIN' }
      : sixUnits,
  }))
  await layoutPage.goto(`${origin}/admin/reports`)
  await layoutPage.locator('.stock-report-summary tbody tr').nth(5).waitFor()
  await layoutPage.locator('.recharts-wrapper').waitFor()
  for (const width of [1920, 1366]) {
    await layoutPage.setViewportSize({ width, height: 960 })
    const summaryBox = await layoutPage.locator('.stock-report-summary').boundingBox()
    const chartBox = await layoutPage.locator('.stock-report-chart').boundingBox()
    assert.ok(summaryBox.height < 240, `Compact six-unit summary height ${summaryBox.height}`)
    assert.ok(chartBox.y < 560, `Chart begins at ${chartBox.y} for width ${width}`)
    assert.ok(chartBox.y + chartBox.height < 960, 'Complete chart is visible in the first viewport')
    await layoutPage.screenshot({ path: path.join(output, `six-units-${width}.png`), fullPage: true })
  }
  await layoutPage.locator('.collapse-btn').click()
  await layoutPage.setViewportSize({ width: 390, height: 844 })
  await layoutPage.screenshot({ path: path.join(output, 'six-units-mobile.png'), fullPage: true })
  assert.ok(await layoutPage.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    'Small screens keep horizontal scrolling inside the summary and detail tables')
  await layoutContext.close()
  console.log('PASS six-unit summary stays compact and keeps the chart visible on desktop; mobile tables scroll locally')
  assert.deepEqual(errors, [])
  console.log('PASS no browser exceptions or unexpected API requests')
} finally {
  await browser?.close()
  await server.close()
}

// Regression checks against the isolated UI QA fixture. Never point at production.
// Install Playwright separately; supply MRP_QA_PASSWORD and optionally PLAYWRIGHT_PACKAGE.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = fileURLToPath(new URL('../../', import.meta.url))
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_PACKAGE || path.join(root, 'backend/target/ui-test-tools/node_modules/playwright'))
const base = process.env.MRP_UI_URL || 'http://127.0.0.1:15173'
const password = process.env.MRP_QA_PASSWORD
assert.ok(password, 'Set MRP_QA_PASSWORD for the isolated QA accounts')
const out = path.join(root, 'docs/warehouse-staff-ui-fix-evidence')
await mkdir(out, { recursive: true })
const browser = await chromium.launch({ headless: true })
const checks = [], pageErrors = [], network = []
const newPage = async () => {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  const page = await context.newPage()
  page.setDefaultTimeout(10000)
  page.on('pageerror', e => pageErrors.push(e.message))
  page.on('response', r => {
    if (new URL(r.url()).pathname.startsWith('/api/')) {
      network.push({ method: r.request().method(), path: new URL(r.url()).pathname, status: r.status() })
    }
  })
  return page
}
const go = async (page, route) => {
  await page.goto(base + route)
  await page.waitForLoadState('networkidle')
}
const login = async (page, username, role) => {
  await go(page, '/login')
  await page.getByLabel('Tên đăng nhập', { exact: true }).fill(username)
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Đăng nhập vào hệ thống' }).click()
  await page.waitForURL(`**/${role}`)
}
const record = async (page, name, detail) => {
  await page.screenshot({ path: path.join(out, name + '.png'), fullPage: true, animations: 'disabled' })
  checks.push({ name, pass: true, detail, screenshot: name + '.png' })
  console.log('PASS', name)
}
const select = async (page, field, text) => {
  await field.click()
  await page.locator('.ant-select-dropdown:visible').getByText(text, { exact: false }).last().click()
}
const write = async (page, button, endpoint, status = 200) => {
  const response = page.waitForResponse(r => new URL(r.url()).pathname === endpoint && r.request().method() === 'POST')
  await button.click()
  const r = await response
  assert.equal(r.status(), status, await r.text())
  return r.json()
}

try {
  const warehouse = await newPage(), staff = await newPage()
  await login(warehouse, 'ui_warehouse', 'warehouse')
  await go(warehouse, '/warehouse/reports')
  const ticks = warehouse.locator('.recharts-cartesian-axis-tick-value')
  await ticks.filter({ hasText: 'UI QA Material' }).waitFor()
  await record(warehouse, '01-report-material-name', 'Trục X hiển thị tên NVL thay vì chỉ số 0')
  await warehouse.getByText('Thành phẩm', { exact: true }).click()
  await warehouse.getByRole('button', { name: /Xem báo cáo/ }).click()
  await ticks.filter({ hasText: 'UI QA Product' }).waitFor()
  await record(warehouse, '02-report-product-name', 'Trục X hiển thị tên thành phẩm')

  await go(warehouse, '/warehouse/inventory')
  for (const [kind, placeholder, fixture] of [
    ['material', 'Tìm mã / tên NVL', 'UI QA Material'],
    ['product', 'Tìm tên sản phẩm', 'UI QA Product'],
  ]) {
    if (kind === 'product') await warehouse.getByRole('tab', { name: 'Thành phẩm' }).click()
    const input = warehouse.getByPlaceholder(placeholder)
    await input.fill('NO_SUCH_UI_QA')
    await input.press('Enter')
    await warehouse.getByText('Không có dữ liệu', { exact: true }).waitFor()
    await warehouse.getByRole('button', { name: /Đặt lại/ }).click()
    await warehouse.getByRole('row').filter({ hasText: fixture }).waitFor()
    assert.equal(await input.inputValue(), '')
    await input.press('Enter')
    await warehouse.waitForLoadState('networkidle')
    await warehouse.getByRole('row').filter({ hasText: fixture }).waitFor()
    await record(warehouse, `03-reset-${kind}-search`, 'Đặt lại xóa ô nhập và filter; Enter sau đặt lại vẫn có dữ liệu')
  }

  await login(staff, 'ui_staff', 'staff')
  await go(staff, '/staff/fg-requests')
  await staff.getByRole('button', { name: /Lập đề nghị xuất bán mới/ }).click()
  const create = staff.getByRole('dialog')
  await select(staff, create.getByLabel('Sản phẩm', { exact: true }), 'UI QA Product')
  await create.getByLabel('Số lượng xuất', { exact: true }).fill('1')
  await create.getByLabel('Thông tin khách hàng / Mã đơn hàng / Ghi chú').fill('UI QA confirm regression')
  const fg = await write(staff, create.getByRole('button', { name: 'Gửi đề nghị (PENDING)', exact: true }), '/api/fg-issue-requests')
  await create.waitFor({ state: 'hidden' })
  assert.equal(fg.hasStockShortage, true, 'Fixture must have zero product stock')

  await go(warehouse, '/warehouse/fg-issues')
  const row = warehouse.getByRole('row').filter({ has: warehouse.getByRole('cell', { name: '#' + fg.id, exact: true }) })
  await row.getByRole('button', { name: /Duyệt$/ }).click()
  const dialog = warehouse.getByRole('dialog')
  const endpoint = `/api/fg-issue-requests/${fg.id}/approve`
  // Delay the real API request to test overlapping clicks without mocking its result.
  let approvalCalls = 0
  await warehouse.route('**' + endpoint, async route => {
    approvalCalls++
    await new Promise(resolve => setTimeout(resolve, 350))
    await route.continue()
  })
  const response = warehouse.waitForResponse(r => new URL(r.url()).pathname === endpoint)
  await dialog.getByRole('button', { name: 'Xác nhận', exact: true }).evaluate(button => { button.click(); button.click() })
  assert.equal((await response).status(), 409)
  await warehouse.getByText(/Tồn kho thành phẩm #.*không đủ để xuất kho/).waitFor()
  await dialog.getByRole('button', { name: 'Xác nhận', exact: true }).waitFor()
  await warehouse.waitForLoadState('networkidle')
  assert.equal(approvalCalls, 1)
  assert.deepEqual(pageErrors, [])
  await row.getByText('Chờ xử lý', { exact: true }).waitFor()
  await record(warehouse, '04-shortage-no-unhandled-error', 'HTTP 409; modal giữ mở, thông báo đúng, không pageerror, bấm hai lần chỉ gửi 1 POST')

  await write(warehouse, dialog.getByRole('button', { name: 'Xác nhận', exact: true }), endpoint, 409)
  await warehouse.waitForLoadState('networkidle')
  assert.equal(approvalCalls, 2)
  assert.deepEqual(pageErrors, [])
  await record(warehouse, '05-retry-after-shortage', 'Có thể thử lại sau lỗi, không bị khóa nút và không pageerror')
  await dialog.getByRole('button', { name: 'Hủy', exact: true }).click()
  await dialog.waitFor({ state: 'hidden' })
  await row.getByRole('button', { name: /Duyệt$/ }).click()
  await dialog.waitFor()
  await dialog.getByRole('button', { name: 'Hủy', exact: true }).click()
  await dialog.waitFor({ state: 'hidden' })
  await record(warehouse, '06-cancel-and-reopen', 'Hủy modal sau lỗi và mở lại được')

  // Add one product through a separate UI page, then retry the same real approval.
  const inventory = await newPage()
  await login(inventory, 'ui_warehouse', 'warehouse')
  await go(inventory, '/warehouse/inventory')
  await inventory.getByRole('button', { name: /Điều chỉnh tồn kho/ }).click()
  const adjust = inventory.getByRole('dialog')
  await adjust.getByText('Thành phẩm', { exact: true }).click()
  await select(inventory, adjust.getByLabel('Chọn thành phẩm', { exact: true }), 'UI QA Product')
  await adjust.getByLabel('Số lượng điều chỉnh', { exact: true }).fill('1')
  await adjust.getByLabel('Lý do / Ghi chú (bắt buộc)').fill('UI QA confirm retry fixture')
  await write(inventory, adjust.getByRole('button', { name: 'Lưu điều chỉnh', exact: true }), '/api/stock-adjustments')
  await adjust.waitFor({ state: 'hidden' })
  await row.getByRole('button', { name: /Duyệt$/ }).click()
  await write(warehouse, dialog.getByRole('button', { name: 'Xác nhận', exact: true }), endpoint)
  await dialog.waitFor({ state: 'hidden' })
  await row.getByText('Đã duyệt', { exact: true }).waitFor()
  await warehouse.waitForLoadState('networkidle')
  assert.deepEqual(pageErrors, [])
  await record(warehouse, '07-confirm-success-closes-modal', 'Duyệt thật khi đủ tồn: modal đóng, trạng thái Đã duyệt, không pageerror')
} catch (error) {
  checks.push({ name: 'runner-failure', pass: false, error: error.stack })
  process.exitCode = 1
  console.error(error)
} finally {
  await writeFile(path.join(out, 'results.json'), JSON.stringify({ date: new Date().toISOString(), browser: browser.version(), checks, pageErrors, network }, null, 2))
  await browser.close()
}

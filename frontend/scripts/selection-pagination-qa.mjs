// Actual frontend components with paginated mock APIs; no application database writes.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import { mkdir } from 'node:fs/promises'
import { createServer } from 'vite'

const root = fileURLToPath(new URL('../../', import.meta.url))
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || path.join(root, 'backend/target/ui-test-tools/node_modules/playwright'))
const origin = 'http://127.0.0.1:15277'
const evidence = path.join(root, 'tmp/selection-pagination-qa')
const categories = Array.from({ length: 205 }, (_, i) => ({ id: i + 1, name: `Danh mục ${String(i + 1).padStart(3, '0')}` }))
const suppliers = categories.map(({ id }) => ({ id, name: `NCC ${String(id).padStart(3, '0')}`, phone: '0123456789' }))
const prs = [
  ...Array.from({ length: 120 }, (_, i) => ({ id: 1000 + i, status: i % 2 ? 'COMPLETED' : 'CANCELLED', productName: 'PR đã đóng' })),
  ...['PENDING', 'IN_PROGRESS'].flatMap((status, index) => Array.from({ length: 105 }, (_, i) => ({
    id: index * 200 + i + 1, status, productName: `Sản phẩm ${status} ${i + 1}`, productUnit: 'cái', quantity: 10, completedQuantity: 0,
  }))),
].sort((a, b) => b.id - a.id)
const material = { id: 1, code: 'NVL01', name: 'NVL kiểm tra', unit: 'kg', standardCost: 10, active: true }
const receipt = { id: 1, supplierId: 205, supplierName: 'NCC 205', status: 'PENDING', items: [{ materialId: 1, quantity: 1, unitPrice: 10 }] }
const product = { id: 1, name: 'SP danh mục cuối', categoryId: 205, categoryName: 'Danh mục 205', unit: 'cái', active: true, sellingPrice: 10 }
const server = await createServer({
  root: path.join(root, 'frontend'), configFile: path.join(root, 'frontend/vite.config.js'),
  define: { 'import.meta.env.VITE_API_BASE_URL': JSON.stringify(origin) },
  server: { host: '127.0.0.1', port: 15277, strictPort: true },
})
let browser
const errors = []

async function screen(role, routePath, failure = null) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1100 } })
  await context.addInitScript(() => sessionStorage.setItem('mrp_token', 'pagination-regression-only'))
  const page = await context.newPage()
  page.setDefaultTimeout(12000)
  page.on('pageerror', (error) => errors.push(error.message))
  const calls = []
  const writes = []
  const state = { fail: !!failure }
  await page.route((url) => url.pathname.startsWith('/api/'), async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    calls.push(url)
    const paged = (items) => {
      const pageNumber = Number(url.searchParams.get('page') || 0)
      const size = Number(url.searchParams.get('size') || 20)
      return route.fulfill({ json: {
        content: items.slice(pageNumber * size, (pageNumber + 1) * size),
        totalPages: Math.ceil(items.length / size), totalElements: items.length, number: pageNumber, size,
      } })
    }
    if (state.fail && failure?.(url)) return route.fulfill({ status: 503, json: { detail: 'Không tải được trang lựa chọn tiếp theo' } })
    if (request.method() !== 'GET') {
      writes.push({ path: url.pathname, body: request.postDataJSON() })
      return route.fulfill({ json: { id: 77, hasStockShortage: false } })
    }
    switch (url.pathname) {
      case '/api/auth/me': return route.fulfill({ json: { id: 1, profileId: 1, username: 'pagination', fullName: 'Kiểm tra phân trang', role } })
      case '/api/categories': return paged(categories)
      case '/api/suppliers': return paged(suppliers)
      case '/api/products': return paged([product])
      case '/api/materials': return paged([material])
      case '/api/inventories/materials': return paged([])
      case '/api/inventories/products': return paged([{ ...product, productId: 1, quantity: 5 }])
      case '/api/purchase-receipts': return paged([receipt])
      case '/api/purchase-receipts/1': return route.fulfill({ json: receipt })
      case '/api/production-requests': {
        const status = url.searchParams.get('status')
        return paged(status ? prs.filter((pr) => pr.status === status) : prs)
      }
      case '/api/material-issue-requests':
      case '/api/productions': return paged([])
      default:
        if (/^\/api\/production-requests\/\d+\/requirements$/.test(url.pathname)) {
          return route.fulfill({ json: { productName: 'Sản phẩm kiểm tra', quantity: 10, materials: [{
            materialId: 1, materialCode: material.code, materialName: material.name, unit: 'kg', remainingAllowed: 10, onHand: 20,
          }] } })
        }
        errors.push(`Unexpected API: ${url.pathname}`)
        return route.fulfill({ status: 500, json: { detail: 'Unexpected API' } })
    }
  })
  await page.goto(`${origin}${routePath}`)
  await page.waitForLoadState('networkidle')
  return { context, page, calls, writes, state }
}

async function choose(page, control, text) {
  await control.click()
  await control.fill(text)
  const listId = await control.getAttribute('aria-controls')
  const popup = page.locator('.ant-select-dropdown').filter({ has: page.locator(`[id="${listId}"]`) })
  await popup.locator('.ant-select-item-option').filter({ hasText: text }).click()
}

function assertAllPages(calls, apiPath, expectedPages) {
  const pages = calls.filter((url) => url.pathname === apiPath).map((url) => Number(url.searchParams.get('page')))
  for (let page = 0; page < expectedPages; page++) assert.ok(pages.includes(page), `Missing ${apiPath} page ${page}`)
}

function assertOpenPrPages(calls) {
  const requests = calls.filter((url) => url.pathname === '/api/production-requests')
  assert.ok(requests.length > 0)
  assert.ok(requests.every((url) => ['PENDING', 'IN_PROGRESS'].includes(url.searchParams.get('status'))), 'PR options must filter on the server')
  for (const status of ['PENDING', 'IN_PROGRESS']) {
    assertAllPages(requests.filter((url) => url.searchParams.get('status') === status), '/api/production-requests', 2)
  }
}

try {
  await mkdir(evidence, { recursive: true })
  await server.listen()
  browser = await chromium.launch({ headless: true })
  {
    const { page, context, calls } = await screen('ADMIN', '/admin/products')
    await page.getByRole('cell', { name: 'Danh mục 205', exact: true }).waitFor()
    assertAllPages(calls, '/api/categories', 3)
    await page.getByRole('button', { name: /Thêm mới/ }).click()
    const dialog = page.getByRole('dialog')
    await choose(page, dialog.getByLabel('Danh mục', { exact: true }), 'Danh mục 205')
    await dialog.getByRole('button', { name: 'Hủy', exact: true }).click()
    await dialog.waitFor({ state: 'hidden' })
    await choose(page, page.getByRole('combobox').first(), 'Danh mục 205')
    await page.waitForLoadState('networkidle')
    assert.ok(calls.some((url) => url.pathname === '/api/products' && url.searchParams.get('categoryId') === '205'))
    console.log('PASS Catalog loads 205 categories, resolves names and selects/filters the last category')
    await context.close()
  }
  {
    const { page, context, calls } = await screen('WAREHOUSE_MANAGER', '/warehouse/receipts')
    assertAllPages(calls, '/api/suppliers', 3)
    await choose(page, page.getByRole('combobox').first(), 'NCC 205')
    await page.waitForLoadState('networkidle')
    assert.ok(calls.some((url) => url.pathname === '/api/purchase-receipts' && url.searchParams.get('supplierId') === '205'))
    await page.getByRole('button', { name: 'Tạo phiếu nhập mới' }).click()
    await choose(page, page.getByRole('dialog').getByLabel('Nhà cung cấp', { exact: true }), 'NCC 205')
    await page.getByRole('dialog').getByRole('button', { name: 'Hủy', exact: true }).click()
    await page.getByRole('button', { name: /Sửa/ }).click()
    await page.getByRole('dialog').getByText('NCC 205', { exact: true }).waitFor()
    console.log('PASS Purchase receipts filter/create/edit can use supplier 205')
    await context.close()
  }
  {
    const { page, context, calls } = await screen('WAREHOUSE_MANAGER', '/warehouse/inventory')
    const loadedCategories = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return url.pathname === '/api/categories' && url.searchParams.get('page') === '2'
    })
    await page.getByRole('tab', { name: 'Thành phẩm', exact: true }).click()
    await loadedCategories
    await page.waitForLoadState('networkidle')
    assertAllPages(calls, '/api/categories', 3)
    await choose(page, page.getByRole('tabpanel', { name: 'Thành phẩm' }).getByRole('combobox').first(), 'Danh mục 205')
    await page.waitForLoadState('networkidle')
    assert.ok(calls.some((url) => url.pathname === '/api/inventories/products' && url.searchParams.get('categoryId') === '205'))
    console.log('PASS Inventory can filter by category 205')
    await context.close()
  }
  for (const kind of ['material-requests', 'productions']) {
    const { page, context, calls, writes } = await screen('STAFF', `/staff/${kind}`)
    assertOpenPrPages(calls)
    const materialRequest = kind === 'material-requests'
    await page.getByRole('button', { name: materialRequest ? /Lập đề nghị xuất NVL mới/ : /Bắt đầu đợt sản xuất mới/ }).click()
    const dialog = page.getByRole('dialog')
    const prId = materialRequest ? 1 : 201
    const control = dialog.getByLabel(materialRequest ? 'Chọn yêu cầu sản xuất' : 'Yêu cầu sản xuất', { exact: true })
    await control.fill('PR đã đóng')
    assert.equal(await page.locator('.ant-select-dropdown:visible .ant-select-item-option').count(), 0)
    await choose(page, control, `PR #${prId} –`)
    if (materialRequest) {
      await choose(page, dialog.getByLabel('Nguyên vật liệu', { exact: true }), material.name)
    } else {
      await dialog.getByLabel('Số lượng sản xuất đợt này', { exact: true }).fill('1')
    }
    await page.screenshot({ path: path.join(evidence, `${kind}-old-open-pr.png`), fullPage: true })
    await dialog.getByRole('button', { name: materialRequest ? 'Gửi đề nghị (PENDING)' : 'Bắt đầu sản xuất', exact: true }).click()
    await dialog.waitFor({ state: 'hidden' })
    const created = writes.find((item) => item.path === (materialRequest ? '/api/material-issue-requests' : '/api/productions'))
    assert.equal(created?.body.productionRequestId, prId)
    console.log(`PASS ${kind}: both open statuses load all pages; closed PRs excluded; old PR ${prId} selected and submitted`)
    await context.close()
  }
  for (const kind of ['categories', 'open-pr']) {
    const isCategories = kind === 'categories'
    const { page, context, calls, state } = await screen(isCategories ? 'ADMIN' : 'STAFF', isCategories ? '/admin/products' : '/staff/productions',
      (url) => url.pathname === (isCategories ? '/api/categories' : '/api/production-requests')
        && url.searchParams.get('page') === '1' && (isCategories || url.searchParams.get('status') === 'IN_PROGRESS'))
    await page.getByText('Không tải được trang lựa chọn tiếp theo', { exact: true }).waitFor()
    await page.getByRole('button', { name: isCategories ? /Thêm mới/ : /Bắt đầu đợt sản xuất mới/ }).click()
    const dialog = page.getByRole('dialog')
    const control = dialog.getByLabel(isCategories ? 'Danh mục' : 'Yêu cầu sản xuất', { exact: true })
    await control.fill(isCategories ? 'Danh mục 001' : 'PR #105')
    assert.equal(await page.locator('.ant-select-dropdown:visible .ant-select-item-option').count(), 0, 'A failed later page must not expose a partial list')
    await page.keyboard.press('Escape')
    await dialog.getByRole('button', { name: 'Hủy', exact: true }).click()
    state.fail = false
    const recovered = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return response.ok() && url.pathname === (isCategories ? '/api/categories' : '/api/production-requests')
        && url.searchParams.get('page') === (isCategories ? '2' : '1')
        && (isCategories || url.searchParams.get('status') === 'IN_PROGRESS')
    })
    await page.getByRole('button', { name: 'Thử lại', exact: true }).click()
    await recovered
    await page.getByText('Không tải được trang lựa chọn tiếp theo', { exact: true }).waitFor({ state: 'hidden' })
    await page.getByRole('button', { name: isCategories ? /Thêm mới/ : /Bắt đầu đợt sản xuất mới/ }).click()
    await choose(page, control, isCategories ? 'Danh mục 205' : 'PR #201 –')
    if (isCategories) assertAllPages(calls, '/api/categories', 3)
    else assertOpenPrPages(calls)
    console.log(`PASS ${kind}: later-page failure reports an error, exposes no partial options and retry recovers`)
    await context.close()
  }
  assert.deepEqual(errors, [])
  console.log('PASS no browser exceptions or unexpected API requests')
} finally {
  await browser?.close()
  await server.close()
}

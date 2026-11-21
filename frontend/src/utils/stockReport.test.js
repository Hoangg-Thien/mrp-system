import test from 'node:test'
import assert from 'node:assert/strict'
import { escapeCsvCell, groupStockTotals, stockReportCsv, stockReportDigits, topStockItems } from './stockReport.js'

test('totals keep different units separate and sum every quantity field', () => {
  const totals = groupStockTotals([
    { unit: 'kg', openingBalance: 5, inQuantity: 1, outQuantity: 2, adjustInQuantity: 0.125, adjustOutQuantity: 0.025, closingBalance: 4.1 },
    { unit: ' kg ', openingBalance: 2, inQuantity: 3, outQuantity: 1, adjustInQuantity: 0.2, adjustOutQuantity: 0.1, closingBalance: 4.1 },
    { unit: 'lít', openingBalance: 3, closingBalance: 3 },
  ], 'MATERIAL')
  assert.deepEqual(totals, [
    { unit: 'kg', openingBalance: 7, inQuantity: 4, outQuantity: 3, adjustInQuantity: 0.325, adjustOutQuantity: 0.125, closingBalance: 8.2 },
    { unit: 'lít', openingBalance: 3, inQuantity: 0, outQuantity: 0, adjustInQuantity: 0, adjustOutQuantity: 0, closingBalance: 3 },
  ])
})

test('material totals retain thousandths and avoid floating point accumulation', () => {
  const [totals] = groupStockTotals([
    { unit: 'kg', openingBalance: 0.001, closingBalance: 0.1 },
    { unit: 'kg', openingBalance: 0.002, closingBalance: 0.2 },
  ], 'MATERIAL')
  assert.equal(totals.openingBalance, 0.003)
  assert.equal(totals.closingBalance, 0.3)
  assert.equal(stockReportDigits('MATERIAL'), 3)
  assert.equal(stockReportDigits('PRODUCT'), 0)
  assert.deepEqual(groupStockTotals([], 'MATERIAL'), [])
})

test('top ten ranks by closing stock within one unit without modifying report rows', () => {
  const rows = Array.from({ length: 12 }, (_, index) => ({ id: index + 1, unit: 'kg', closingBalance: index + 1 }))
  rows.push({ id: 13, unit: 'lít', closingBalance: 1000 })
  const original = structuredClone(rows)
  assert.deepEqual(topStockItems(rows, 'kg').map((item) => item.id), [12, 11, 10, 9, 8, 7, 6, 5, 4, 3])
  assert.deepEqual(topStockItems(rows, 'lít').map((item) => item.id), [13])
  assert.deepEqual(rows, original)
})

test('ties are ordered by ID and an empty unit selection produces no chart rows', () => {
  const rows = [{ id: 9, unit: 'cái', closingBalance: 4 }, { id: 2, unit: 'cái', closingBalance: 4 }]
  assert.deepEqual(topStockItems(rows, 'cái').map((item) => item.id), [2, 9])
  assert.deepEqual(topStockItems(rows, 'kg'), [])
})

test('CSV escaping covers commas, quotes, both newline types, Unicode, zero and null', () => {
  for (const [input, expected] of [
    ['Ván "A"', '"Ván ""A"""'], ['Gỗ, loại 1', '"Gỗ, loại 1"'],
    ['Dòng 1\nDòng 2', '"Dòng 1\nDòng 2"'], ['Dòng 1\rDòng 2', '"Dòng 1\rDòng 2"'],
    ['Gỗ sồi', 'Gỗ sồi'], [0, '0'], [null, ''],
  ]) assert.equal(escapeCsvCell(input), expected)
})

test('export preserves complete special-character cells and consistent material precision', () => {
  const csv = stockReportCsv([{ id: 1, code: 'M,"1"', name: 'Ván "A", loại 1\nDòng 2', unit: 'kg',
    openingBalance: 0.001, inQuantity: 0.002, outQuantity: 0, adjustInQuantity: 0, adjustOutQuantity: 0, closingBalance: 0.003 }], 'MATERIAL')
  assert.equal(csv, '\uFEFFID,Ma,Ten,DVT,Ton_dau_ky,Nhap_trong_ky,Xuat_trong_ky,Dieu_chinh_tang,Dieu_chinh_giam,Ton_cuoi_ky\r\n'
    + '1,"M,""1""","Ván ""A"", loại 1\nDòng 2",kg,0.001,0.002,0,0,0,0.003')
  assert.equal(stockReportCsv([{ id: 2, name: 'Bàn', unit: 'cái', closingBalance: 3 }], 'PRODUCT'),
    '\uFEFFID,Ten,DVT,Ton_dau_ky,Nhap_trong_ky,Xuat_trong_ky,Dieu_chinh_tang,Dieu_chinh_giam,Ton_cuoi_ky\r\n'
    + '2,Bàn,cái,0,0,0,0,0,3')
})

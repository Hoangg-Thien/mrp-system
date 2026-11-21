export const stockQuantityFields = [
  'openingBalance', 'inQuantity', 'outQuantity',
  'adjustInQuantity', 'adjustOutQuantity', 'closingBalance',
]

export const stockReportDigits = (kind) => kind === 'PRODUCT' ? 0 : 3
export const stockUnit = (unit) => String(unit ?? '').trim() || 'Chưa xác định'
const number = (value) => Number.isFinite(Number(value)) ? Number(value) : 0

export function groupStockTotals(items, kind) {
  const scale = 10 ** stockReportDigits(kind)
  const groups = new Map()
  for (const item of items) {
    const unit = stockUnit(item.unit)
    if (!groups.has(unit)) {
      groups.set(unit, Object.fromEntries(stockQuantityFields.map((field) => [field, 0])))
    }
    const totals = groups.get(unit)
    for (const field of stockQuantityFields) {
      // Sum at the displayed precision, avoiding binary fractions such as 0.1 + 0.2.
      totals[field] += Math.round(number(item[field]) * scale)
    }
  }
  return [...groups].map(([unit, totals]) => ({
    unit,
    ...Object.fromEntries(stockQuantityFields.map((field) => [field, totals[field] / scale])),
  })).sort((a, b) => a.unit.localeCompare(b.unit, 'vi'))
}

export function topStockItems(items, unit, limit = 10) {
  return items.filter((item) => stockUnit(item.unit) === unit)
    .sort((a, b) => number(b.closingBalance) - number(a.closingBalance) || number(a.id) - number(b.id))
    .slice(0, limit)
}

export function escapeCsvCell(value) {
  const text = String(value ?? '')
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

export function stockReportCsv(items, kind) {
  const includeCode = kind === 'MATERIAL'
  const headers = ['ID', ...(includeCode ? ['Ma'] : []), 'Ten', 'DVT', 'Ton_dau_ky', 'Nhap_trong_ky', 'Xuat_trong_ky', 'Dieu_chinh_tang', 'Dieu_chinh_giam', 'Ton_cuoi_ky']
  const digits = stockReportDigits(kind)
  const rows = items.map((item) => [
    item.id, ...(includeCode ? [item.code ?? ''] : []), item.name ?? '', item.unit ?? '',
    ...stockQuantityFields.map((field) => Number(number(item[field]).toFixed(digits))),
  ])
  return '\uFEFF' + [headers, ...rows].map((row) => row.map(escapeCsvCell).join(',')).join('\r\n')
}

export const money = (value) => (value == null ? '—' : new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 }).format(value))

export const quantity = (value, digits = 3) => (
  value == null ? '—' : new Intl.NumberFormat('vi-VN', { maximumFractionDigits: digits }).format(value)
)

// Preserve partial input while typing and hide trailing decimal zeros on blur.
export const numberInputFormatter = (value, { userTyping, input }) => (
  userTyping ? input : String(value ?? '').replace(/(\.\d*?[1-9])0+$|\.0+$/, '$1')
)

export const dateTime = (value) => (
  value ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) : '—'
)

export const dateOnly = (value) => (
  value ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short' }).format(new Date(value)) : '—'
)

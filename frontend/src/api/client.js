// @ts-check
/**
 * @typedef {Object} RequestOptions
 * @property {'GET'|'POST'|'PUT'|'PATCH'|'DELETE'} [method]
 * @property {unknown} [body] Object sẽ serialize thành JSON; bỏ qua khi request không có body.
 * @property {Record<string, string|number|boolean|null|undefined>} [query] Giá trị rỗng/null/undefined tự động bị loại.
 * @property {AbortSignal} [signal] Dùng để hủy request khi component unmount (TanStack Query).
 */

const tokenKey = 'mrp_token'
export const session = {
  get: () => localStorage.getItem(tokenKey) || sessionStorage.getItem(tokenKey),
  set: (token, remember = false) => {
    if (remember) {
      localStorage.setItem(tokenKey, token)
      sessionStorage.removeItem(tokenKey)
    } else {
      sessionStorage.setItem(tokenKey, token)
      localStorage.removeItem(tokenKey)
    }
  },
  clear: () => {
    localStorage.removeItem(tokenKey)
    sessionStorage.removeItem(tokenKey)
  },
}

/**
 * All HTTP access is centralized here; no business rules are calculated locally.
 * @param {string} path Đường dẫn API, ví dụ `/api/users`.
 * @param {RequestOptions} [options]
 * @returns {Promise<any>} JSON backend trả về; `null` với HTTP 204.
 */
export async function request(path, { method = 'GET', body, query, signal } = {}) {
  const base = /** @type {string | undefined} */ (import.meta.env.VITE_API_BASE_URL)
  if (!base) throw new Error('Chưa cấu hình VITE_API_BASE_URL trong .env.')
  const params = new URLSearchParams()
  Object.entries(query || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value))
  })
  const token = session.get()
  const response = await fetch(`${base.replace(/\/$/, '')}${path}${params.size ? `?${params}` : ''}`, {
    method, signal,
    headers: { Accept: 'application/json', ...(body !== undefined && { 'Content-Type': 'application/json' }), ...(token && { Authorization: `Bearer ${token}` }) },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (response.status === 401 && token) {
    session.clear()
    window.dispatchEvent(new Event('mrp:logout'))
  }
  if (response.status === 204) return null
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    /** @type {Record<number, string>} */
    const fallback = { 401: 'Tên đăng nhập/mật khẩu sai hoặc phiên đã hết hạn.', 403: 'Bạn không có quyền thực hiện thao tác này.', 409: 'Dữ liệu trùng hoặc đang được sử dụng.' }
    throw new Error([data?.detail || fallback[response.status] || `Yêu cầu thất bại (${response.status})`, ...Object.entries(data?.errors || {}).map(([field, text]) => `${field}: ${text}`)].join('\n'))
  }
  return data
}

/** Trợ lý gọi API ngắn gọn; các trang chỉ dùng nhóm hàm này, không gọi fetch trực tiếp. */
export const api = {
  get: (path, query, signal) => request(path, { query, signal }),
  post: (path, body) => request(path, { method: 'POST', body }),
  put: (path, body) => request(path, { method: 'PUT', body }),
  del: (path) => request(path, { method: 'DELETE' }),
}

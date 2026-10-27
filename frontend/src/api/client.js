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
 * Thống nhất thông báo lỗi sang tiếng Việt nghiệp vụ chuẩn
 * @param {string} text - Chuỗi lỗi thô từ backend hoặc mạng
 * @returns {string}
 */
export function translateErrorMessage(text) {
  if (!text) return 'Đã xảy ra lỗi không xác định. Vui lòng thử lại.'
  const raw = String(text).trim()

  // Lỗi mạng hoặc kết nối server
  if (/failed to fetch|networkerror|network request failed/i.test(raw)) {
    return 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra lại dịch vụ backend hoặc kết nối mạng.'
  }

  // Lỗi xác thực & phân quyền
  if (/bad credentials/i.test(raw)) {
    return 'Tên đăng nhập hoặc mật khẩu không chính xác.'
  }
  if (/user is disabled|account is disabled|locked/i.test(raw)) {
    return 'Tài khoản này đang bị khóa. Vui lòng liên hệ Quản trị viên để mở khóa.'
  }
  if (/jwt expired|token expired/i.test(raw)) {
    return 'Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.'
  }
  if (/access is denied|forbidden/i.test(raw)) {
    return 'Bạn không có quyền thực hiện thao tác nghiệp vụ này.'
  }

  // Lỗi trùng lặp dữ liệu
  if (/username.*already exists|username.*exists/i.test(raw)) {
    return 'Tên đăng nhập đã tồn tại trong hệ thống. Vui lòng chọn tên khác.'
  }
  if (/email.*already exists|email.*exists/i.test(raw)) {
    return 'Địa chỉ email đã được sử dụng cho tài khoản khác.'
  }
  if (/phone.*already exists|phone.*exists/i.test(raw)) {
    return 'Số điện thoại đã tồn tại trong hệ thống.'
  }
  if (/code.*already exists|code.*exists/i.test(raw)) {
    return 'Mã này đã tồn tại trong danh mục. Vui lòng chọn mã khác.'
  }

  // Lỗi ràng buộc khóa ngoại / đang được sử dụng
  if (/foreign key|constraint|being used|cannot delete/i.test(raw)) {
    return 'Dữ liệu này đang được liên kết trong các nghiệp vụ khác, không thể xóa hoặc thay đổi.'
  }

  // Lỗi nghiệp vụ kho & sản xuất
  if (/insufficient stock|not enough stock/i.test(raw)) {
    return 'Số lượng tồn kho khả dụng không đủ để thực hiện yêu cầu xuất kho.'
  }
  if (/invalid status|cannot transition/i.test(raw)) {
    return 'Trạng thái hiện tại của phiếu không cho phép thực hiện thao tác này.'
  }

  // Fallback thông dụng
  if (/internal server error/i.test(raw)) {
    return 'Máy chủ gặp sự cố khi xử lý dữ liệu. Vui lòng thử lại sau.'
  }
  if (/validation failed/i.test(raw)) {
    return 'Dữ liệu nhập vào chưa đúng định dạng yêu cầu.'
  }

  return raw
}

/**
 * Toàn bộ kết nối HTTP tập trung tại đây; chuẩn hóa báo lỗi tiếng Việt nghiệp vụ.
 * @param {string} path Đường dẫn API, ví dụ `/api/users`.
 * @param {RequestOptions} [options]
 * @returns {Promise<any>} JSON trả về; `null` với HTTP 204.
 */
export async function request(path, { method = 'GET', body, query, signal } = {}) {
  const base = /** @type {string | undefined} */ (import.meta.env.VITE_API_BASE_URL)
  if (!base) throw new Error('Chưa cấu hình đường dẫn API máy chủ trong hệ thống.')
  const params = new URLSearchParams()
  Object.entries(query || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value))
  })
  const token = session.get()
  let response
  try {
    response = await fetch(`${base.replace(/\/$/, '')}${path}${params.size ? `?${params}` : ''}`, {
      method,
      signal,
      headers: {
        Accept: 'application/json',
        ...(body !== undefined && { 'Content-Type': 'application/json' }),
        ...(token && { Authorization: `Bearer ${token}` }),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch (err) {
    if (/** @type {any} */ (err)?.name === 'AbortError') throw err
    throw new Error('Không thể kết nối đến máy chủ. Vui lòng kiểm tra lại dịch vụ backend hoặc kết nối mạng.')
  }

  if (response.status === 401 && token) {
    session.clear()
    window.dispatchEvent(new Event('mrp:logout'))
  }
  if (response.status === 204) return null
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    /** @type {Record<number, string>} */
    const fallback = {
      400: 'Dữ liệu gửi lên chưa hợp lệ hoặc thiếu thông tin bắt buộc.',
      401: 'Tên đăng nhập hoặc mật khẩu không chính xác, hoặc phiên đăng nhập đã hết hạn.',
      403: 'Bạn không có quyền thực hiện thao tác nghiệp vụ này.',
      404: 'Không tìm thấy dữ liệu yêu cầu trong hệ thống.',
      409: 'Dữ liệu bị trùng lặp hoặc đang có ràng buộc nghiệp vụ ở phân hệ khác.',
      500: 'Máy chủ gặp sự cố khi xử lý dữ liệu. Vui lòng thử lại sau.',
      502: 'Dịch vụ máy chủ tạm thời không phản hồi. Vui lòng thử lại sau.',
      503: 'Hệ thống đang quá tải hoặc bảo trì. Vui lòng thử lại sau.',
    }
    const rawDetail = data?.detail || data?.message || fallback[response.status] || `Thao tác thất bại (Mã lỗi ${response.status})`
    const mainMessage = translateErrorMessage(rawDetail)
    const fieldErrors = Object.entries(data?.errors || {}).map(([field, text]) => `${field}: ${translateErrorMessage(String(text))}`)
    const fullMessage = [mainMessage, ...fieldErrors].join('\n')
    throw new Error(fullMessage)
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

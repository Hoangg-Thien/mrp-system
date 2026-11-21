/**
 * Chuẩn hóa trạng thái và màu sắc hệ thống ERP
 * Tuân thủ đúng 4 màu trạng thái nghiệp vụ (frontend/DESIGN.md):
 * 1. PENDING (Chờ xử lý / Chờ duyệt) -> Amber (#b45309)
 * 2. APPROVED / COMPLETED (Đã duyệt / Hoàn thành) -> Forest Green (#166534)
 * 3. REJECTED / CANCELLED (Từ chối / Đã hủy) -> Crimson (#b91c1c)
 * 4. IN_PROGRESS (Đang thực hiện) -> Slate Blue (#1d4ed8)
 */

export const STATUS_PRESETS = {
  PENDING: {
    key: 'PENDING',
    color: '#b45309',
    bgColor: '#fffbeb',
    borderColor: '#fde68a',
    className: 'erp-tag erp-tag-pending',
  },
  APPROVED: {
    key: 'APPROVED',
    color: '#166534',
    bgColor: '#f0fdf4',
    borderColor: '#bbf7d0',
    className: 'erp-tag erp-tag-approved',
  },
  REJECTED: {
    key: 'REJECTED',
    color: '#b91c1c',
    bgColor: '#fef2f2',
    borderColor: '#fecaca',
    className: 'erp-tag erp-tag-rejected',
  },
  IN_PROGRESS: {
    key: 'IN_PROGRESS',
    color: '#1d4ed8',
    bgColor: '#eff6ff',
    borderColor: '#bfdbfe',
    className: 'erp-tag erp-tag-progress',
  },
}

export const roleLabels = {
  ADMIN: 'Quản trị viên',
  WAREHOUSE_MANAGER: 'Quản lý kho',
  STAFF: 'Nhân viên',
}

/** Màu vai trò: phong cách ERP trung tính, không dùng màu sặc sỡ */
export const roleColors = {
  ADMIN: '#0f172a',
  WAREHOUSE_MANAGER: '#334155',
  STAFF: '#475569',
}

export const roleOptions = Object.entries(roleLabels).map(([value, label]) => ({ value, label }))
export const homeByRole = { ADMIN: '/admin', WAREHOUSE_MANAGER: '/warehouse', STAFF: '/staff' }

export const activeOptions = [
  { value: true, label: 'Đang hoạt động' },
  { value: false, label: 'Đã vô hiệu hóa' },
]

/** Trạng thái chứng từ chung */
export const docStatusLabels = {
  PENDING: 'Chờ xử lý',
  COMPLETED: 'Hoàn thành',
  CANCELLED: 'Đã hủy',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Từ chối',
  IN_PROGRESS: 'Đang thực hiện',
}

/**
 * Bản đồ ánh xạ toàn bộ trạng thái về đúng 4 trạng thái cốt lõi
 * Bỏ toàn bộ màu có sẵn của Ant Design (gold, geekblue, cyan, v.v.)
 */
export const docStatusColors = {
  PENDING: STATUS_PRESETS.PENDING.color,
  APPROVED: STATUS_PRESETS.APPROVED.color,
  COMPLETED: STATUS_PRESETS.APPROVED.color,
  REJECTED: STATUS_PRESETS.REJECTED.color,
  CANCELLED: STATUS_PRESETS.REJECTED.color,
  IN_PROGRESS: STATUS_PRESETS.IN_PROGRESS.color,
}

export const getDocStatusPreset = (status) => {
  switch (status) {
    case 'PENDING':
      return STATUS_PRESETS.PENDING
    case 'APPROVED':
    case 'COMPLETED':
      return STATUS_PRESETS.APPROVED
    case 'REJECTED':
    case 'CANCELLED':
      return STATUS_PRESETS.REJECTED
    case 'IN_PROGRESS':
      return STATUS_PRESETS.IN_PROGRESS
    default:
      return {
        key: status,
        color: '#475569',
        bgColor: '#f1f5f9',
        borderColor: '#e2e8f0',
        className: 'erp-tag erp-tag-neutral',
      }
  }
}

export const docStatusOptions = Object.entries(docStatusLabels).map(([value, label]) => ({ value, label }))

export const purchaseStatusOptions = [
  { value: 'PENDING', label: 'Chờ xử lý' },
  { value: 'COMPLETED', label: 'Hoàn thành' },
  { value: 'CANCELLED', label: 'Đã hủy' },
]

export const productionRequestStatusOptions = [
  { value: 'PENDING', label: 'Chờ xử lý' },
  { value: 'IN_PROGRESS', label: 'Đang thực hiện' },
  { value: 'COMPLETED', label: 'Hoàn thành' },
  { value: 'CANCELLED', label: 'Đã hủy' },
]

export const issueStatusOptions = [
  { value: 'PENDING', label: 'Chờ duyệt' },
  { value: 'APPROVED', label: 'Đã duyệt' },
  { value: 'REJECTED', label: 'Từ chối' },
  { value: 'CANCELLED', label: 'Đã hủy' },
]

export const productionStatusOptions = [
  { value: 'IN_PROGRESS', label: 'Đang sản xuất' },
  { value: 'COMPLETED', label: 'Hoàn thành' },
  { value: 'CANCELLED', label: 'Đã hủy' },
]

export const movementTypeLabels = {
  IN: 'Nhập kho',
  OUT: 'Xuất kho',
  ADJUST_IN: 'Điều chỉnh tăng',
  ADJUST_OUT: 'Điều chỉnh giảm',
}

/** Biến động kho: gom về 4 trạng thái cốt lõi */
export const movementTypeColors = {
  IN: STATUS_PRESETS.APPROVED.color,
  OUT: STATUS_PRESETS.IN_PROGRESS.color,
  ADJUST_IN: STATUS_PRESETS.APPROVED.color,
  ADJUST_OUT: STATUS_PRESETS.REJECTED.color,
}

export const getMovementTypePreset = (type) => {
  switch (type) {
    case 'IN':
    case 'ADJUST_IN':
      return STATUS_PRESETS.APPROVED
    case 'OUT':
      return STATUS_PRESETS.IN_PROGRESS
    case 'ADJUST_OUT':
      return STATUS_PRESETS.REJECTED
    default:
      return {
        key: type,
        color: '#475569',
        bgColor: '#f1f5f9',
        borderColor: '#e2e8f0',
        className: 'erp-tag erp-tag-neutral',
      }
  }
}

export const movementTypeOptions = Object.entries(movementTypeLabels).map(([value, label]) => ({ value, label }))

export const reportKindOptions = [
  { value: 'MATERIAL', label: 'Nguyên vật liệu' },
  { value: 'PRODUCT', label: 'Thành phẩm' },
]

export const reportGranularityOptions = [
  { value: 'MONTH', label: 'Theo tháng' },
  { value: 'QUARTER', label: 'Theo quý' },
  { value: 'YEAR', label: 'Theo năm' },
]

export const emptyText = (value) => (value === null || value === undefined || value === '' ? '—' : value)

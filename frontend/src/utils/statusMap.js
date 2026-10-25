/** Nhãn và màu hiển thị; giá trị trạng thái/quyền luôn do backend trả về. */
export const roleLabels = {
  ADMIN: 'Quản trị viên',
  WAREHOUSE_MANAGER: 'Quản lý kho',
  STAFF: 'Nhân viên',
}

export const roleColors = { ADMIN: 'geekblue', WAREHOUSE_MANAGER: 'cyan', STAFF: 'green' }
export const roleOptions = Object.entries(roleLabels).map(([value, label]) => ({ value, label }))
export const homeByRole = { ADMIN: '/admin', WAREHOUSE_MANAGER: '/warehouse', STAFF: '/staff' }
export const activeOptions = [
  { value: true, label: 'Đang hoạt động' },
  { value: false, label: 'Đã vô hiệu hóa' },
]

/** Trạng thái chứng từ chung (phiếu nhập, yêu cầu SX, xuất NVL, xuất TP…). */
export const docStatusLabels = {
  PENDING: 'Chờ xử lý',
  COMPLETED: 'Hoàn thành',
  CANCELLED: 'Đã hủy',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Từ chối',
  IN_PROGRESS: 'Đang thực hiện',
}

export const docStatusColors = {
  PENDING: 'gold',
  COMPLETED: 'green',
  CANCELLED: 'default',
  APPROVED: 'blue',
  REJECTED: 'red',
  IN_PROGRESS: 'processing',
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
  IN: 'Nhập',
  OUT: 'Xuất',
  ADJUST_IN: 'Điều chỉnh tăng',
  ADJUST_OUT: 'Điều chỉnh giảm',
}

export const movementTypeColors = {
  IN: 'green',
  OUT: 'orange',
  ADJUST_IN: 'cyan',
  ADJUST_OUT: 'magenta',
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

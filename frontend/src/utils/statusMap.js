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
export const emptyText = (value) => (value === null || value === undefined || value === '' ? '—' : value)
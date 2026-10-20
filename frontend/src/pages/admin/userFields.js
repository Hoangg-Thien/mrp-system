import { roleOptions } from '../../utils/statusMap'

/** Whitelist sort của backend AdminUserController: username | createdAt | role | active. */
export const sortOptions = [
  { value: 'createdAt', label: 'Ngày tạo' },
  { value: 'username', label: 'Tên đăng nhập' },
  { value: 'role', label: 'Vai trò' },
  { value: 'active', label: 'Trạng thái' },
]

export const directionOptions = [
  { value: 'desc', label: 'Giảm dần' },
  { value: 'asc', label: 'Tăng dần' },
]

/** Rules bám theo validation CreateUserRequest của backend. */
export const userFields = [
  { name: 'username', label: 'Tên đăng nhập', type: 'text', required: true, max: 50 },
  { name: 'password', label: 'Mật khẩu', type: 'password', required: true, min: 8, max: 100 },
  { name: 'role', label: 'Vai trò', type: 'select', required: true, options: roleOptions },
  { name: 'fullName', label: 'Họ tên', type: 'text', required: true, max: 150 },
  { name: 'email', label: 'Email', type: 'email', max: 150 },
  { name: 'phone', label: 'Số điện thoại', type: 'text', max: 20 },
  { name: 'address', label: 'Địa chỉ', type: 'textarea' },
]

import { Tag } from 'antd'
import { roleColors, roleLabels } from '../utils/statusMap'

/** Hiển thị trạng thái active do backend trả về; frontend không tự suy luận. */
export default function StatusTag({ active }) {
  return <Tag color={active ? 'green' : 'red'}>{active ? 'Đang hoạt động' : 'Đã vô hiệu hóa'}</Tag>
}

export function RoleTag({ role }) {
  return <Tag color={roleColors[role] || 'default'}>{roleLabels[role] || role}</Tag>
}

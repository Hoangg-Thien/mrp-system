import { Tag } from 'antd'
import { docStatusColors, docStatusLabels, movementTypeColors, movementTypeLabels, roleColors, roleLabels } from '../utils/statusMap'

/** Hiển thị trạng thái active do backend trả về; frontend không tự suy luận. */
export default function StatusTag({ active }) {
  return <Tag color={active ? 'green' : 'red'}>{active ? 'Đang hoạt động' : 'Đã vô hiệu hóa'}</Tag>
}

export function RoleTag({ role }) {
  return <Tag color={roleColors[role] || 'default'}>{roleLabels[role] || role}</Tag>
}

/** Tag trạng thái chứng từ (PENDING / APPROVED / …). */
export function DocStatusTag({ status }) {
  if (!status) return <Tag>—</Tag>
  return <Tag color={docStatusColors[status] || 'default'}>{docStatusLabels[status] || status}</Tag>
}

export function MovementTypeTag({ type }) {
  if (!type) return <Tag>—</Tag>
  return <Tag color={movementTypeColors[type] || 'default'}>{movementTypeLabels[type] || type}</Tag>
}

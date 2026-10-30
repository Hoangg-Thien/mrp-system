import { Tag } from 'antd'
import {
  STATUS_PRESETS,
  docStatusLabels,
  getDocStatusPreset,
  getMovementTypePreset,
  movementTypeLabels,
  roleLabels,
} from '../utils/statusMap'

/** Hiển thị trạng thái active do backend trả về; frontend không tự suy luận. */
export default function StatusTag({ active }) {
  const isOk = !!active
  return (
    <Tag className={isOk ? STATUS_PRESETS.APPROVED.className : STATUS_PRESETS.REJECTED.className}>
      {isOk ? 'Đang hoạt động' : 'Đã vô hiệu hóa'}
    </Tag>
  )
}

/** Tag vai trò: phong cách ERP trung tính, trang nhã */
export function RoleTag({ role }) {
  return (
    <Tag className="erp-tag erp-tag-role">
      {roleLabels[role] || role}
    </Tag>
  )
}

/** Tag trạng thái chứng từ (PENDING / APPROVED / COMPLETED / REJECTED / CANCELLED / IN_PROGRESS). */
export function DocStatusTag({ status }) {
  if (!status) return <Tag className="erp-tag erp-tag-neutral">—</Tag>
  const preset = getDocStatusPreset(status)
  return <Tag className={preset.className}>{docStatusLabels[status] || status}</Tag>
}

/** Tag loại biến động sổ kho */
export function MovementTypeTag({ type }) {
  if (!type) return <Tag className="erp-tag erp-tag-neutral">—</Tag>
  const preset = getMovementTypePreset(type)
  return <Tag className={preset.className}>{movementTypeLabels[type] || type}</Tag>
}

/** Tag ERP cơ sở tuân thủ đúng 4 màu nghiệp vụ: 'pending' | 'approved' | 'rejected' | 'progress' | 'neutral' */
export function ErpTag({ status = 'neutral', children, className = '' }) {
  const map = {
    pending: STATUS_PRESETS.PENDING.className,
    approved: STATUS_PRESETS.APPROVED.className,
    rejected: STATUS_PRESETS.REJECTED.className,
    progress: STATUS_PRESETS.IN_PROGRESS.className,
    neutral: 'erp-tag erp-tag-neutral',
  }
  const cls = map[status] || 'erp-tag erp-tag-neutral'
  return <Tag className={`${cls} ${className}`.trim()}>{children}</Tag>
}

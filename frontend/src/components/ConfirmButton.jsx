import { Button } from 'antd'
import { confirmAction } from '../utils/confirm'

/** Nút thao tác có hộp thoại xác nhận Modal ở giữa màn hình; chống bấm đúp nhờ loading/disabled. */
export default function ConfirmButton({
  title = 'Xác nhận thao tác?',
  description,
  okText = 'Xác nhận',
  cancelText = 'Hủy',
  danger,
  type = 'default',
  size = 'small',
  loading,
  disabled,
  children,
  icon,
  onConfirm,
  ...rest
}) {
  const handleClick = (e) => {
    e?.stopPropagation?.()
    confirmAction({
      title,
      content: description,
      okText,
      cancelText,
      danger,
      onOk: () => onConfirm?.(),
    })
  }

  return (
    <Button
      size={size}
      type={type}
      danger={danger}
      loading={loading}
      disabled={disabled || loading}
      icon={icon}
      onClick={handleClick}
      {...rest}
    >
      {children}
    </Button>
  )
}

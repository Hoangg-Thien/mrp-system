import { Button } from 'antd'
import { useRef } from 'react'
import { useConfirm } from '../utils/confirm'

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
  const { confirmAction } = useConfirm()
  const confirming = useRef(false)
  const handleClick = (e) => {
    e?.stopPropagation?.()
    if (confirming.current || loading || disabled) return
    confirming.current = true
    confirmAction({
      title,
      content: description,
      okText,
      cancelText,
      danger,
      onOk: () => onConfirm?.(),
      afterClose: () => { confirming.current = false },
    })
  }

  return (
    <Button
      size={size}
      type={type}
      danger={danger}
      loading={loading}
      disabled={(disabled || loading) || (loading)}
      icon={icon}
      onClick={handleClick}
      {...rest}
    >
      {children}
    </Button>
  )
}

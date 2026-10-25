import { Button, Popconfirm } from 'antd'

/** Nút thao tác có xác nhận; chống bấm đúp nhờ `loading`/`disabled` từ mutation. */
export default function ConfirmButton({
  title = 'Xác nhận thao tác?',
  description,
  okText = 'Đồng ý',
  cancelText = 'Hủy',
  danger,
  type = 'default',
  size = 'small',
  loading,
  disabled,
  children,
  onConfirm,
}) {
  return (
    <Popconfirm
      title={title}
      description={description}
      okText={okText}
      cancelText={cancelText}
      okButtonProps={{ danger, loading }}
      onConfirm={() => onConfirm?.()}
      disabled={disabled || loading}
    >
      <Button size={size} type={type} danger={danger} loading={loading} disabled={disabled}>
        {children}
      </Button>
    </Popconfirm>
  )
}

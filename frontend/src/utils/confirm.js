import { App } from 'antd'
import { singleFlight } from '../api/singleFlight'

/**
 * Hộp thoại xác nhận đăng xuất thống nhất toàn hệ thống (Modal ở giữa màn hình)
 * @param {Function} onConfirm - Callback thực thi khi người dùng bấm Đăng xuất
 */
function confirmLogout(modal, onConfirm) {
  modal.confirm({
    title: 'Xác nhận đăng xuất',
    content: 'Bạn có chắc chắn muốn đăng xuất khỏi phiên làm việc hiện tại không?',
    okText: 'Đăng xuất',
    cancelText: 'Hủy',
    okButtonProps: { danger: true },
    centered: true,
    width: 440,
    onOk: onConfirm,
  })
}

/**
 * Hộp thoại xác nhận thao tác quan trọng (thống nhất kiểu Modal giữa màn hình)
 * @param {Object} options - { title, content, onOk, danger, okText, cancelText, width }
 */
function confirmAction(modal, {
  title = 'Xác nhận thao tác?',
  content,
  onOk,
  danger = false,
  okText = 'Xác nhận',
  cancelText = 'Hủy',
  width = 440,
  afterClose,
}) {
  const submit = singleFlight(async () => {
    dialog.update({ okButtonProps: { danger, disabled: true }, cancelButtonProps: { disabled: true }, closable: false })
    try { return await onOk?.() } finally {
      dialog.update({ okButtonProps: { danger, disabled: false }, cancelButtonProps: { disabled: false } })
    }
  })
  const dialog = modal.confirm({
    title,
    content,
    okText,
    cancelText,
    okButtonProps: { danger },
    centered: true,
    width,
    onOk: submit,
    afterClose,
  })
  return dialog
}


export function useConfirm() {
  const { modal } = App.useApp()
  return {
    confirmAction: (options) => confirmAction(modal, options),
    confirmLogout: (onConfirm) => confirmLogout(modal, onConfirm),
  }
}

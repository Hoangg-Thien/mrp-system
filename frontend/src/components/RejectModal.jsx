import { useEffect } from 'react'
import { Form, Input, Modal } from 'antd'

/** Modal nhập lý do từ chối (backend bắt buộc reason). */
export default function RejectModal({ open, confirmLoading, onCancel, onSubmit, title = 'Từ chối phiếu' }) {
  const [form] = Form.useForm()

  useEffect(() => {
    if (open) form.resetFields()
  }, [open, form])

  return (
    <Modal
      title={title}
      open={open}
      onCancel={onCancel}
      onOk={() => form.submit()}
      okText="Từ chối"
      cancelText="Hủy"
      okButtonProps={{ danger: true }}
      confirmLoading={confirmLoading}
      centered
      width={480}
      destroyOnHidden
    >
      <Form form={form} layout="vertical" onFinish={(values) => onSubmit(values.reason)}>
        <Form.Item
          name="reason"
          label="Lý do từ chối"
          rules={[{ required: true, message: 'Vui lòng nhập lý do từ chối' }, { max: 1000, message: 'Tối đa 1000 ký tự' }]}
        >
          <Input.TextArea rows={4} maxLength={1000} placeholder="Nhập lý do từ chối..." />
        </Form.Item>
      </Form>
    </Modal>
  )
}

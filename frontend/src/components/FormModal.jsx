import { useEffect } from 'react'
import { Form, Input, InputNumber, Modal, Select } from 'antd'

function renderControl(field, options) {
  switch (field.type) {
    case 'textarea':
      return <Input.TextArea rows={3} maxLength={field.max} placeholder={field.placeholder || `Nhập ${field.label.toLowerCase()}`} />
    case 'password':
      return <Input.Password autoComplete="new-password" placeholder={field.placeholder || '••••••••'} />
    case 'money':
      return <InputNumber className="full-width" min={0} precision={2} step={1000} placeholder={field.placeholder || '0'} style={{ width: '100%' }} />
    case 'number':
      return <InputNumber className="full-width" min={field.min ?? 0} placeholder={field.placeholder || '0'} style={{ width: '100%' }} />
    case 'select':
      return <Select options={field.options} allowClear placeholder={field.placeholder || `Chọn ${field.label.toLowerCase()}`} />
    case 'category':
      return <Select options={options?.categories || []} showSearch optionFilterProp="label" placeholder="Chọn danh mục" allowClear />
    default:
      return <Input maxLength={field.max} type={field.type === 'email' ? 'email' : 'text'} placeholder={field.placeholder || `Nhập ${field.label.toLowerCase()}`} />
  }
}

/** Modal form sinh từ cấu hình fields; rules bám theo validation của backend. */
export default function FormModal({
  title,
  open,
  fields,
  options,
  initialValues,
  confirmLoading,
  onCancel,
  onSubmit,
  width,
}) {
  const [form] = Form.useForm()
  const isMultiCol = fields.length >= 4
  const modalWidth = width || (isMultiCol ? 640 : 480)

  useEffect(() => {
    if (!open) return
    form.resetFields()
    if (initialValues) form.setFieldsValue(initialValues)
  }, [open, initialValues, form])

  return (
    <Modal
      title={title}
      open={open}
      onCancel={onCancel}
      onOk={() => form.submit()}
      okText="Lưu"
      cancelText="Hủy"
      confirmLoading={confirmLoading}
      forceRender
      centered
      width={modalWidth}
      destroyOnClose
    >
      <Form
        form={form}
        layout="vertical"
        onFinish={onSubmit}
        className={isMultiCol ? 'form-modal-grid' : ''}
      >
        {fields.map((field) => {
          const isFullWidth = field.type === 'textarea' || field.fullWidth
          return (
            <Form.Item
              key={field.name}
              name={field.name}
              label={field.label}
              className={isFullWidth ? 'form-full-width' : ''}
              rules={[
                ...(field.required ? [{ required: true, message: `Vui lòng nhập ${field.label.toLowerCase()}` }] : []),
                ...(field.max ? [{ max: field.max, message: `Tối đa ${field.max} ký tự` }] : []),
                ...(field.min ? [{ min: field.min, message: `Tối thiểu ${field.min} ký tự` }] : []),
                ...(field.type === 'email' ? [{ type: 'email', message: 'Email không hợp lệ' }] : []),
                ...(field.rules || []),
              ]}
            >
              {renderControl(field, options)}
            </Form.Item>
          )
        })}
      </Form>
    </Modal>
  )
}

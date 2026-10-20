import { useEffect } from 'react'
import { Form, Input, InputNumber, Modal, Select } from 'antd'

function Control({ field, options }) {
  switch (field.type) {
    case 'textarea': return <Input.TextArea rows={3} maxLength={field.max} />
    case 'password': return <Input.Password autoComplete="new-password" />
    case 'money': return <InputNumber className="full-width" min={0} precision={2} step={1000} />
    case 'number': return <InputNumber className="full-width" min={field.min ?? 0} />
    case 'select': return <Select options={field.options} allowClear />
    case 'category': return <Select options={options?.categories || []} showSearch optionFilterProp="label" placeholder="Chọn danh mục" />
    default: return <Input maxLength={field.max} type={field.type === 'email' ? 'email' : 'text'} />
  }
}

/** Modal form sinh từ cấu hình fields; rules bám theo validation của backend. */
export default function FormModal({ title, open, fields, options, initialValues, confirmLoading, onCancel, onSubmit }) {
  const [form] = Form.useForm()

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
      width={560}
    >
      <Form form={form} layout="vertical" onFinish={onSubmit}>
        {fields.map((field) => (
          <Form.Item
            key={field.name}
            name={field.name}
            label={field.label}
            rules={[
              ...(field.required ? [{ required: true, message: `Vui lòng nhập ${field.label.toLowerCase()}` }] : []),
              ...(field.max ? [{ max: field.max, message: `Tối đa ${field.max} ký tự` }] : []),
              ...(field.min ? [{ min: field.min, message: `Tối thiểu ${field.min} ký tự` }] : []),
              ...(field.type === 'email' ? [{ type: 'email', message: 'Email không hợp lệ' }] : []),
              ...(field.rules || []),
            ]}
          >
            <Control field={field} options={options} />
          </Form.Item>
        ))}
      </Form>
    </Modal>
  )
}

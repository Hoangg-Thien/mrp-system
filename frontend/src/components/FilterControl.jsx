import { Input, InputNumber, Select } from 'antd'

/** Điều khiển lọc sinh từ cấu hình filter của từng màn hình danh mục. */
export default function FilterControl({ filter, value, onChange, categoryOptions }) {
  const style = { width: filter.width || 220 }
  if (filter.type === 'select') {
    const options = filter.name === 'categoryId' ? categoryOptions : filter.options
    return (
      <Select allowClear showSearch optionFilterProp="label" style={style} placeholder={filter.placeholder}
        value={value} options={options} onChange={(next) => onChange(next)} />
    )
  }
  if (filter.type === 'number') {
    return <InputNumber min={0} style={style} placeholder={filter.placeholder} value={value} onChange={(next) => onChange(next)} />
  }
  return (
    <Input.Search allowClear style={style} placeholder={filter.placeholder} defaultValue={value}
      onSearch={(next) => onChange(next?.trim() || undefined)} />
  )
}

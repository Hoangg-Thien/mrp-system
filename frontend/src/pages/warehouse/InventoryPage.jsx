import { useState } from 'react'
import {
  Alert,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Radio,
  Select,
  Space,
  Tabs,
  Typography,
  message,
} from 'antd'
import { PlusCircleOutlined, ReloadOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import { usePagedQuery } from '../../api/hooks'
import PageHeader from '../../components/PageHeader'
import PagedTable from '../../components/PagedTable'
import StatusTag from '../../components/StatusTag'
import { dateTime, quantity } from '../../utils/format'

const PAGE_SIZE = 15

function StockTable({ kind, onAdjustItem }) {
  const isMaterial = kind === 'materials'
  const [filters, setFilters] = useState({ positiveOnly: false })
  const [page, setPage] = useState(0)
  const [sort, setSort] = useState({ sort: 'name', direction: 'asc' })

  const categoriesQuery = useQuery({
    queryKey: ['categories-for-inventory-filter'],
    queryFn: () => api.get('/api/categories', { size: 100 }),
    enabled: !isMaterial,
  })

  const query = usePagedQuery(
    `inv-${kind}`,
    `/api/inventories/${kind}`,
    { ...filters, ...sort, page, size: PAGE_SIZE },
  )

  const columns = isMaterial
    ? [
        { title: 'Mã', dataIndex: 'code', sorter: true, width: 120 },
        { title: 'Tên NVL', dataIndex: 'name', sorter: true },
        { title: 'ĐVT', dataIndex: 'unit', width: 80 },
        {
          title: 'Tồn hiện tại',
          dataIndex: 'quantity',
          sorter: true,
          width: 140,
          render: (v) => <Typography.Text strong>{quantity(v)}</Typography.Text>,
        },
        { title: 'Trạng thái', dataIndex: 'active', width: 140, render: (v) => <StatusTag active={v} /> },
        { title: 'Cập nhật', dataIndex: 'updatedAt', width: 160, render: dateTime },
        {
          title: 'Thao tác',
          key: 'action',
          width: 120,
          render: (_, r) => (
            <Button
              size="small"
              type="link"
              onClick={() => onAdjustItem?.('material', r.materialId, r.name, r.quantity, r.unit)}
            >
              Điều chỉnh
            </Button>
          ),
        },
      ]
    : [
        { title: 'ID', dataIndex: 'productId', width: 80 },
        { title: 'Tên SP', dataIndex: 'name', sorter: true },
        { title: 'Danh mục', dataIndex: 'categoryName', width: 160 },
        { title: 'ĐVT', dataIndex: 'unit', width: 80 },
        {
          title: 'Tồn hiện tại',
          dataIndex: 'quantity',
          sorter: true,
          width: 140,
          render: (v) => <Typography.Text strong>{quantity(v, 0)}</Typography.Text>,
        },
        { title: 'Trạng thái', dataIndex: 'active', width: 140, render: (v) => <StatusTag active={v} /> },
        { title: 'Cập nhật', dataIndex: 'updatedAt', width: 160, render: dateTime },
        {
          title: 'Thao tác',
          key: 'action',
          width: 120,
          render: (_, r) => (
            <Button
              size="small"
              type="link"
              onClick={() => onAdjustItem?.('product', r.productId, r.name, r.quantity, r.unit)}
            >
              Điều chỉnh
            </Button>
          ),
        },
      ]

  const withSort = columns.map((col) => (
    col.sorter
      ? { ...col, sortOrder: sort.sort === col.dataIndex ? (sort.direction === 'desc' ? 'descend' : 'ascend') : null }
      : col
  ))

  return (
    <PagedTable
      rowKey={isMaterial ? 'materialId' : 'productId'}
      filters={(
        <Space wrap>
          <Input.Search
            allowClear
            style={{ width: 240 }}
            placeholder={isMaterial ? 'Tìm mã / tên NVL' : 'Tìm tên sản phẩm'}
            onSearch={(v) => { setPage(0); setFilters((f) => ({ ...f, keyword: v?.trim() || undefined })) }}
          />
          {!isMaterial && (
            <Select
              allowClear
              style={{ width: 180 }}
              placeholder="Danh mục"
              options={categoriesQuery.data?.content?.map((c) => ({ value: c.id, label: c.name })) || []}
              value={filters.categoryId}
              onChange={(v) => { setPage(0); setFilters((f) => ({ ...f, categoryId: v })) }}
            />
          )}
          <Select
            allowClear
            style={{ width: 160 }}
            placeholder="Trạng thái"
            options={[{ value: true, label: 'Đang hoạt động' }, { value: false, label: 'Vô hiệu' }]}
            value={filters.active}
            onChange={(v) => { setPage(0); setFilters((f) => ({ ...f, active: v })) }}
          />
          <Select
            style={{ width: 160 }}
            value={filters.positiveOnly ? 'yes' : 'no'}
            options={[{ value: 'no', label: 'Tất cả tồn' }, { value: 'yes', label: 'Chỉ tồn > 0' }]}
            onChange={(v) => { setPage(0); setFilters((f) => ({ ...f, positiveOnly: v === 'yes' })) }}
          />
          <Button icon={<ReloadOutlined />} onClick={() => { setFilters({ positiveOnly: false }); setPage(0) }}>Đặt lại</Button>
        </Space>
      )}
      columns={withSort}
      data={query.data?.content || []}
      loading={query.isFetching}
      page={page}
      size={PAGE_SIZE}
      total={query.data?.totalElements || 0}
      onPageChange={setPage}
      onTableChange={(_p, _f, sorter) => {
        const next = Array.isArray(sorter) ? sorter[0] : sorter
        setPage(0)
        setSort(next?.order
          ? { sort: next.field, direction: next.order === 'descend' ? 'desc' : 'asc' }
          : { sort: 'name', direction: 'asc' })
      }}
    />
  )
}

function StockAdjustmentModal({ open, onClose, defaultItem }) {
  const [form] = Form.useForm()
  const queryClient = useQueryClient()
  const [targetType, setTargetType] = useState('material')
  const [selectedItemId, setSelectedItemId] = useState(null)

  const materialsQuery = useQuery({
    queryKey: ['active-materials-select'],
    queryFn: () => api.get('/api/materials', { active: true, size: 100 }),
    enabled: open,
  })

  const productsQuery = useQuery({
    queryKey: ['active-products-select'],
    queryFn: () => api.get('/api/products', { size: 100 }),
    enabled: open,
  })

  // Current stock inventory for materials and products
  const invMaterialsQuery = useQuery({
    queryKey: ['inv-materials-for-modal'],
    queryFn: () => api.get('/api/inventories/materials', { size: 200 }),
    enabled: open && targetType === 'material',
  })

  const invProductsQuery = useQuery({
    queryKey: ['inv-products-for-modal'],
    queryFn: () => api.get('/api/inventories/products', { size: 200 }),
    enabled: open && targetType === 'product',
  })

  const currentStockInfo = (() => {
    if (!selectedItemId) return null
    if (targetType === 'material') {
      const inv = invMaterialsQuery.data?.content?.find((m) => m.materialId === selectedItemId)
      return inv ? { quantity: inv.quantity, unit: inv.unit } : null
    }
    const inv = invProductsQuery.data?.content?.find((p) => p.productId === selectedItemId)
    return inv ? { quantity: inv.quantity, unit: inv.unit } : null
  })()

  const adjustMutation = useMutation({
    mutationFn: (body) => api.post('/api/stock-adjustments', body),
    onSuccess: () => {
      message.success('Điều chỉnh tồn kho thành công')
      queryClient.invalidateQueries({ queryKey: ['inv-materials'] })
      queryClient.invalidateQueries({ queryKey: ['inv-products'] })
      queryClient.invalidateQueries({ queryKey: ['stock-movements'] })
      form.resetFields()
      setSelectedItemId(null)
      onClose()
    },
    onError: (err) => {
      message.error(err.message || 'Điều chỉnh tồn kho thất bại')
    },
  })

  const handleOpen = () => {
    if (defaultItem) {
      setTargetType(defaultItem.type)
      setSelectedItemId(defaultItem.id)
      form.setFieldsValue({
        targetType: defaultItem.type,
        itemId: defaultItem.id,
        direction: 'IN',
        quantity: 1,
        note: '',
      })
    } else {
      setTargetType('material')
      setSelectedItemId(null)
      form.setFieldsValue({
        targetType: 'material',
        direction: 'IN',
        quantity: 1,
        note: '',
      })
    }
  }

  const handleSubmit = (values) => {
    const payload = {
      direction: values.direction,
      quantity: values.quantity,
      note: values.note?.trim(),
      ...(values.targetType === 'material' ? { materialId: values.itemId } : { productId: values.itemId }),
    }
    adjustMutation.mutate(payload)
  }

  return (
    <Modal
      title="Điều chỉnh tồn kho (Kiểm kê)"
      open={open}
      onCancel={onClose}
      onOk={() => form.submit()}
      confirmLoading={adjustMutation.isPending}
      destroyOnHidden
      afterOpenChange={(isOpen) => isOpen && handleOpen()}
      okText="Lưu điều chỉnh"
      cancelText="Hủy"
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="Mọi điều chỉnh tồn sẽ được ghi vào sổ kho (ADJUST_IN / ADJUST_OUT). Không thể sửa xóa sau khi lưu."
      />
      <Form form={form} layout="vertical" onFinish={handleSubmit}>
        <Form.Item name="targetType" label="Loại đối tượng" initialValue="material">
          <Radio.Group onChange={(e) => {
            setTargetType(e.target.value)
            setSelectedItemId(null)
            form.setFieldValue('itemId', undefined)
          }}>
            <Radio.Button value="material">Nguyên vật liệu</Radio.Button>
            <Radio.Button value="product">Thành phẩm</Radio.Button>
          </Radio.Group>
        </Form.Item>

        <Form.Item
          name="itemId"
          label={targetType === 'material' ? 'Chọn nguyên vật liệu' : 'Chọn thành phẩm'}
          rules={[{ required: true, message: 'Vui lòng chọn đối tượng cần điều chỉnh' }]}
        >
          {targetType === 'material' ? (
            <Select
              showSearch
              placeholder="Chọn nguyên vật liệu"
              optionFilterProp="label"
              loading={materialsQuery.isLoading}
              onChange={(id) => setSelectedItemId(id)}
              options={materialsQuery.data?.content?.map((m) => ({
                value: m.id,
                label: `[${m.code}] ${m.name} (${m.unit})`,
              })) || []}
            />
          ) : (
            <Select
              showSearch
              placeholder="Chọn thành phẩm"
              optionFilterProp="label"
              loading={productsQuery.isLoading}
              onChange={(id) => setSelectedItemId(id)}
              options={productsQuery.data?.content?.map((p) => ({
                value: p.id,
                label: `${p.name} (${p.unit})`,
              })) || []}
            />
          )}
        </Form.Item>

        {currentStockInfo && (
          <div style={{ marginBottom: 16, background: '#f0fdf4', padding: '8px 12px', borderRadius: 6, border: '1px solid #bbf7d0' }}>
            <span style={{ fontSize: 13, color: '#16653a' }}>
              Tồn kho hiện tại: <strong>{quantity(currentStockInfo.quantity, targetType === 'product' ? 0 : 3)} {currentStockInfo.unit}</strong>
            </span>
          </div>
        )}

        <Form.Item name="direction" label="Hướng điều chỉnh" initialValue="IN" rules={[{ required: true }]}>
          <Radio.Group>
            <Radio value="IN">Tăng kho (+ ADJUST_IN)</Radio>
            <Radio value="OUT">Giảm kho (- ADJUST_OUT)</Radio>
          </Radio.Group>
        </Form.Item>

        <Form.Item
          name="quantity"
          label="Số lượng điều chỉnh"
          rules={[
            { required: true, message: 'Vui lòng nhập số lượng' },
            {
              validator: (_, val) => {
                if (val <= 0) return Promise.reject(new Error('Số lượng phải lớn hơn 0'))
                if (targetType === 'product' && !Number.isInteger(val)) {
                  return Promise.reject(new Error('Số lượng thành phẩm phải là số nguyên'))
                }
                return Promise.resolve()
              },
            },
          ]}
        >
          <InputNumber
            style={{ width: '100%' }}
            min={targetType === 'product' ? 1 : 0.001}
            step={targetType === 'product' ? 1 : 0.001}
            precision={targetType === 'product' ? 0 : 3}
            placeholder="Nhập số lượng"
          />
        </Form.Item>

        <Form.Item
          name="note"
          label="Lý do / Ghi chú (bắt buộc)"
          rules={[
            { required: true, message: 'Vui lòng nhập lý do điều chỉnh' },
            { max: 1000, message: 'Tối đa 1000 ký tự' },
          ]}
        >
          <Input.TextArea rows={3} maxLength={1000} placeholder="Ví dụ: Kiểm kê định kỳ phát hiện thừa/thiếu, hao hụt..." />
        </Form.Item>
      </Form>
    </Modal>
  )
}

/** W3 – Tồn kho NVL và thành phẩm (chỉ đọc) kèm chức năng điều chỉnh tồn cho WM. */
export default function InventoryPage() {
  const [modalOpen, setModalOpen] = useState(false)
  const [selectedItem, setSelectedItem] = useState(null)

  const handleAdjustItem = (type, id, name, curQty, unit) => {
    setSelectedItem({ type, id, name, curQty, unit })
    setModalOpen(true)
  }

  return (
    <>
      <PageHeader
        title="Tồn kho"
        subtitle="Tồn hiện tại do backend tính qua sổ kho; frontend chỉ hiển thị và gửi lệnh điều chỉnh"
        extra={(
          <Button
            type="primary"
            icon={<PlusCircleOutlined />}
            onClick={() => { setSelectedItem(null); setModalOpen(true) }}
          >
            Điều chỉnh tồn kho
          </Button>
        )}
      />
      <Tabs
        items={[
          { key: 'materials', label: 'Nguyên vật liệu', children: <StockTable kind="materials" onAdjustItem={handleAdjustItem} /> },
          { key: 'products', label: 'Thành phẩm', children: <StockTable kind="products" onAdjustItem={handleAdjustItem} /> },
        ]}
      />
      <StockAdjustmentModal
        open={modalOpen}
        onClose={() => { setModalOpen(false); setSelectedItem(null) }}
        defaultItem={selectedItem}
      />
    </>
  )
}

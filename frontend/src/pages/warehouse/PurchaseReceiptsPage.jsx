import { useAllPages } from '../../api/useAllPages'
import QueryError from '../../components/QueryError'
import { useState } from 'react'
import {
  Button,
  Card,
  DatePicker,
  Descriptions,
  Divider,
  Form,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import {
  CheckOutlined,
  CloseOutlined,
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { api } from '../../api/client'
import { usePagedQuery } from '../../api/hooks'
import ConfirmButton from '../../components/ConfirmButton'
import PageHeader from '../../components/PageHeader'
import PagedTable from '../../components/PagedTable'
import { DocStatusTag } from '../../components/StatusTag'
import { dateTime, money, quantity } from '../../utils/format'
import { purchaseStatusOptions } from '../../utils/statusMap'

const PAGE_SIZE = 15

export default function PurchaseReceiptsPage() {
  const queryClient = useQueryClient()
  const [filters, setFilters] = useState({})
  const [page, setPage] = useState(0)
  const [sort, setSort] = useState({ sort: 'id', direction: 'desc' })

  // Modal states
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [editItem, setEditItem] = useState(null)
  const [detailId, setDetailId] = useState(null)

  const [form] = Form.useForm()
  const [editForm] = Form.useForm()

  const createItems = Form.useWatch('items', form)
  const editItems = Form.useWatch('items', editForm)

  const createEstimatedTotal = (createItems || []).reduce(
    (sum, it) => sum + ((Number(it?.quantity) || 0) * (Number(it?.unitPrice) || 0)),
    0,
  )
  const editEstimatedTotal = (editItems || []).reduce(
    (sum, it) => sum + ((Number(it?.quantity) || 0) * (Number(it?.unitPrice) || 0)),
    0,
  )

  const receiptsQuery = usePagedQuery(
    'purchase-receipts',
    '/api/purchase-receipts',
    { ...filters, ...sort, page, size: PAGE_SIZE },
  )

  const suppliersQuery = useQuery({
    queryKey: ['suppliers-all'],
    queryFn: () => api.get('/api/suppliers', { size: 100 }),
  })

  const materialsQuery = useAllPages({
    queryKey: ['active-materials-all'],
    path: '/api/materials',
    params: { active: true },
  })

  const detailQuery = useQuery({
    queryKey: ['purchase-receipt-detail', detailId],
    queryFn: () => api.get(`/api/purchase-receipts/${detailId}`),
    enabled: !!detailId,
  })

  const completeMutation = useMutation({
    mutationFn: (id) => api.post(`/api/purchase-receipts/${id}/complete`),
    onSuccess: () => {
      message.success('Đã hoàn tất phiếu nhập kho và ghi nhận tăng tồn kho!')
      queryClient.invalidateQueries({ queryKey: ['purchase-receipts'] })
      queryClient.invalidateQueries({ queryKey: ['inv-materials'] })
      queryClient.invalidateQueries({ queryKey: ['stock-movements'] })
      if (detailId) queryClient.invalidateQueries({ queryKey: ['purchase-receipt-detail', detailId] })
    },
    onError: (err) => message.error(err.message || 'Không thể hoàn tất phiếu nhập'),
  })

  const cancelMutation = useMutation({
    mutationFn: (id) => api.post(`/api/purchase-receipts/${id}/cancel`),
    onSuccess: () => {
      message.success('Đã hủy phiếu nhập kho')
      queryClient.invalidateQueries({ queryKey: ['purchase-receipts'] })
      if (detailId) queryClient.invalidateQueries({ queryKey: ['purchase-receipt-detail', detailId] })
    },
    onError: (err) => message.error(err.message || 'Không thể hủy phiếu nhập'),
  })

  const createMutation = useMutation({
    mutationFn: (body) => api.post('/api/purchase-receipts', body),
    onSuccess: () => {
      message.success('Tạo phiếu nhập kho thành công')
      queryClient.invalidateQueries({ queryKey: ['purchase-receipts'] })
      form.resetFields()
      setCreateModalOpen(false)
    },
    onError: (err) => message.error(err.message || 'Lỗi tạo phiếu nhập kho'),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, body }) => api.put(`/api/purchase-receipts/${id}`, body),
    onSuccess: () => {
      message.success('Cập nhật phiếu nhập kho thành công')
      queryClient.invalidateQueries({ queryKey: ['purchase-receipts'] })
      editForm.resetFields()
      setEditItem(null)
    },
    onError: (err) => message.error(err.message || 'Lỗi cập nhật phiếu nhập kho'),
  })

  const columns = [
    { title: 'Mã phiếu', dataIndex: 'id', width: 90, sorter: true, render: (v) => `#${v}` },
    { title: 'Nhà cung cấp', dataIndex: 'supplierName' },
    { title: 'Ngày tạo', dataIndex: 'receiptDate', sorter: true, width: 160, render: dateTime },
    { title: 'Tổng tiền (VNĐ)', dataIndex: 'totalAmount', sorter: true, width: 150, render: (v) => money(v) },
    { title: 'Người tạo', dataIndex: 'warehouseManagerName', width: 140 },
    { title: 'Trạng thái', dataIndex: 'status', width: 120, sorter: true, render: (v) => <DocStatusTag status={v} /> },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 220,
      render: (_, r) => (
        <Space size="small">
          <Button size="small" icon={<EyeOutlined />} onClick={() => setDetailId(r.id)}>
            Chi tiết
          </Button>
          {r.status === 'PENDING' && (
            <>
              <Button
                size="small"
                icon={<EditOutlined />}
                onClick={async () => {
                  const detail = await api.get(`/api/purchase-receipts/${r.id}`)
                  setEditItem(detail)
                  editForm.setFieldsValue({
                    supplierId: detail.supplierId,
                    items: detail.items?.map((it) => ({
                      materialId: it.materialId,
                      quantity: it.quantity,
                      unitPrice: it.unitPrice,
                    })) || [],
                  })
                }}
              >
                Sửa
              </Button>
              <ConfirmButton
                type="primary"
                size="small"
                title="Hoàn tất phiếu nhập này?"
                description="Hệ thống sẽ cập nhật tăng tồn kho NVL. Hành động này không thể hoàn tác."
                loading={completeMutation.isPending}
                onConfirm={() => completeMutation.mutate(r.id)}
              >
                <CheckOutlined /> Hoàn tất
              </ConfirmButton>
              <ConfirmButton
                danger
                size="small"
                title="Hủy phiếu nhập kho này?"
                description="Phiếu sẽ bị hủy và không ghi nhận tồn kho."
                loading={cancelMutation.isPending}
                onConfirm={() => cancelMutation.mutate(r.id)}
              >
                <CloseOutlined />
              </ConfirmButton>
            </>
          )}
        </Space>
      ),
    },
  ].map((col) => (
    col.sorter
      ? { ...col, sortOrder: sort.sort === col.dataIndex ? (sort.direction === 'desc' ? 'descend' : 'ascend') : null }
      : col
  ))

  const handleCreateSubmit = (values) => {
    createMutation.mutate({
      supplierId: values.supplierId,
      items: values.items.map((it) => ({
        materialId: it.materialId,
        quantity: it.quantity,
        unitPrice: it.unitPrice,
      })),
    })
  }

  const handleUpdateSubmit = (values) => {
    updateMutation.mutate({
      id: editItem.id,
      body: {
        supplierId: values.supplierId,
        items: values.items.map((it) => ({
          materialId: it.materialId,
          quantity: it.quantity,
          unitPrice: it.unitPrice,
        })),
      },
    })
  }

  return (
    <>
      <QueryError query={materialsQuery} />
      <PageHeader
        title="Phiếu nhập kho nguyên vật liệu"
        subtitle="Quản lý và hoàn tất nhập kho từ nhà cung cấp"
        extra={(
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              form.setFieldsValue({ items: [{ materialId: undefined, quantity: 1, unitPrice: 0 }] })
              setCreateModalOpen(true)
            }}
          >
            Tạo phiếu nhập mới
          </Button>
        )}
      />

      <PagedTable query={receiptsQuery}
        filters={(
          <Space wrap>
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              style={{ width: 220 }}
              placeholder="Chọn nhà cung cấp"
              options={suppliersQuery.data?.content?.map((s) => ({ value: s.id, label: s.name })) || []}
              value={filters.supplierId}
              onChange={(v) => { setPage(0); setFilters((f) => ({ ...f, supplierId: v })) }}
            />
            <Select
              allowClear
              style={{ width: 160 }}
              placeholder="Trạng thái"
              options={purchaseStatusOptions}
              value={filters.status}
              onChange={(v) => { setPage(0); setFilters((f) => ({ ...f, status: v })) }}
            />
            <DatePicker.RangePicker
              style={{ width: 260 }}
              value={filters.from && filters.to ? [dayjs(filters.from), dayjs(filters.to)] : null}
              onChange={(range) => {
                setPage(0)
                setFilters((f) => ({
                  ...f,
                  from: range?.[0]?.format('YYYY-MM-DD'),
                  to: range?.[1]?.format('YYYY-MM-DD'),
                }))
              }}
            />
            <Button
              icon={<ReloadOutlined />}
              onClick={() => { setFilters({}); setPage(0) }}
            >
              Đặt lại
            </Button>
          </Space>
        )}
        columns={columns}
        data={receiptsQuery.data?.content || []}
        loading={receiptsQuery.isFetching}
        page={page}
        size={PAGE_SIZE}
        total={receiptsQuery.data?.totalElements || 0}
        onPageChange={setPage}
        onTableChange={(_p, _f, sorter) => {
          const next = Array.isArray(sorter) ? sorter[0] : sorter
          setPage(0)
          setSort(next?.order
            ? { sort: next.field, direction: next.order === 'descend' ? 'desc' : 'asc' }
            : { sort: 'id', direction: 'desc' })
        }}
      />

      {/* Modal tạo phiếu nhập mới */}
      <Modal
        title="Tạo phiếu nhập kho NVL"
        open={createModalOpen}
        width={760}
        onCancel={() => setCreateModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending}
        destroyOnHidden
        okText="Tạo phiếu (PENDING)"
        cancelText="Hủy"
      >
        <Form form={form} layout="vertical" onFinish={handleCreateSubmit}>
          <Form.Item
            name="supplierId"
            label="Nhà cung cấp"
            rules={[{ required: true, message: 'Vui lòng chọn nhà cung cấp' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="Chọn nhà cung cấp"
              options={suppliersQuery.data?.content?.map((s) => ({ value: s.id, label: `${s.name} (${s.phone || 'Không SĐT'})` })) || []}
            />
          </Form.Item>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
            <Typography.Title level={5} style={{ margin: 0 }}>Danh sách dòng nguyên vật liệu</Typography.Title>
            <Tag className="erp-tag erp-tag-approved">
              Tạm tính: <strong>{money(createEstimatedTotal)} đ</strong>
            </Tag>
          </div>

          <Form.List
            name="items"
            rules={[
              {
                validator: async (_, items) => {
                  if (!items || items.length === 0) {
                    return Promise.reject(new Error('Phiếu nhập phải có ít nhất 1 dòng nguyên vật liệu'))
                  }
                  const materialIds = items.map((it) => it?.materialId).filter(Boolean)
                  if (new Set(materialIds).size !== materialIds.length) {
                    return Promise.reject(new Error('Không được chọn trùng nguyên vật liệu trong cùng một phiếu'))
                  }
                },
              },
            ]}
          >
            {(fields, { add, remove }, { errors }) => (
              <>
                {fields.map(({ key, name, ...restField }) => (
                  <Card key={key} size="small" style={{ marginBottom: 12, background: '#fafafa' }}>
                    <Space align="baseline" style={{ display: 'flex', width: '100%' }} wrap>
                      <Form.Item
                        {...restField}
                        name={[name, 'materialId']}
                        label="Nguyên vật liệu"
                        rules={[{ required: true, message: 'Chọn NVL' }]}
                        style={{ minWidth: 260 }}
                      >
                        <Select
                          showSearch
                          placeholder="Chọn NVL"
                          optionFilterProp="label"
                          options={materialsQuery.data?.content?.map((m) => ({
                            value: m.id,
                            label: `[${m.code}] ${m.name} (${m.unit})`,
                          })) || []}
                        />
                      </Form.Item>

                      <Form.Item
                        {...restField}
                        name={[name, 'quantity']}
                        label="Số lượng"
                        rules={[
                          { required: true, message: 'Nhập số lượng' },
                          { type: 'number', min: 0.001, message: '> 0' },
                        ]}
                      >
                        <InputNumber min={0.001} step={0.01} precision={3} placeholder="SL" style={{ width: 120 }} />
                      </Form.Item>

                      <Form.Item
                        {...restField}
                        name={[name, 'unitPrice']}
                        label="Đơn giá (VNĐ)"
                        rules={[
                          { required: true, message: 'Nhập đơn giá' },
                          { type: 'number', min: 0, message: '≥ 0' },
                        ]}
                      >
                        <InputNumber min={0} step={1000} precision={2} placeholder="Đơn giá" style={{ width: 140 }} />
                      </Form.Item>

                      <Button
                        danger
                        type="text"
                        icon={<DeleteOutlined />}
                        onClick={() => remove(name)}
                        disabled={fields.length === 1}
                      />
                    </Space>
                  </Card>
                ))}

                <Form.ErrorList errors={errors} />

                <Button
                  type="dashed"
                  onClick={() => add({ materialId: undefined, quantity: 1, unitPrice: 0 })}
                  block
                  icon={<PlusOutlined />}
                >
                  Thêm dòng nguyên vật liệu
                </Button>
              </>
            )}
          </Form.List>
        </Form>
      </Modal>

      {/* Modal sửa dòng phiếu nhập (khi PENDING) */}
      <Modal
        title={`Chỉnh sửa phiếu nhập #${editItem?.id}`}
        open={!!editItem}
        width={760}
        onCancel={() => setEditItem(null)}
        onOk={() => editForm.submit()}
        confirmLoading={updateMutation.isPending}
        destroyOnHidden
        okText="Lưu thay đổi"
        cancelText="Đóng"
      >
        <Form form={editForm} layout="vertical" onFinish={handleUpdateSubmit}>
          <Form.Item
            name="supplierId"
            label="Nhà cung cấp"
            rules={[{ required: true, message: 'Vui lòng chọn nhà cung cấp' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              options={suppliersQuery.data?.content?.map((s) => ({ value: s.id, label: s.name })) || []}
            />
          </Form.Item>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
            <Typography.Title level={5} style={{ margin: 0 }}>Danh sách dòng nguyên vật liệu</Typography.Title>
            <Tag className="erp-tag erp-tag-approved">
              Tạm tính: <strong>{money(editEstimatedTotal)} đ</strong>
            </Tag>
          </div>

          <Form.List
            name="items"
            rules={[
              {
                validator: async (_, items) => {
                  if (!items || items.length === 0) {
                    return Promise.reject(new Error('Phiếu nhập phải có ít nhất 1 dòng nguyên vật liệu'))
                  }
                  const materialIds = items.map((it) => it?.materialId).filter(Boolean)
                  if (new Set(materialIds).size !== materialIds.length) {
                    return Promise.reject(new Error('Không được chọn trùng nguyên vật liệu trong cùng một phiếu'))
                  }
                },
              },
            ]}
          >
            {(fields, { add, remove }, { errors }) => (
              <>
                {fields.map(({ key, name, ...restField }) => (
                  <Card key={key} size="small" style={{ marginBottom: 12, background: '#fafafa' }}>
                    <Space align="baseline" style={{ display: 'flex', width: '100%' }} wrap>
                      <Form.Item
                        {...restField}
                        name={[name, 'materialId']}
                        label="Nguyên vật liệu"
                        rules={[{ required: true, message: 'Chọn NVL' }]}
                        style={{ minWidth: 260 }}
                      >
                        <Select
                          showSearch
                          placeholder="Chọn NVL"
                          optionFilterProp="label"
                          options={materialsQuery.data?.content?.map((m) => ({
                            value: m.id,
                            label: `[${m.code}] ${m.name} (${m.unit})`,
                          })) || []}
                        />
                      </Form.Item>

                      <Form.Item
                        {...restField}
                        name={[name, 'quantity']}
                        label="Số lượng"
                        rules={[
                          { required: true, message: 'Nhập số lượng' },
                          { type: 'number', min: 0.001, message: '> 0' },
                        ]}
                      >
                        <InputNumber min={0.001} step={0.01} precision={3} style={{ width: 120 }} />
                      </Form.Item>

                      <Form.Item
                        {...restField}
                        name={[name, 'unitPrice']}
                        label="Đơn giá (VNĐ)"
                        rules={[
                          { required: true, message: 'Nhập đơn giá' },
                          { type: 'number', min: 0, message: '≥ 0' },
                        ]}
                      >
                        <InputNumber min={0} step={1000} precision={2} style={{ width: 140 }} />
                      </Form.Item>

                      <Button
                        danger
                        type="text"
                        icon={<DeleteOutlined />}
                        onClick={() => remove(name)}
                        disabled={fields.length === 1}
                      />
                    </Space>
                  </Card>
                ))}

                <Form.ErrorList errors={errors} />

                <Button
                  type="dashed"
                  onClick={() => add({ materialId: undefined, quantity: 1, unitPrice: 0 })}
                  block
                  icon={<PlusOutlined />}
                >
                  Thêm dòng nguyên vật liệu
                </Button>
              </>
            )}
          </Form.List>
        </Form>
      </Modal>

      {/* Modal chi tiết phiếu nhập */}
      <Modal
        title={`Chi tiết phiếu nhập #${detailId}`}
        open={!!detailId}
        width={720}
        footer={[
          detailQuery.data?.status === 'PENDING' && (
            <ConfirmButton
              key="complete"
              type="primary"
              title="Xác nhận hoàn tất phiếu nhập này?"
              description="Hệ thống sẽ cập nhật tăng tồn kho NVL. Không thể hoàn tác."
              loading={completeMutation.isPending}
              onConfirm={() => completeMutation.mutate(detailId)}
            >
              Hoàn tất phiếu nhập
            </ConfirmButton>
          ),
          detailQuery.data?.status === 'PENDING' && (
            <ConfirmButton
              key="cancel"
              danger
              title="Xác nhận hủy phiếu nhập này?"
              loading={cancelMutation.isPending}
              onConfirm={() => cancelMutation.mutate(detailId)}
            >
              Hủy phiếu
            </ConfirmButton>
          ),
          <Button key="close" onClick={() => setDetailId(null)}>Đóng</Button>,
        ]}
        onCancel={() => setDetailId(null)}
        destroyOnHidden
      >
        {detailQuery.data && (
          <>
            <Descriptions bordered size="small" column={2}>
              <Descriptions.Item label="Mã phiếu">#{detailQuery.data.id}</Descriptions.Item>
              <Descriptions.Item label="Trạng thái">
                <DocStatusTag status={detailQuery.data.status} />
              </Descriptions.Item>
              <Descriptions.Item label="Nhà cung cấp">{detailQuery.data.supplierName}</Descriptions.Item>
              <Descriptions.Item label="Người tạo">{detailQuery.data.warehouseManagerName}</Descriptions.Item>
              <Descriptions.Item label="Ngày nhập">{dateTime(detailQuery.data.receiptDate)}</Descriptions.Item>
              <Descriptions.Item label="Tổng tiền">{money(detailQuery.data.totalAmount)} VNĐ</Descriptions.Item>
            </Descriptions>

            <Divider style={{ margin: '16px 0 12px' }}>Chi tiết các dòng nguyên vật liệu</Divider>

            <Table
              size="small"
              rowKey="materialId"
              pagination={false}
              dataSource={detailQuery.data.items || []}
              columns={[
                { title: 'Mã NVL', dataIndex: 'materialCode', width: 100 },
                { title: 'Tên nguyên vật liệu', dataIndex: 'materialName' },
                { title: 'ĐVT', dataIndex: 'unit', width: 70 },
                { title: 'Số lượng', dataIndex: 'quantity', width: 110, render: (v) => quantity(v) },
                { title: 'Đơn giá', dataIndex: 'unitPrice', width: 120, render: (v) => `${money(v)} đ` },
                { title: 'Thành tiền', dataIndex: 'amount', width: 130, render: (v) => `${money(v)} đ` },
              ]}
              summary={(items) => {
                const total = items.reduce((sum, item) => sum + (Number(item.amount) || 0), 0)
                return (
                  <Table.Summary.Row>
                    <Table.Summary.Cell index={0} colSpan={5} style={{ textAlign: 'right', fontWeight: 600 }}>
                      Tổng cộng:
                    </Table.Summary.Cell>
                    <Table.Summary.Cell index={1} style={{ fontWeight: 700, color: 'var(--color-primary)' }}>
                      {money(total)} đ
                    </Table.Summary.Cell>
                  </Table.Summary.Row>
                )
              }}
            />
          </>
        )}
      </Modal>
    </>
  )
}


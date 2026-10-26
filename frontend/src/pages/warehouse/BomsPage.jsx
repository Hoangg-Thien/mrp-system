import { useState } from 'react'
import {
  Badge,
  Button,
  Card,
  Descriptions,
  Divider,
  Form,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Typography,
  message,
} from 'antd'
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  DeleteOutlined,
  EyeOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import { usePagedQuery } from '../../api/hooks'
import ConfirmButton from '../../components/ConfirmButton'
import PageHeader from '../../components/PageHeader'
import PagedTable from '../../components/PagedTable'
import StatusTag from '../../components/StatusTag'
import { dateTime, quantity } from '../../utils/format'

const PAGE_SIZE = 15

export default function BomsPage() {
  const queryClient = useQueryClient()
  const [filters, setFilters] = useState({})
  const [page, setPage] = useState(0)
  const [sort, setSort] = useState({ sort: 'id', direction: 'desc' })

  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [detailId, setDetailId] = useState(null)
  const [form] = Form.useForm()

  const bomsQuery = usePagedQuery(
    'boms-list',
    '/api/boms',
    { ...filters, ...sort, page, size: PAGE_SIZE },
  )

  const productsQuery = useQuery({
    queryKey: ['products-for-bom'],
    queryFn: () => api.get('/api/products', { size: 100 }),
  })

  const materialsQuery = useQuery({
    queryKey: ['active-materials-for-bom'],
    queryFn: () => api.get('/api/materials', { active: true, size: 100 }),
  })

  const detailQuery = useQuery({
    queryKey: ['bom-detail', detailId],
    queryFn: () => api.get(`/api/boms/${detailId}`),
    enabled: !!detailId,
  })

  const createMutation = useMutation({
    mutationFn: (body) => api.post('/api/boms', body),
    onSuccess: () => {
      message.success('Tạo công thức BOM mới thành công (tự động kích hoạt)')
      queryClient.invalidateQueries({ queryKey: ['boms-list'] })
      form.resetFields()
      setCreateModalOpen(false)
    },
    onError: (err) => message.error(err.message || 'Lỗi tạo BOM'),
  })

  const activateMutation = useMutation({
    mutationFn: (id) => api.post(`/api/boms/${id}/activate`),
    onSuccess: () => {
      message.success('Kích hoạt BOM thành công (BOM cũ của sản phẩm tự động vô hiệu)')
      queryClient.invalidateQueries({ queryKey: ['boms-list'] })
      if (detailId) queryClient.invalidateQueries({ queryKey: ['bom-detail', detailId] })
    },
    onError: (err) => message.error(err.message || 'Lỗi kích hoạt BOM'),
  })

  const deactivateMutation = useMutation({
    mutationFn: (id) => api.post(`/api/boms/${id}/deactivate`),
    onSuccess: () => {
      message.success('Đã vô hiệu hóa BOM')
      queryClient.invalidateQueries({ queryKey: ['boms-list'] })
      if (detailId) queryClient.invalidateQueries({ queryKey: ['bom-detail', detailId] })
    },
    onError: (err) => message.error(err.message || 'Lỗi vô hiệu hóa BOM'),
  })

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 80, sorter: true },
    { title: 'Sản phẩm', dataIndex: 'productName' },
    { title: 'ĐVT', dataIndex: 'productUnit', width: 80 },
    {
      title: 'Phiên bản',
      dataIndex: 'version',
      sorter: true,
      width: 110,
      render: (v) => <Badge count={`v${v}`} style={{ backgroundColor: '#108ee9' }} />,
    },
    {
      title: 'Trạng thái',
      dataIndex: 'active',
      width: 140,
      render: (v) => <StatusTag active={v} />,
    },
    {
      title: 'Số dòng NVL',
      dataIndex: 'itemCount',
      width: 120,
      render: (v) => `${v} loại vật tư`,
    },
    { title: 'Ngày tạo', dataIndex: 'createdAt', width: 160, sorter: true, render: dateTime },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 220,
      render: (_, r) => (
        <Space size="small">
          <Button size="small" icon={<EyeOutlined />} onClick={() => setDetailId(r.id)}>
            Chi tiết
          </Button>
          {!r.active ? (
            <ConfirmButton
              type="primary"
              size="small"
              title="Kích hoạt BOM phiên bản này?"
              description="BOM đang active khác của cùng sản phẩm này sẽ tự động chuyển thành vô hiệu hóa."
              loading={activateMutation.isPending}
              onConfirm={() => activateMutation.mutate(r.id)}
            >
              <CheckCircleOutlined /> Kích hoạt
            </ConfirmButton>
          ) : (
            <ConfirmButton
              danger
              size="small"
              title="Vô hiệu hóa BOM này?"
              description="Sản phẩm sẽ không thể dùng BOM này để tạo kế hoạch MRP hoặc yêu cầu sản xuất mới cho đến khi có BOM active khác."
              loading={deactivateMutation.isPending}
              onConfirm={() => deactivateMutation.mutate(r.id)}
            >
              <CloseCircleOutlined /> Vô hiệu
            </ConfirmButton>
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
      productId: values.productId,
      items: values.items.map((it) => ({
        materialId: it.materialId,
        quantity: it.quantity,
      })),
    })
  }

  return (
    <>
      <PageHeader
        title="Công thức sản phẩm (BOM)"
        subtitle="Quản lý định mức nguyên vật liệu cấu thành 1 đơn vị thành phẩm"
        extra={(
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              form.setFieldsValue({ items: [{ materialId: undefined, quantity: 1 }] })
              setCreateModalOpen(true)
            }}
          >
            Tạo BOM mới
          </Button>
        )}
      />

      <PagedTable
        filters={(
          <Space wrap>
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              style={{ width: 240 }}
              placeholder="Lọc theo sản phẩm"
              options={productsQuery.data?.content?.map((p) => ({ value: p.id, label: p.name })) || []}
              value={filters.productId}
              onChange={(v) => { setPage(0); setFilters((f) => ({ ...f, productId: v })) }}
            />
            <Select
              allowClear
              style={{ width: 160 }}
              placeholder="Trạng thái"
              options={[
                { value: true, label: 'Đang kích hoạt' },
                { value: false, label: 'Đã vô hiệu' },
              ]}
              value={filters.active}
              onChange={(v) => { setPage(0); setFilters((f) => ({ ...f, active: v })) }}
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
        data={bomsQuery.data?.content || []}
        loading={bomsQuery.isFetching}
        page={page}
        size={PAGE_SIZE}
        total={bomsQuery.data?.totalElements || 0}
        onPageChange={setPage}
        onTableChange={(_p, _f, sorter) => {
          const next = Array.isArray(sorter) ? sorter[0] : sorter
          setPage(0)
          setSort(next?.order
            ? { sort: next.field, direction: next.order === 'descend' ? 'desc' : 'asc' }
            : { sort: 'id', direction: 'desc' })
        }}
      />

      {/* Modal tạo BOM mới */}
      <Modal
        title="Tạo công thức sản phẩm (BOM)"
        open={createModalOpen}
        width={720}
        onCancel={() => setCreateModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending}
        destroyOnHidden
        okText="Lưu và Kích hoạt"
        cancelText="Hủy"
      >
        <Form form={form} layout="vertical" onFinish={handleCreateSubmit}>
          <Form.Item
            name="productId"
            label="Sản phẩm"
            rules={[{ required: true, message: 'Vui lòng chọn sản phẩm' }]}
          >
            <Select
              showSearch
              placeholder="Chọn sản phẩm hoàn thiện"
              optionFilterProp="label"
              options={productsQuery.data?.content?.map((p) => ({ value: p.id, label: `${p.name} (${p.unit})` })) || []}
            />
          </Form.Item>

          <Typography.Title level={5}>
            Định mức nguyên vật liệu cho 1 đơn vị sản phẩm
          </Typography.Title>

          <Form.List
            name="items"
            rules={[
              {
                validator: async (_, items) => {
                  if (!items || items.length === 0) {
                    return Promise.reject(new Error('BOM phải có ít nhất 1 dòng nguyên vật liệu'))
                  }
                  const ids = items.map((it) => it?.materialId).filter(Boolean)
                  if (new Set(ids).size !== ids.length) {
                    return Promise.reject(new Error('Không được chọn trùng nguyên vật liệu trong cùng một BOM'))
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
                        style={{ minWidth: 320 }}
                      >
                        <Select
                          showSearch
                          placeholder="Chọn nguyên vật liệu"
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
                        label="Số lượng cần cho 1 SP"
                        rules={[
                          { required: true, message: 'Nhập số lượng' },
                          { type: 'number', min: 0.001, message: '> 0' },
                        ]}
                      >
                        <InputNumber min={0.001} step={0.1} precision={3} style={{ width: 160 }} />
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
                  onClick={() => add({ materialId: undefined, quantity: 1 })}
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

      {/* Modal chi tiết BOM */}
      <Modal
        title={`Chi tiết BOM #${detailId} – v${detailQuery.data?.version}`}
        open={!!detailId}
        width={680}
        footer={[
          detailQuery.data && !detailQuery.data.active && (
            <ConfirmButton
              key="activate"
              type="primary"
              title="Kích hoạt BOM phiên bản này?"
              description="BOM đang active khác của cùng sản phẩm sẽ tự chuyển sang vô hiệu."
              loading={activateMutation.isPending}
              onConfirm={() => activateMutation.mutate(detailId)}
            >
              Kích hoạt BOM
            </ConfirmButton>
          ),
          detailQuery.data && detailQuery.data.active && (
            <ConfirmButton
              key="deactivate"
              danger
              title="Vô hiệu hóa BOM này?"
              loading={deactivateMutation.isPending}
              onConfirm={() => deactivateMutation.mutate(detailId)}
            >
              Vô hiệu hóa BOM
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
              <Descriptions.Item label="Sản phẩm">{detailQuery.data.productName}</Descriptions.Item>
              <Descriptions.Item label="Đơn vị tính">{detailQuery.data.productUnit}</Descriptions.Item>
              <Descriptions.Item label="Phiên bản">v{detailQuery.data.version}</Descriptions.Item>
              <Descriptions.Item label="Trạng thái">
                <StatusTag active={detailQuery.data.active} />
              </Descriptions.Item>
              <Descriptions.Item label="Ngày tạo" span={2}>
                {dateTime(detailQuery.data.createdAt)}
              </Descriptions.Item>
            </Descriptions>

            <Divider style={{ margin: '16px 0 12px' }}>Định mức nguyên vật liệu (1 đơn vị sản phẩm)</Divider>

            <Table
              size="small"
              rowKey="id"
              pagination={false}
              dataSource={detailQuery.data.items || []}
              columns={[
                { title: 'Mã NVL', dataIndex: 'materialCode', width: 110 },
                { title: 'Tên nguyên vật liệu', dataIndex: 'materialName' },
                { title: 'ĐVT', dataIndex: 'materialUnit', width: 80 },
                {
                  title: 'Định mức cần',
                  dataIndex: 'quantity',
                  width: 140,
                  render: (v, r) => `${quantity(v)} ${r.materialUnit || ''}`,
                },
              ]}
            />
          </>
        )}
      </Modal>
    </>
  )
}


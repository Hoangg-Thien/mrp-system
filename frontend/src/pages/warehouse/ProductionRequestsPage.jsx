import { useEffect, useState } from 'react'
import {
  Badge,
  Button,
  Descriptions,
  Divider,
  Form,
  Input,
  InputNumber,
  Modal,
  Progress,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import {
  CloseOutlined,
  EyeOutlined,
  FileSearchOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useLocation } from 'react-router-dom'
import { api } from '../../api/client'
import { usePagedQuery } from '../../api/hooks'
import ConfirmButton from '../../components/ConfirmButton'
import PageHeader from '../../components/PageHeader'
import PagedTable from '../../components/PagedTable'
import { DocStatusTag } from '../../components/StatusTag'
import { dateTime, quantity } from '../../utils/format'
import { productionRequestStatusOptions } from '../../utils/statusMap'

const PAGE_SIZE = 15

export default function ProductionRequestsPage() {
  const location = useLocation()
  const queryClient = useQueryClient()
  const [filters, setFilters] = useState({})
  const [page, setPage] = useState(0)
  const [sort, setSort] = useState({ sort: 'id', direction: 'desc' })

  const [createModalOpen, setCreateModalOpen] = useState(() => Boolean(location.state?.prefillProductId))
  const [detailId, setDetailId] = useState(null)
  const [requirementsId, setRequirementsId] = useState(null)
  const [form] = Form.useForm()

  // Handle prefill from MRP Planning page
  useEffect(() => {
    if (location.state?.prefillProductId) {
      form.setFieldsValue({
        productId: location.state.prefillProductId,
        quantity: location.state.prefillQuantity || 1,
      })
    }
  }, [location.state, form])

  const prQuery = usePagedQuery(
    'production-requests',
    '/api/production-requests',
    { ...filters, ...sort, page, size: PAGE_SIZE },
  )

  const productsQuery = useQuery({
    queryKey: ['products-for-pr'],
    queryFn: () => api.get('/api/products', { size: 100 }),
  })

  const staffQuery = useQuery({
    queryKey: ['staff-list-all'],
    queryFn: () => api.get('/api/staff'),
  })

  const detailQuery = useQuery({
    queryKey: ['pr-detail', detailId],
    queryFn: () => api.get(`/api/production-requests/${detailId}`),
    enabled: !!detailId,
  })

  const reqQuery = useQuery({
    queryKey: ['pr-requirements', requirementsId],
    queryFn: () => api.get(`/api/production-requests/${requirementsId}/requirements`),
    enabled: !!requirementsId,
  })

  const createMutation = useMutation({
    mutationFn: (body) => api.post('/api/production-requests', body),
    onSuccess: () => {
      message.success('Tạo yêu cầu sản xuất thành công')
      queryClient.invalidateQueries({ queryKey: ['production-requests'] })
      form.resetFields()
      setCreateModalOpen(false)
    },
    onError: (err) => message.error(err.message || 'Lỗi tạo yêu cầu sản xuất'),
  })

  const cancelMutation = useMutation({
    mutationFn: (id) => api.post(`/api/production-requests/${id}/cancel`),
    onSuccess: () => {
      message.success('Đã hủy yêu cầu sản xuất')
      queryClient.invalidateQueries({ queryKey: ['production-requests'] })
      if (detailId) queryClient.invalidateQueries({ queryKey: ['pr-detail', detailId] })
    },
    onError: (err) => message.error(err.message || 'Không thể hủy yêu cầu sản xuất'),
  })

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 80, sorter: true, render: (v) => `#${v}` },
    { title: 'Sản phẩm', dataIndex: 'productName' },
    {
      title: 'Số lượng',
      dataIndex: 'quantity',
      sorter: true,
      width: 100,
      render: (v, r) => `${v} ${r.productUnit || ''}`,
    },
    {
      title: 'Tiến độ hoàn thành',
      key: 'progress',
      width: 180,
      render: (_, r) => {
        const percent = Math.min(100, Math.round(((r.completedQuantity || 0) / (r.quantity || 1)) * 100))
        return (
          <Space direction="vertical" size={2} style={{ width: '100%' }}>
            <Progress percent={percent} size="small" status={r.status === 'COMPLETED' ? 'success' : 'active'} />
            <span style={{ fontSize: 12, color: '#6b7280' }}>
              Đã xong {r.completedQuantity || 0}/{r.quantity} {r.productUnit || ''}
            </span>
          </Space>
        )
      },
    },
    {
      title: 'BOM',
      dataIndex: 'bomVersion',
      width: 80,
      render: (v) => <Badge count={`v${v}`} style={{ backgroundColor: '#108ee9' }} />,
    },
    { title: 'Nhân viên phụ trách', dataIndex: 'assignedStaffName', width: 160 },
    { title: 'Trạng thái', dataIndex: 'status', sorter: true, width: 120, render: (v) => <DocStatusTag status={v} /> },
    { title: 'Ngày tạo', dataIndex: 'createdAt', sorter: true, width: 150, render: dateTime },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 250,
      render: (_, r) => (
        <Space size="small">
          <Button size="small" icon={<EyeOutlined />} onClick={() => setDetailId(r.id)}>
            Chi tiết
          </Button>
          <Button size="small" icon={<FileSearchOutlined />} onClick={() => setRequirementsId(r.id)}>
            Kiểm tra NVL
          </Button>
          {(r.status === 'PENDING' || r.status === 'IN_PROGRESS') && (
            <ConfirmButton
              danger
              size="small"
              title="Hủy yêu cầu sản xuất này?"
              description="Chỉ có thể hủy khi chưa có phiếu xuất NVL nào được duyệt và chưa có đợt sản xuất đang chạy."
              loading={cancelMutation.isPending}
              onConfirm={() => cancelMutation.mutate(r.id)}
            >
              <CloseOutlined /> Hủy
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
      quantity: values.quantity,
      assignedStaffId: values.assignedStaffId,
      note: values.note?.trim(),
    })
  }

  return (
    <>
      <PageHeader
        title="Yêu cầu sản xuất (Production Requests)"
        subtitle="Tạo và theo dõi tiến độ sản xuất, kiểm tra định mức nguyên vật liệu"
        extra={(
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              form.resetFields()
              setCreateModalOpen(true)
            }}
          >
            Tạo yêu cầu sản xuất
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
              style={{ width: 220 }}
              placeholder="Chọn sản phẩm"
              options={productsQuery.data?.content?.map((p) => ({ value: p.id, label: p.name })) || []}
              value={filters.productId}
              onChange={(v) => { setPage(0); setFilters((f) => ({ ...f, productId: v })) }}
            />
            <Select
              allowClear
              style={{ width: 160 }}
              placeholder="Trạng thái"
              options={productionRequestStatusOptions}
              value={filters.status}
              onChange={(v) => { setPage(0); setFilters((f) => ({ ...f, status: v })) }}
            />
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              style={{ width: 200 }}
              placeholder="Nhân viên phụ trách"
              options={staffQuery.data?.map((s) => ({ value: s.id, label: s.fullName })) || []}
              value={filters.assignedTo}
              onChange={(v) => { setPage(0); setFilters((f) => ({ ...f, assignedTo: v })) }}
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
        data={prQuery.data?.content || []}
        loading={prQuery.isFetching}
        page={page}
        size={PAGE_SIZE}
        total={prQuery.data?.totalElements || 0}
        onPageChange={setPage}
        onTableChange={(_p, _f, sorter) => {
          const next = Array.isArray(sorter) ? sorter[0] : sorter
          setPage(0)
          setSort(next?.order
            ? { sort: next.field, direction: next.order === 'descend' ? 'desc' : 'asc' }
            : { sort: 'id', direction: 'desc' })
        }}
      />

      {/* Modal tạo yêu cầu sản xuất */}
      <Modal
        title="Tạo yêu cầu sản xuất mới"
        open={createModalOpen}
        onCancel={() => setCreateModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending}
        destroyOnHidden
        okText="Tạo yêu cầu"
        cancelText="Hủy"
      >
        <Form form={form} layout="vertical" onFinish={handleCreateSubmit}>
          <Form.Item
            name="productId"
            label="Sản phẩm cần sản xuất"
            rules={[{ required: true, message: 'Vui lòng chọn sản phẩm' }]}
          >
            <Select
              showSearch
              placeholder="Chọn sản phẩm (yêu cầu đã có BOM active)"
              optionFilterProp="label"
              options={productsQuery.data?.content?.map((p) => ({
                value: p.id,
                label: `${p.name} (${p.unit})`,
              })) || []}
            />
          </Form.Item>

          <Form.Item
            name="quantity"
            label="Số lượng sản xuất"
            rules={[
              { required: true, message: 'Vui lòng nhập số lượng' },
              { type: 'number', min: 1, message: 'Số lượng phải lớn hơn 0' },
            ]}
          >
            <InputNumber min={1} step={1} precision={0} style={{ width: '100%' }} placeholder="Số lượng thành phẩm" />
          </Form.Item>

          <Form.Item
            name="assignedStaffId"
            label="Giao cho nhân viên (STAFF)"
            rules={[{ required: true, message: 'Vui lòng chọn nhân viên phụ trách' }]}
          >
            <Select
              showSearch
              placeholder="Chọn nhân viên"
              optionFilterProp="label"
              options={staffQuery.data?.map((s) => ({ value: s.id, label: s.fullName })) || []}
            />
          </Form.Item>

          <Form.Item name="note" label="Ghi chú">
            <Input.TextArea rows={3} maxLength={500} placeholder="Ghi chú yêu cầu sản xuất..." />
          </Form.Item>
        </Form>
      </Modal>

      {/* Modal chi tiết yêu cầu sản xuất */}
      <Modal
        title={`Chi tiết yêu cầu sản xuất #${detailId}`}
        open={!!detailId}
        width={780}
        footer={[
          detailQuery.data?.status === 'PENDING' && (
            <ConfirmButton
              key="cancel"
              danger
              title="Hủy yêu cầu sản xuất này?"
              loading={cancelMutation.isPending}
              onConfirm={() => cancelMutation.mutate(detailId)}
            >
              Hủy yêu cầu sản xuất
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
              <Descriptions.Item label="Trạng thái">
                <DocStatusTag status={detailQuery.data.status} />
              </Descriptions.Item>
              <Descriptions.Item label="Số lượng yêu cầu">
                {detailQuery.data.quantity} {detailQuery.data.productUnit}
              </Descriptions.Item>
              <Descriptions.Item label="BOM áp dụng">
                Phiên bản v{detailQuery.data.bomVersion} (BOM #{detailQuery.data.bomId})
              </Descriptions.Item>
              <Descriptions.Item label="Đã hoàn thành">
                {detailQuery.data.completedQuantity || 0} {detailQuery.data.productUnit}
              </Descriptions.Item>
              <Descriptions.Item label="Đang sản xuất">
                {detailQuery.data.inProgressQuantity || 0} {detailQuery.data.productUnit}
              </Descriptions.Item>
              <Descriptions.Item label="Người tạo">
                {detailQuery.data.warehouseManagerName}
              </Descriptions.Item>
              <Descriptions.Item label="Nhân viên phụ trách">
                {detailQuery.data.assignedStaffName}
              </Descriptions.Item>
              <Descriptions.Item label="Ngày tạo" span={2}>
                {dateTime(detailQuery.data.createdAt)}
              </Descriptions.Item>
              {detailQuery.data.note && (
                <Descriptions.Item label="Ghi chú" span={2}>
                  {detailQuery.data.note}
                </Descriptions.Item>
              )}
            </Descriptions>

            <Divider style={{ margin: '16px 0 12px' }}>Các đợt sản xuất (Productions)</Divider>
            <Table
              size="small"
              rowKey="id"
              pagination={false}
              dataSource={detailQuery.data.productions || []}
              locale={{ emptyText: 'Chưa có đợt sản xuất nào' }}
              columns={[
                { title: 'ID', dataIndex: 'id', width: 70, render: (v) => `#${v}` },
                { title: 'Số lượng', dataIndex: 'quantity', width: 90 },
                { title: 'Trạng thái', dataIndex: 'status', width: 120, render: (v) => <DocStatusTag status={v} /> },
                { title: 'Người thực hiện', dataIndex: 'producedByName', width: 140 },
                { title: 'Bắt đầu', dataIndex: 'startedAt', width: 140, render: dateTime },
                { title: 'Hoàn thành', dataIndex: 'completedAt', width: 140, render: dateTime },
              ]}
            />

            <Divider style={{ margin: '16px 0 12px' }}>Các phiếu xuất nguyên vật liệu</Divider>
            <Table
              size="small"
              rowKey="id"
              pagination={false}
              dataSource={detailQuery.data.issueRequests || []}
              locale={{ emptyText: 'Chưa có phiếu xuất NVL nào' }}
              columns={[
                { title: 'Mã phiếu', dataIndex: 'id', width: 90, render: (v) => `#${v}` },
                { title: 'Người yêu cầu', dataIndex: 'requestedByName', width: 130 },
                { title: 'Số dòng', dataIndex: 'itemCount', width: 80, render: (v) => `${v} NVL` },
                { title: 'Trạng thái', dataIndex: 'status', width: 120, render: (v) => <DocStatusTag status={v} /> },
                { title: 'Ngày yêu cầu', dataIndex: 'requestedAt', width: 140, render: dateTime },
                { title: 'Người duyệt', dataIndex: 'reviewedByName', width: 120 },
              ]}
            />
          </>
        )}
      </Modal>

      {/* Modal kiểm tra nhu cầu NVL (Requirements) */}
      <Modal
        title={`Bảng kiểm tra định mức NVL – Yêu cầu SX #${requirementsId}`}
        open={!!requirementsId}
        width={900}
        footer={[<Button key="close" onClick={() => setRequirementsId(null)}>Đóng</Button>]}
        onCancel={() => setRequirementsId(null)}
        destroyOnHidden
      >
        {reqQuery.data && (
          <>
            <Descriptions bordered size="small" column={3} style={{ marginBottom: 16 }}>
              <Descriptions.Item label="Sản phẩm">{reqQuery.data.productName}</Descriptions.Item>
              <Descriptions.Item label="Số lượng cần">{reqQuery.data.quantity}</Descriptions.Item>
              <Descriptions.Item label="Mã PR">#{reqQuery.data.productionRequestId}</Descriptions.Item>
            </Descriptions>

            <Table
              size="small"
              rowKey="materialId"
              pagination={false}
              dataSource={reqQuery.data.materials || []}
              columns={[
                { title: 'Mã NVL', dataIndex: 'materialCode', width: 90 },
                { title: 'Tên nguyên vật liệu', dataIndex: 'materialName' },
                { title: 'ĐVT', dataIndex: 'unit', width: 60 },
                { title: 'Định mức/SP', dataIndex: 'bomQuantity', width: 90, render: (v) => quantity(v) },
                {
                  title: 'Tổng cần',
                  dataIndex: 'totalRequired',
                  width: 90,
                  render: (v) => <Typography.Text strong>{quantity(v)}</Typography.Text>,
                },
                { title: 'Đã duyệt xuất', dataIndex: 'approvedQuantity', width: 100, render: (v) => quantity(v) },
                { title: 'Đang xin duyệt', dataIndex: 'pendingQuantity', width: 100, render: (v) => quantity(v) },
                {
                  title: 'Còn được xin',
                  dataIndex: 'remainingAllowed',
                  width: 100,
                  render: (v) => <Typography.Text type={v > 0 ? 'success' : 'secondary'}>{quantity(v)}</Typography.Text>,
                },
                { title: 'Tồn kho', dataIndex: 'onHand', width: 90, render: (v) => quantity(v) },
                {
                  title: 'Thiếu hụt',
                  dataIndex: 'shortage',
                  width: 100,
                  render: (v) => {
                    const num = Number(v) || 0
                    return num > 0 ? <Tag color="red">Thiếu: {quantity(num)}</Tag> : <Tag color="green">Đủ</Tag>
                  },
                },
              ]}
            />
          </>
        )}
      </Modal>
    </>
  )
}

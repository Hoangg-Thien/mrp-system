import { useEffect, useState } from 'react'
import {
  Button,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Tag,
  Typography,
  message,
} from 'antd'
import {
  CheckOutlined,
  CloseOutlined,
  EyeOutlined,
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
import { dateTime } from '../../utils/format'
import { productionStatusOptions } from '../../utils/statusMap'

const PAGE_SIZE = 15

export default function StaffProductionsPage() {
  const location = useLocation()
  const queryClient = useQueryClient()
  const [filters, setFilters] = useState({})
  const [page, setPage] = useState(0)
  const [sort, setSort] = useState({ sort: 'id', direction: 'desc' })

  const [createModalOpen, setCreateModalOpen] = useState(() => Boolean(location.state?.prefillPrId))
  const [detailId, setDetailId] = useState(null)
  const [form] = Form.useForm()

  // Handle prefill from PR page
  useEffect(() => {
    if (location.state?.prefillPrId) {
      form.setFieldsValue({ productionRequestId: location.state.prefillPrId, quantity: 1 })
    }
  }, [location.state, form])

  const prodQuery = usePagedQuery(
    'staff-productions-list',
    '/api/productions',
    { ...filters, ...sort, page, size: PAGE_SIZE },
  )

  const staffPrsQuery = useQuery({
    queryKey: ['staff-prs-for-prod-select'],
    queryFn: () => api.get('/api/production-requests', { size: 100 }),
  })

  const detailQuery = useQuery({
    queryKey: ['staff-prod-detail', detailId],
    queryFn: () => api.get(`/api/productions/${detailId}`),
    enabled: !!detailId,
  })

  const createMutation = useMutation({
    mutationFn: (body) => api.post('/api/productions', body),
    onSuccess: () => {
      message.success('Bắt đầu đợt sản xuất mới thành công (trạng thái Đang thực hiện)')
      queryClient.invalidateQueries({ queryKey: ['staff-productions-list'] })
      queryClient.invalidateQueries({ queryKey: ['staff-production-requests'] })
      form.resetFields()
      setCreateModalOpen(false)
    },
    onError: (err) => message.error(err.message || 'Lỗi bắt đầu đợt sản xuất'),
  })

  const completeMutation = useMutation({
    mutationFn: (id) => api.post(`/api/productions/${id}/complete`),
    onSuccess: () => {
      message.success('Hoàn tất đợt sản xuất thành công! Thành phẩm đã được nhập vào kho.')
      queryClient.invalidateQueries({ queryKey: ['staff-productions-list'] })
      queryClient.invalidateQueries({ queryKey: ['staff-production-requests'] })
      queryClient.invalidateQueries({ queryKey: ['inv-products'] })
      queryClient.invalidateQueries({ queryKey: ['stock-movements'] })
      if (detailId) queryClient.invalidateQueries({ queryKey: ['staff-prod-detail', detailId] })
    },
    onError: (err) => message.error(err.message || 'Không thể hoàn tất đợt sản xuất. Kiểm tra xem NVL đã xuất đủ theo BOM chưa!'),
  })

  const cancelMutation = useMutation({
    mutationFn: (id) => api.post(`/api/productions/${id}/cancel`),
    onSuccess: () => {
      message.success('Đã hủy đợt sản xuất')
      queryClient.invalidateQueries({ queryKey: ['staff-productions-list'] })
      if (detailId) queryClient.invalidateQueries({ queryKey: ['staff-prod-detail', detailId] })
    },
    onError: (err) => message.error(err.message || 'Không thể hủy đợt sản xuất'),
  })

  const columns = [
    { title: 'Mã đợt', dataIndex: 'id', width: 90, sorter: true, render: (v) => `#${v}` },
    {
      title: 'Yêu cầu SX',
      dataIndex: 'productionRequestId',
      width: 120,
      render: (v) => <Tag color="blue">PR #{v}</Tag>,
    },
    {
      title: 'Số lượng sản xuất',
      dataIndex: 'quantity',
      sorter: true,
      width: 140,
      render: (v) => <Typography.Text strong>{v} sản phẩm</Typography.Text>,
    },
    { title: 'Trạng thái', dataIndex: 'status', sorter: true, width: 140, render: (v) => <DocStatusTag status={v} /> },
    { title: 'Thời gian bắt đầu', dataIndex: 'startedAt', sorter: true, width: 160, render: dateTime },
    { title: 'Thời gian hoàn tất', dataIndex: 'completedAt', sorter: true, width: 160, render: dateTime },
    { title: 'Ghi chú', dataIndex: 'note', ellipsis: true },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 230,
      render: (_, r) => (
        <Space size="small">
          <Button size="small" icon={<EyeOutlined />} onClick={() => setDetailId(r.id)}>
            Chi tiết
          </Button>
          {r.status === 'IN_PROGRESS' && (
            <>
              <ConfirmButton
                type="primary"
                size="small"
                title="Xác nhận hoàn tất đợt sản xuất này?"
                description="Hệ thống sẽ kiểm tra NVL đã xuất đủ theo BOM và ghi tăng thành phẩm vào kho."
                loading={completeMutation.isPending}
                onConfirm={() => completeMutation.mutate(r.id)}
              >
                <CheckOutlined /> Hoàn tất
              </ConfirmButton>
              <ConfirmButton
                danger
                size="small"
                title="Hủy đợt sản xuất này?"
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
      productionRequestId: values.productionRequestId,
      quantity: values.quantity,
      note: values.note?.trim(),
    })
  }

  const activePrOptions = staffPrsQuery.data?.content
    ?.filter((pr) => pr.status === 'PENDING' || pr.status === 'IN_PROGRESS')
    ?.map((pr) => {
      const remaining = (pr.quantity || 0) - (pr.completedQuantity || 0)
      return {
        value: pr.id,
        label: `PR #${pr.id} – ${pr.productName} (còn cần: ${remaining}/${pr.quantity} ${pr.productUnit || ''})`,
      }
    }) || []

  return (
    <>
      <PageHeader
        title="Thực hiện sản xuất (Productions)"
        subtitle="Quản lý các đợt sản xuất thực tế, báo cáo hoàn tất và nhập kho thành phẩm"
        extra={(
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              form.resetFields()
              setCreateModalOpen(true)
            }}
          >
            Bắt đầu đợt sản xuất mới
          </Button>
        )}
      />

      <PagedTable
        filters={(
          <Space wrap>
            <Select
              allowClear
              style={{ width: 180 }}
              placeholder="Trạng thái"
              options={productionStatusOptions}
              value={filters.status}
              onChange={(v) => { setPage(0); setFilters((f) => ({ ...f, status: v })) }}
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
        data={prodQuery.data?.content || []}
        loading={prodQuery.isFetching}
        page={page}
        size={PAGE_SIZE}
        total={prodQuery.data?.totalElements || 0}
        onPageChange={setPage}
        onTableChange={(_p, _f, sorter) => {
          const next = Array.isArray(sorter) ? sorter[0] : sorter
          setPage(0)
          setSort(next?.order
            ? { sort: next.field, direction: next.order === 'descend' ? 'desc' : 'asc' }
            : { sort: 'id', direction: 'desc' })
        }}
      />

      {/* Modal bắt đầu đợt sản xuất */}
      <Modal
        title="Bắt đầu đợt sản xuất mới"
        open={createModalOpen}
        onCancel={() => setCreateModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending}
        destroyOnHidden
        okText="Bắt đầu sản xuất"
        cancelText="Hủy"
      >
        <Form form={form} layout="vertical" onFinish={handleCreateSubmit}>
          <Form.Item
            name="productionRequestId"
            label="Yêu cầu sản xuất"
            rules={[{ required: true, message: 'Vui lòng chọn yêu cầu sản xuất' }]}
          >
            <Select
              showSearch
              placeholder="Chọn yêu cầu sản xuất"
              optionFilterProp="label"
              options={activePrOptions}
            />
          </Form.Item>

          <Form.Item
            name="quantity"
            label="Số lượng sản xuất đợt này"
            rules={[
              { required: true, message: 'Vui lòng nhập số lượng' },
              { type: 'number', min: 1, message: 'Số lượng phải lớn hơn 0' },
            ]}
          >
            <InputNumber min={1} step={1} precision={0} style={{ width: '100%' }} placeholder="Số lượng thành phẩm" />
          </Form.Item>

          <Form.Item name="note" label="Ghi chú đợt sản xuất">
            <Input.TextArea rows={3} maxLength={500} placeholder="Ví dụ: Đợt 1 phân xưởng A..." />
          </Form.Item>
        </Form>
      </Modal>

      {/* Modal chi tiết đợt sản xuất */}
      <Modal
        title={`Chi tiết đợt sản xuất #${detailId}`}
        open={!!detailId}
        footer={[
          detailQuery.data?.status === 'IN_PROGRESS' && (
            <ConfirmButton
              key="complete"
              type="primary"
              title="Xác nhận hoàn tất đợt sản xuất này?"
              description="Hệ thống sẽ kiểm tra NVL xuất đủ theo BOM và ghi tăng thành phẩm vào kho."
              loading={completeMutation.isPending}
              onConfirm={() => completeMutation.mutate(detailId)}
            >
              Hoàn tất sản xuất
            </ConfirmButton>
          ),
          detailQuery.data?.status === 'IN_PROGRESS' && (
            <ConfirmButton
              key="cancel"
              danger
              title="Hủy đợt sản xuất này?"
              loading={cancelMutation.isPending}
              onConfirm={() => cancelMutation.mutate(detailId)}
            >
              Hủy đợt SX
            </ConfirmButton>
          ),
          <Button key="close" onClick={() => setDetailId(null)}>Đóng</Button>,
        ]}
        onCancel={() => setDetailId(null)}
        destroyOnHidden
      >
        {detailQuery.data && (
          <Descriptions bordered size="small" column={2}>
            <Descriptions.Item label="Mã đợt SX">#{detailQuery.data.id}</Descriptions.Item>
            <Descriptions.Item label="Trạng thái">
              <DocStatusTag status={detailQuery.data.status} />
            </Descriptions.Item>
            <Descriptions.Item label="Yêu cầu SX liên quan">
              <Tag color="blue">PR #{detailQuery.data.productionRequestId}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Số lượng sản xuất">
              {detailQuery.data.quantity} sản phẩm
            </Descriptions.Item>
            <Descriptions.Item label="Người thực hiện">
              {detailQuery.data.producedByName}
            </Descriptions.Item>
            <Descriptions.Item label="Bắt đầu">
              {dateTime(detailQuery.data.startedAt)}
            </Descriptions.Item>
            <Descriptions.Item label="Hoàn tất" span={2}>
              {dateTime(detailQuery.data.completedAt)}
            </Descriptions.Item>
            {detailQuery.data.note && (
              <Descriptions.Item label="Ghi chú" span={2}>
                {detailQuery.data.note}
              </Descriptions.Item>
            )}
          </Descriptions>
        )}
      </Modal>
    </>
  )
}

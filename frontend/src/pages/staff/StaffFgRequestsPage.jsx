import { useSingleMutation as useMutation } from '../../api/useSingleMutation'
import { useAllPages } from '../../api/useAllPages'
import QueryError from '../../components/QueryError'
import { useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Divider,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Typography,
  App,
} from 'antd'
import {
  CloseOutlined,
  DeleteOutlined,
  EyeOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import { usePagedQuery } from '../../api/hooks'
import ConfirmButton from '../../components/ConfirmButton'
import PageHeader from '../../components/PageHeader'
import PagedTable from '../../components/PagedTable'
import { DocStatusTag } from '../../components/StatusTag'
import { dateTime, quantity } from '../../utils/format'
import { issueStatusOptions } from '../../utils/statusMap'

const PAGE_SIZE = 15

export default function StaffFgRequestsPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const [filters, setFilters] = useState({})
  const [page, setPage] = useState(0)
  const [sort, setSort] = useState({ sort: 'id', direction: 'desc' })

  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [detailId, setDetailId] = useState(null)
  const [form] = Form.useForm()

  const fgQuery = usePagedQuery(
    'staff-fg-requests-list',
    '/api/fg-issue-requests',
    { ...filters, ...sort, page, size: PAGE_SIZE },
  )

  const productsQuery = useAllPages({
    queryKey: ['products-for-staff-fg-select'],
    path: '/api/products',
    params: {  },
  })

  const detailQuery = useQuery({
    queryKey: ['staff-fg-detail', detailId],
    queryFn: () => api.get(`/api/fg-issue-requests/${detailId}`),
    enabled: !!detailId,
  })

  const createMutation = useMutation({
    mutationFn: (body) => api.post('/api/fg-issue-requests', body),
    onSuccess: (data) => {
      if (data.hasStockShortage) {
        message.warning(`Đã tạo phiếu #${data.id} (lưu ý: tồn kho thành phẩm hiện tại chưa đủ cho một số mặt hàng)`)
      } else {
        message.success('Tạo phiếu đề nghị xuất bán thành phẩm thành công')
      }
      queryClient.invalidateQueries({ queryKey: ['staff-fg-requests-list'] })
      form.resetFields()
      setCreateModalOpen(false)
    },
    onError: (err) => message.error(err.message || 'Lỗi tạo phiếu xuất thành phẩm'),
  })

  const cancelMutation = useMutation({
    mutationFn: (id) => api.post(`/api/fg-issue-requests/${id}/cancel`),
    onSuccess: () => {
      message.success('Đã hủy phiếu đề nghị xuất thành phẩm')
      queryClient.invalidateQueries({ queryKey: ['staff-fg-requests-list'] })
      if (detailId) queryClient.invalidateQueries({ queryKey: ['staff-fg-detail', detailId] })
    },
    onError: (err) => message.error(err.message || 'Không thể hủy phiếu xuất thành phẩm'),
  })

  const columns = [
    { title: 'Mã phiếu', dataIndex: 'id', width: 90, sorter: true, render: (v) => `#${v}` },
    {
      title: 'Số mặt hàng',
      dataIndex: 'itemCount',
      width: 110,
      render: (v) => `${v} sản phẩm`,
    },
    {
      title: 'Tổng số lượng',
      dataIndex: 'totalQuantity',
      width: 120,
      render: (v) => <Typography.Text strong>{v}</Typography.Text>,
    },
    { title: 'Trạng thái', dataIndex: 'status', sorter: true, width: 120, render: (v) => <DocStatusTag status={v} /> },
    { title: 'Thời gian đề xuất', dataIndex: 'requestedAt', sorter: true, width: 150, render: dateTime },
    { title: 'Người duyệt', dataIndex: 'reviewedByName', width: 140 },
    { title: 'Thời gian duyệt', dataIndex: 'reviewedAt', width: 150, render: dateTime },
    { title: 'Ghi chú', dataIndex: 'note', ellipsis: true },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 180,
      render: (_, r) => (
        <Space size="small">
          <Button size="small" icon={<EyeOutlined />} onClick={() => setDetailId(r.id)}>
            Chi tiết
          </Button>
          {r.status === 'PENDING' && (
            <ConfirmButton disabled={cancelMutation.isPending}
              danger
              size="small"
              title="Hủy phiếu đề xuất xuất bán này?"
              loading={cancelMutation.isPending}
              onConfirm={() => cancelMutation.mutateAsync(r.id)}
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
      items: values.items.map((it) => ({
        productId: it.productId,
        quantity: it.quantity,
      })),
      note: values.note?.trim(),
    })
  }

  return (
    <>
      <QueryError query={productsQuery} />
      <PageHeader
        title="Đề nghị xuất bán thành phẩm"
        subtitle="Lập phiếu xin xuất kho thành phẩm phục vụ các đơn hàng bán cho khách"
        extra={(
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              form.setFieldsValue({ items: [{ productId: undefined, quantity: 1 }] })
              setCreateModalOpen(true)
            }}
          >
            Lập đề nghị xuất bán mới
          </Button>
        )}
      />

      <PagedTable query={fgQuery}
        filters={(
          <Space wrap>
            <Select
              allowClear
              style={{ width: 160 }}
              placeholder="Trạng thái"
              options={issueStatusOptions}
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
        data={fgQuery.data?.content || []}
        loading={fgQuery.isFetching}
        page={page}
        size={PAGE_SIZE}
        total={fgQuery.data?.totalElements || 0}
        onPageChange={setPage}
        onTableChange={(_p, _f, sorter) => {
          const next = Array.isArray(sorter) ? sorter[0] : sorter
          setPage(0)
          setSort(next?.order
            ? { sort: next.field, direction: next.order === 'descend' ? 'desc' : 'asc' }
            : { sort: 'id', direction: 'desc' })
        }}
      />

      {/* Modal lập phiếu xuất bán TP */}
      <Modal forceRender cancelButtonProps={{ disabled: createMutation.isPending }} closable={!(createMutation.isPending)} mask={{ closable: !(createMutation.isPending) }} okButtonProps={{ disabled: createMutation.isPending }}
        title="Lập phiếu đề nghị xuất bán thành phẩm"
        open={createModalOpen}
        width={720}
        onCancel={() => setCreateModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending}
        destroyOnHidden
        okText="Gửi đề nghị (PENDING)"
        cancelText="Hủy"
      >
        <Form form={form} layout="vertical" onFinish={handleCreateSubmit}>
          <Typography.Title level={5}>Danh sách thành phẩm xuất bán</Typography.Title>

          <Form.List
            name="items"
            rules={[
              {
                validator: async (_, items) => {
                  if (!items || items.length === 0) {
                    return Promise.reject(new Error('Vui lòng thêm ít nhất 1 sản phẩm'))
                  }
                  const ids = items.map((it) => it?.productId).filter(Boolean)
                  if (new Set(ids).size !== ids.length) {
                    return Promise.reject(new Error('Không được chọn trùng sản phẩm trong cùng một phiếu'))
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
                        name={[name, 'productId']}
                        label="Sản phẩm"
                        rules={[{ required: true, message: 'Chọn sản phẩm' }]}
                        style={{ minWidth: 320 }}
                      >
                        <Select
                          showSearch
                          placeholder="Chọn thành phẩm"
                          optionFilterProp="label"
                          options={productsQuery.data?.content?.map((p) => ({
                            value: p.id,
                            label: `${p.name} (${p.unit})`,
                          })) || []}
                        />
                      </Form.Item>

                      <Form.Item
                        {...restField}
                        name={[name, 'quantity']}
                        label="Số lượng xuất"
                        rules={[
                          { required: true, message: 'Nhập số lượng' },
                          { type: 'number', min: 1, message: '≥ 1' },
                        ]}
                      >
                        <InputNumber min={1} step={1} precision={0} style={{ width: 160 }} />
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
                  onClick={() => add({ productId: undefined, quantity: 1 })}
                  block
                  icon={<PlusOutlined />}
                >
                  Thêm sản phẩm
                </Button>
              </>
            )}
          </Form.List>

          <Form.Item
            name="note"
            label="Thông tin khách hàng / Mã đơn hàng / Ghi chú"
            rules={[{ required: true, message: 'Vui lòng nhập thông tin khách hàng hoặc đơn hàng' }]}
            style={{ marginTop: 16 }}
          >
            <Input.TextArea
              rows={3}
              maxLength={500}
              placeholder="Ví dụ: Xuất cho khách hàng Công ty TNHH ABC theo Hợp đồng số 12..."
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* Modal chi tiết phiếu xuất thành phẩm */}
      <Modal cancelButtonProps={{ disabled: cancelMutation.isPending }} closable={!(cancelMutation.isPending)} mask={{ closable: !(cancelMutation.isPending) }} okButtonProps={{ disabled: cancelMutation.isPending }}
        title={`Chi tiết phiếu xuất thành phẩm #${detailId}`}
        open={!!detailId}
        width={720}
        footer={[
          detailQuery.data?.status === 'PENDING' && (
            <ConfirmButton disabled={cancelMutation.isPending}
              key="cancel"
              danger
              title="Hủy phiếu đề xuất xuất này?"
              loading={cancelMutation.isPending}
              onConfirm={() => cancelMutation.mutateAsync(detailId)}
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
              <Descriptions.Item label="Thời gian đề xuất">
                {dateTime(detailQuery.data.requestedAt)}
              </Descriptions.Item>
              <Descriptions.Item label="Người duyệt">
                {detailQuery.data.reviewedByName || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Thời gian duyệt">
                {dateTime(detailQuery.data.reviewedAt)}
              </Descriptions.Item>
              <Descriptions.Item label="Ghi chú người mua / Phản hồi" span={2}>
                {detailQuery.data.note || '—'}
              </Descriptions.Item>
            </Descriptions>

            {detailQuery.data.hasStockShortage && (
              <Alert
                type="warning"
                showIcon
                style={{ marginTop: 16 }}
                title="Cảnh báo tồn kho"
                description={detailQuery.data.warning || 'Kho hiện tại không đủ tồn cho một số sản phẩm trong phiếu.'}
              />
            )}

            <Divider style={{ margin: '16px 0 12px' }}>Chi tiết sản phẩm</Divider>

            <Table
              size="small"
              rowKey="id"
              pagination={false}
              dataSource={detailQuery.data.items || []}
              columns={[
                { title: 'Tên thành phẩm', dataIndex: 'productName' },
                { title: 'ĐVT', dataIndex: 'productUnit', width: 70 },
                {
                  title: 'Số lượng xin xuất',
                  dataIndex: 'quantity',
                  width: 140,
                  render: (v) => <Typography.Text strong>{v}</Typography.Text>,
                },
                {
                  title: 'Tồn kho hiện tại',
                  dataIndex: 'currentStock',
                  width: 140,
                  render: (v) => quantity(v, 0),
                },
              ]}
            />
          </>
        )}
      </Modal>
    </>
  )
}


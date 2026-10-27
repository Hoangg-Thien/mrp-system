import { useEffect, useState } from 'react'
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
  Tag,
  Typography,
  message,
} from 'antd'
import {
  CloseOutlined,
  DeleteOutlined,
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
import { dateTime, quantity } from '../../utils/format'
import { issueStatusOptions } from '../../utils/statusMap'

const PAGE_SIZE = 15

export default function StaffMaterialRequestsPage() {
  const location = useLocation()
  const queryClient = useQueryClient()
  const [filters, setFilters] = useState({})
  const [page, setPage] = useState(0)
  const [sort, setSort] = useState({ sort: 'id', direction: 'desc' })

  const [createModalOpen, setCreateModalOpen] = useState(() => Boolean(location.state?.prefillPrId))
  const [selectedPrId, setSelectedPrId] = useState(() => location.state?.prefillPrId || null)
  const [detailId, setDetailId] = useState(null)
  const [form] = Form.useForm()

  // Handle prefill from PR page
  useEffect(() => {
    if (location.state?.prefillPrId) {
      form.setFieldsValue({ productionRequestId: location.state.prefillPrId })
    }
  }, [location.state, form])

  const mirQuery = usePagedQuery(
    'staff-mir-list',
    '/api/material-issue-requests',
    { ...filters, ...sort, page, size: PAGE_SIZE },
  )

  // Assigned PRs for staff
  const staffPrsQuery = useQuery({
    queryKey: ['staff-prs-for-mir-select'],
    queryFn: () => api.get('/api/production-requests', { size: 100 }),
  })

  // Requirements of selected PR
  const prRequirementsQuery = useQuery({
    queryKey: ['pr-requirements-for-mir-form', selectedPrId],
    queryFn: () => api.get(`/api/production-requests/${selectedPrId}/requirements`),
    enabled: !!selectedPrId,
  })

  const detailQuery = useQuery({
    queryKey: ['staff-mir-detail', detailId],
    queryFn: () => api.get(`/api/material-issue-requests/${detailId}`),
    enabled: !!detailId,
  })

  const createMutation = useMutation({
    mutationFn: (body) => api.post('/api/material-issue-requests', body),
    onSuccess: (data) => {
      if (data.hasStockShortage) {
        message.warning(`Đã tạo phiếu #${data.id} (lưu ý: kho hiện tại đang thiếu một số NVL)`)
      } else {
        message.success('Tạo phiếu đề nghị xuất NVL thành công')
      }
      queryClient.invalidateQueries({ queryKey: ['staff-mir-list'] })
      form.resetFields()
      setSelectedPrId(null)
      setCreateModalOpen(false)
    },
    onError: (err) => message.error(err.message || 'Lỗi tạo phiếu đề nghị xuất NVL'),
  })

  const cancelMutation = useMutation({
    mutationFn: (id) => api.post(`/api/material-issue-requests/${id}/cancel`),
    onSuccess: () => {
      message.success('Đã hủy phiếu đề nghị xuất NVL')
      queryClient.invalidateQueries({ queryKey: ['staff-mir-list'] })
      if (detailId) queryClient.invalidateQueries({ queryKey: ['staff-mir-detail', detailId] })
    },
    onError: (err) => message.error(err.message || 'Không thể hủy phiếu xuất NVL'),
  })

  const columns = [
    { title: 'Mã phiếu', dataIndex: 'id', width: 90, sorter: true, render: (v) => `#${v}` },
    {
      title: 'Yêu cầu SX',
      dataIndex: 'productionRequestId',
      sorter: true,
      width: 120,
      render: (v) => <Tag className="erp-tag erp-tag-neutral">PR #{v}</Tag>,
    },
    {
      title: 'Số dòng NVL',
      dataIndex: 'itemCount',
      width: 110,
      render: (v) => `${v} loại`,
    },
    { title: 'Trạng thái', dataIndex: 'status', sorter: true, width: 120, render: (v) => <DocStatusTag status={v} /> },
    { title: 'Ngày yêu cầu', dataIndex: 'requestedAt', sorter: true, width: 150, render: dateTime },
    { title: 'Người duyệt', dataIndex: 'reviewedByName', width: 140 },
    { title: 'Ngày duyệt', dataIndex: 'reviewedAt', width: 150, render: dateTime },
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
            <ConfirmButton
              danger
              size="small"
              title="Hủy phiếu đề nghị xuất này?"
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
      productionRequestId: values.productionRequestId,
      items: values.items.map((it) => ({
        materialId: it.materialId,
        quantity: it.quantity,
      })),
      note: values.note?.trim(),
    })
  }

  const activePrOptions = staffPrsQuery.data?.content
    ?.filter((pr) => pr.status === 'PENDING' || pr.status === 'IN_PROGRESS')
    ?.map((pr) => ({
      value: pr.id,
      label: `PR #${pr.id} – ${pr.productName} (giao ${pr.quantity} ${pr.productUnit || ''})`,
    })) || []

  return (
    <>
      <PageHeader
        title="Phiếu đề nghị xuất nguyên vật liệu"
        subtitle="Lập phiếu xin xuất NVL từ kho để phục vụ các yêu cầu sản xuất được giao"
        extra={(
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              form.setFieldsValue({ items: [{ materialId: undefined, quantity: 1 }] })
              setCreateModalOpen(true)
            }}
          >
            Lập đề nghị xuất NVL mới
          </Button>
        )}
      />

      <PagedTable
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
        data={mirQuery.data?.content || []}
        loading={mirQuery.isFetching}
        page={page}
        size={PAGE_SIZE}
        total={mirQuery.data?.totalElements || 0}
        onPageChange={setPage}
        onTableChange={(_p, _f, sorter) => {
          const next = Array.isArray(sorter) ? sorter[0] : sorter
          setPage(0)
          setSort(next?.order
            ? { sort: next.field, direction: next.order === 'descend' ? 'desc' : 'asc' }
            : { sort: 'id', direction: 'desc' })
        }}
      />

      {/* Modal lập phiếu xuất NVL */}
      <Modal
        title="Lập phiếu đề nghị xuất nguyên vật liệu"
        open={createModalOpen}
        width={760}
        onCancel={() => {
          setCreateModalOpen(false)
          setSelectedPrId(null)
        }}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending}
        destroyOnHidden
        okText="Gửi đề nghị (PENDING)"
        cancelText="Hủy"
      >
        <Form form={form} layout="vertical" onFinish={handleCreateSubmit}>
          <Form.Item
            name="productionRequestId"
            label="Chọn yêu cầu sản xuất"
            rules={[{ required: true, message: 'Vui lòng chọn yêu cầu sản xuất' }]}
          >
            <Select
              showSearch
              placeholder="Chọn yêu cầu sản xuất cần xuất NVL"
              optionFilterProp="label"
              options={activePrOptions}
              onChange={(prId) => {
                setSelectedPrId(prId)
                form.setFieldValue('items', [{ materialId: undefined, quantity: 1 }])
              }}
            />
          </Form.Item>

          {prRequirementsQuery.data && (
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message={`Định mức NVL cho: ${prRequirementsQuery.data.productName} (SL: ${prRequirementsQuery.data.quantity})`}
              description={(
                <div style={{ marginTop: 6 }}>
                  {prRequirementsQuery.data.materials?.map((m) => (
                    <Tag key={m.materialId} className={`erp-tag ${m.remainingAllowed > 0 ? 'erp-tag-progress' : 'erp-tag-neutral'}`} style={{ marginBottom: 4 }}>
                      {m.materialName}: còn được xin {quantity(m.remainingAllowed)} {m.unit} (tồn kho: {quantity(m.onHand)})
                    </Tag>
                  ))}
                </div>
              )}
            />
          )}

          <Typography.Title level={5}>Danh sách dòng nguyên vật liệu xin xuất</Typography.Title>

          <Form.List
            name="items"
            rules={[
              {
                validator: async (_, items) => {
                  if (!items || items.length === 0) {
                    return Promise.reject(new Error('Vui lòng chọn ít nhất 1 dòng nguyên vật liệu'))
                  }
                  const ids = items.map((it) => it?.materialId).filter(Boolean)
                  if (new Set(ids).size !== ids.length) {
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
                        style={{ minWidth: 320 }}
                      >
                        <Select
                          showSearch
                          placeholder="Chọn nguyên vật liệu trong BOM"
                          optionFilterProp="label"
                          options={
                            prRequirementsQuery.data?.materials?.map((m) => ({
                              value: m.materialId,
                              label: `[${m.materialCode}] ${m.materialName} (còn xin: ${quantity(m.remainingAllowed)} ${m.unit})`,
                            })) || []
                          }
                        />
                      </Form.Item>

                      <Form.Item
                        {...restField}
                        name={[name, 'quantity']}
                        label="Số lượng xin xuất"
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

          <Form.Item name="note" label="Ghi chú" style={{ marginTop: 16 }}>
            <Input.TextArea rows={2} maxLength={500} placeholder="Ghi chú lý do xuất hoặc đợt xuất..." />
          </Form.Item>
        </Form>
      </Modal>

      {/* Modal chi tiết phiếu xuất NVL */}
      <Modal
        title={`Chi tiết phiếu xuất NVL #${detailId}`}
        open={!!detailId}
        width={760}
        footer={[
          detailQuery.data?.status === 'PENDING' && (
            <ConfirmButton
              key="cancel"
              danger
              title="Xác nhận hủy phiếu đề xuất xuất này?"
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
              <Descriptions.Item label="Yêu cầu SX liên quan">
                <Tag className="erp-tag erp-tag-neutral">PR #{detailQuery.data.productionRequestId}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Người yêu cầu">
                {detailQuery.data.requestedByName}
              </Descriptions.Item>
              <Descriptions.Item label="Thời gian tạo">
                {dateTime(detailQuery.data.requestedAt)}
              </Descriptions.Item>
              <Descriptions.Item label="Người duyệt">
                {detailQuery.data.reviewedByName || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Thời gian duyệt">
                {dateTime(detailQuery.data.reviewedAt)}
              </Descriptions.Item>
              <Descriptions.Item label="Ghi chú / Phản hồi" span={2}>
                {detailQuery.data.note || '—'}
              </Descriptions.Item>
            </Descriptions>

            {detailQuery.data.hasStockShortage && (
              <Alert
                type="warning"
                showIcon
                style={{ marginTop: 16 }}
                message="Cảnh báo tồn kho"
                description={detailQuery.data.warning || 'Kho hiện tại không đủ tồn cho một số NVL trong phiếu này.'}
              />
            )}

            <Divider style={{ margin: '16px 0 12px' }}>Chi tiết nguyên vật liệu</Divider>

            <Table
              size="small"
              rowKey="id"
              pagination={false}
              dataSource={detailQuery.data.items || []}
              columns={[
                { title: 'Mã NVL', dataIndex: 'materialCode', width: 100 },
                { title: 'Tên nguyên vật liệu', dataIndex: 'materialName' },
                { title: 'ĐVT', dataIndex: 'unit', width: 70 },
                {
                  title: 'Số lượng xin xuất',
                  dataIndex: 'quantity',
                  width: 140,
                  render: (v) => <Typography.Text strong>{quantity(v)}</Typography.Text>,
                },
                {
                  title: 'Tồn kho hiện tại',
                  dataIndex: 'currentStock',
                  width: 140,
                  render: (v) => quantity(v),
                },
              ]}
            />
          </>
        )}
      </Modal>
    </>
  )
}

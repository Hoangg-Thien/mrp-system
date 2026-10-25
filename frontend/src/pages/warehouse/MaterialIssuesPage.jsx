import { useState } from 'react'
import {
  Alert,
  Button,
  Descriptions,
  Divider,
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
  EyeOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import { usePagedQuery } from '../../api/hooks'
import ConfirmButton from '../../components/ConfirmButton'
import PageHeader from '../../components/PageHeader'
import PagedTable from '../../components/PagedTable'
import RejectModal from '../../components/RejectModal'
import { DocStatusTag } from '../../components/StatusTag'
import { dateTime, quantity } from '../../utils/format'
import { issueStatusOptions } from '../../utils/statusMap'

const PAGE_SIZE = 15

export default function MaterialIssuesPage() {
  const queryClient = useQueryClient()
  const [filters, setFilters] = useState({})
  const [page, setPage] = useState(0)
  const [sort, setSort] = useState({ sort: 'id', direction: 'desc' })

  const [detailId, setDetailId] = useState(null)
  const [rejectId, setRejectId] = useState(null)

  const staffQuery = useQuery({
    queryKey: ['staff-for-mir-filter'],
    queryFn: () => api.get('/api/staff'),
  })

  const mirQuery = usePagedQuery(
    'material-issue-requests',
    '/api/material-issue-requests',
    { ...filters, ...sort, page, size: PAGE_SIZE },
  )

  const detailQuery = useQuery({
    queryKey: ['mir-detail', detailId],
    queryFn: () => api.get(`/api/material-issue-requests/${detailId}`),
    enabled: !!detailId,
  })

  const approveMutation = useMutation({
    mutationFn: (id) => api.post(`/api/material-issue-requests/${id}/approve`),
    onSuccess: () => {
      message.success('Đã duyệt phiếu xuất NVL và trừ tồn kho thành công!')
      queryClient.invalidateQueries({ queryKey: ['material-issue-requests'] })
      queryClient.invalidateQueries({ queryKey: ['inv-materials'] })
      queryClient.invalidateQueries({ queryKey: ['stock-movements'] })
      if (detailId) queryClient.invalidateQueries({ queryKey: ['mir-detail', detailId] })
    },
    onError: (err) => message.error(err.message || 'Lỗi duyệt phiếu xuất NVL'),
  })

  const rejectMutation = useMutation({
    mutationFn: ({ id, reason }) => api.post(`/api/material-issue-requests/${id}/reject`, { reason }),
    onSuccess: () => {
      message.success('Đã từ chối phiếu xuất NVL')
      queryClient.invalidateQueries({ queryKey: ['material-issue-requests'] })
      if (detailId) queryClient.invalidateQueries({ queryKey: ['mir-detail', detailId] })
      setRejectId(null)
    },
    onError: (err) => message.error(err.message || 'Lỗi từ chối phiếu xuất'),
  })

  const columns = [
    { title: 'Mã phiếu', dataIndex: 'id', width: 90, sorter: true, render: (v) => `#${v}` },
    {
      title: 'Yêu cầu SX',
      dataIndex: 'productionRequestId',
      sorter: true,
      width: 110,
      render: (v) => <Tag color="blue">PR #{v}</Tag>,
    },
    { title: 'Nhân viên yêu cầu', dataIndex: 'requestedByName', width: 150 },
    {
      title: 'Số dòng NVL',
      dataIndex: 'itemCount',
      width: 100,
      render: (v) => `${v} loại`,
    },
    { title: 'Trạng thái', dataIndex: 'status', sorter: true, width: 120, render: (v) => <DocStatusTag status={v} /> },
    { title: 'Ngày yêu cầu', dataIndex: 'requestedAt', sorter: true, width: 150, render: dateTime },
    { title: 'Người duyệt', dataIndex: 'reviewedByName', width: 140 },
    { title: 'Ngày duyệt', dataIndex: 'reviewedAt', width: 150, render: dateTime },
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
              <ConfirmButton
                type="primary"
                size="small"
                title="Duyệt phiếu xuất NVL này?"
                description="Hệ thống sẽ trừ tồn kho NVL. Nếu kho không đủ tồn, thao tác sẽ bị chặn."
                loading={approveMutation.isPending}
                onConfirm={() => approveMutation.mutate(r.id)}
              >
                <CheckOutlined /> Duyệt
              </ConfirmButton>
              <Button
                danger
                size="small"
                icon={<CloseOutlined />}
                onClick={() => setRejectId(r.id)}
              >
                Từ chối
              </Button>
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

  return (
    <>
      <PageHeader
        title="Duyệt phiếu xuất nguyên vật liệu"
        subtitle="Kiểm tra tồn kho và phê duyệt xuất NVL cho các yêu cầu sản xuất"
      />

      <PagedTable
        filters={(
          <Space wrap>
            <InputNumber
              min={1}
              style={{ width: 140 }}
              placeholder="Mã PR"
              value={filters.productionRequestId}
              onChange={(v) => { setPage(0); setFilters((f) => ({ ...f, productionRequestId: v || undefined })) }}
            />
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              style={{ width: 200 }}
              placeholder="Nhân viên yêu cầu"
              options={staffQuery.data?.map((s) => ({ value: s.id, label: s.fullName })) || []}
              value={filters.requestedBy}
              onChange={(v) => { setPage(0); setFilters((f) => ({ ...f, requestedBy: v })) }}
            />
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

      {/* Modal chi tiết phiếu xuất NVL */}
      <Modal
        title={`Chi tiết phiếu xuất NVL #${detailId}`}
        open={!!detailId}
        width={760}
        footer={[
          detailQuery.data?.status === 'PENDING' && (
            <ConfirmButton
              key="approve"
              type="primary"
              title="Xác nhận duyệt xuất NVL này?"
              description="Hệ thống sẽ trừ tồn kho NVL qua sổ kho."
              loading={approveMutation.isPending}
              onConfirm={() => approveMutation.mutate(detailId)}
            >
              Duyệt xuất kho
            </ConfirmButton>
          ),
          detailQuery.data?.status === 'PENDING' && (
            <Button
              key="reject"
              danger
              onClick={() => setRejectId(detailId)}
            >
              Từ chối phiếu
            </Button>
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
                <Tag color="blue">PR #{detailQuery.data.productionRequestId}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Nhân viên đề nghị">
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
              <Descriptions.Item label="Ghi chú">
                {detailQuery.data.note || '—'}
              </Descriptions.Item>
            </Descriptions>

            {detailQuery.data.hasStockShortage && (
              <Alert
                type="warning"
                showIcon
                style={{ marginTop: 16 }}
                message="Cảnh báo tồn kho"
                description={detailQuery.data.warning || 'Có nguyên vật liệu trong phiếu có số lượng yêu cầu vượt quá tồn kho thực tế! Khi duyệt hệ thống sẽ báo lỗi và chặn trừ kho.'}
              />
            )}

            <Divider style={{ margin: '16px 0 12px' }}>Danh sách nguyên vật liệu xin xuất</Divider>

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
                  width: 130,
                  render: (v) => <Typography.Text strong>{quantity(v)}</Typography.Text>,
                },
                {
                  title: 'Tồn kho hiện tại',
                  dataIndex: 'currentStock',
                  width: 130,
                  render: (v) => quantity(v),
                },
                {
                  title: 'Khả năng đáp ứng',
                  key: 'check',
                  width: 140,
                  render: (_, r) => {
                    const reqQty = Number(r.quantity) || 0
                    const curStock = Number(r.currentStock) || 0
                    return curStock >= reqQty ? (
                      <Tag color="green">Đủ tồn kho</Tag>
                    ) : (
                      <Tag color="red">Thiếu {quantity(reqQty - curStock)}</Tag>
                    )
                  },
                },
              ]}
            />
          </>
        )}
      </Modal>

      {/* Modal từ chối (bắt buộc lý do) */}
      <RejectModal
        title={`Từ chối phiếu xuất NVL #${rejectId}`}
        open={!!rejectId}
        confirmLoading={rejectMutation.isPending}
        onCancel={() => setRejectId(null)}
        onSubmit={(reason) => rejectMutation.mutate({ id: rejectId, reason })}
      />
    </>
  )
}


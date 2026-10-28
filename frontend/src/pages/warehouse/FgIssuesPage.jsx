import { useSingleMutation as useMutation } from '../../api/useSingleMutation'
import { useAllPages } from '../../api/useAllPages'
import QueryError from '../../components/QueryError'
import { useState } from 'react'
import {
  Alert,
  Button,
  Descriptions,
  Divider,
  Modal,
  Select,
  Space,
  Table,
  Typography,
  message,
} from 'antd'
import {
  CheckOutlined,
  CloseOutlined,
  EyeOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { useQuery, useQueryClient } from '@tanstack/react-query'
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

export default function FgIssuesPage() {
  const queryClient = useQueryClient()
  const [filters, setFilters] = useState({})
  const [page, setPage] = useState(0)
  const [sort, setSort] = useState({ sort: 'id', direction: 'desc' })

  const [detailId, setDetailId] = useState(null)
  const [rejectId, setRejectId] = useState(null)

  const fgQuery = usePagedQuery(
    'fg-issue-requests',
    '/api/fg-issue-requests',
    { ...filters, ...sort, page, size: PAGE_SIZE },
  )

  const productsQuery = useAllPages({
    queryKey: ['products-for-fg-filter'],
    path: '/api/products',
    params: {  },
  })

  const staffQuery = useQuery({
    queryKey: ['staff-for-fg-filter'],
    queryFn: () => api.get('/api/staff'),
  })

  const detailQuery = useQuery({
    queryKey: ['fg-detail', detailId],
    queryFn: () => api.get(`/api/fg-issue-requests/${detailId}`),
    enabled: !!detailId,
  })

  const approveMutation = useMutation({
    mutationFn: (id) => api.post(`/api/fg-issue-requests/${id}/approve`),
    onSuccess: () => {
      message.success('Đã duyệt phiếu xuất thành phẩm và trừ tồn kho thành công!')
      queryClient.invalidateQueries({ queryKey: ['fg-issue-requests'] })
      queryClient.invalidateQueries({ queryKey: ['inv-products'] })
      queryClient.invalidateQueries({ queryKey: ['stock-movements'] })
      if (detailId) queryClient.invalidateQueries({ queryKey: ['fg-detail', detailId] })
    },
    onError: (err) => message.error(err.message || 'Lỗi duyệt phiếu xuất thành phẩm'),
  })

  const rejectMutation = useMutation({
    mutationFn: ({ id, reason }) => api.post(`/api/fg-issue-requests/${id}/reject`, { reason }),
    onSuccess: () => {
      message.success('Đã từ chối phiếu xuất thành phẩm')
      queryClient.invalidateQueries({ queryKey: ['fg-issue-requests'] })
      if (detailId) queryClient.invalidateQueries({ queryKey: ['fg-detail', detailId] })
      setRejectId(null)
    },
    onError: (err) => message.error(err.message || 'Lỗi từ chối phiếu xuất thành phẩm'),
  })

  const columns = [
    { title: 'Mã phiếu', dataIndex: 'id', width: 90, sorter: true, render: (v) => `#${v}` },
    { title: 'Nhân viên yêu cầu', dataIndex: 'requestedByName', width: 160 },
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
              <ConfirmButton disabled={approveMutation.isPending}
                type="primary"
                size="small"
                title="Duyệt xuất kho thành phẩm này?"
                description="Hệ thống sẽ trừ tồn kho thành phẩm. Nếu kho thiếu tồn, thao tác sẽ bị chặn."
                loading={approveMutation.isPending}
                onConfirm={() => approveMutation.mutateAsync(r.id)}
              >
                <CheckOutlined /> Duyệt
              </ConfirmButton>
              <Button disabled={rejectMutation.isPending} loading={rejectMutation.isPending}
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
      <QueryError query={productsQuery} />
      <PageHeader
        title="Duyệt phiếu xuất thành phẩm để bán"
        subtitle="Kiểm tra tồn kho thành phẩm và phê duyệt đơn hàng xuất bán do nhân viên lập"
      />

      <PagedTable query={fgQuery}
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

      {/* Modal chi tiết phiếu xuất thành phẩm */}
      <Modal cancelButtonProps={{ disabled: approveMutation.isPending }} closable={!(approveMutation.isPending)} maskClosable={!(approveMutation.isPending)} okButtonProps={{ disabled: approveMutation.isPending }}
        title={`Chi tiết phiếu xuất thành phẩm #${detailId}`}
        open={!!detailId}
        width={760}
        footer={[
          detailQuery.data?.status === 'PENDING' && (
            <ConfirmButton disabled={approveMutation.isPending}
              key="approve"
              type="primary"
              title="Xác nhận duyệt xuất thành phẩm này?"
              description="Hệ thống sẽ trừ tồn kho thành phẩm qua sổ kho."
              loading={approveMutation.isPending}
              onConfirm={() => approveMutation.mutateAsync(detailId)}
            >
              Duyệt xuất kho
            </ConfirmButton>
          ),
          detailQuery.data?.status === 'PENDING' && (
            <Button disabled={rejectMutation.isPending} loading={rejectMutation.isPending}
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
              <Descriptions.Item label="Nhân viên đề xuất">
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
              <Descriptions.Item label="Ghi chú người mua / đơn hàng" span={2}>
                {detailQuery.data.note || '—'}
              </Descriptions.Item>
            </Descriptions>

            {detailQuery.data.hasStockShortage && (
              <Alert
                type="warning"
                showIcon
                style={{ marginTop: 16 }}
                message="Cảnh báo thiếu tồn kho thành phẩm"
                description={detailQuery.data.warning || 'Có sản phẩm trong phiếu có số lượng yêu cầu vượt quá tồn kho thực tế! Khi duyệt hệ thống sẽ báo lỗi và chặn trừ kho.'}
              />
            )}

            <Divider style={{ margin: '16px 0 12px' }}>Danh sách sản phẩm xin xuất</Divider>

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
                {
                  title: 'Khả năng đáp ứng',
                  key: 'check',
                  width: 140,
                  render: (_, r) => {
                    const reqQty = Number(r.quantity) || 0
                    const curStock = Number(r.currentStock) || 0
                    return curStock >= reqQty ? (
                      <span className="erp-tag erp-tag-approved">Đủ tồn kho</span>
                    ) : (
                      <span className="erp-tag erp-tag-rejected">Thiếu {reqQty - curStock}</span>
                    )
                  },
                },
              ]}
            />
          </>
        )}
      </Modal>

      {/* Modal từ chối */}
      <RejectModal
        title={`Từ chối phiếu xuất thành phẩm #${rejectId}`}
        open={!!rejectId}
        confirmLoading={rejectMutation.isPending}
        onCancel={() => setRejectId(null)}
        onSubmit={(reason) => rejectMutation.mutate({ id: rejectId, reason })}
      />
    </>
  )
}


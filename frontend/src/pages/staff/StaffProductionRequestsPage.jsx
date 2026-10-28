import { useState } from 'react'
import {
  Button,
  Descriptions,
  Divider,
  Modal,
  Progress,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import {
  EyeOutlined,
  FileSearchOutlined,
  PlusOutlined,
  ReloadOutlined,
  ToolOutlined,
} from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { api } from '../../api/client'
import { usePagedQuery } from '../../api/hooks'
import PageHeader from '../../components/PageHeader'
import PagedTable from '../../components/PagedTable'
import { DocStatusTag } from '../../components/StatusTag'
import { dateTime, quantity } from '../../utils/format'
import { productionRequestStatusOptions } from '../../utils/statusMap'

const PAGE_SIZE = 15

export default function StaffProductionRequestsPage() {
  const navigate = useNavigate()
  const [filters, setFilters] = useState({})
  const [page, setPage] = useState(0)
  const [sort, setSort] = useState({ sort: 'id', direction: 'desc' })

  const [detailId, setDetailId] = useState(null)
  const [requirementsId, setRequirementsId] = useState(null)

  const prQuery = usePagedQuery(
    'staff-production-requests',
    '/api/production-requests',
    { ...filters, ...sort, page, size: PAGE_SIZE },
  )

  const detailQuery = useQuery({
    queryKey: ['staff-pr-detail', detailId],
    queryFn: () => api.get(`/api/production-requests/${detailId}`),
    enabled: !!detailId,
  })

  const reqQuery = useQuery({
    queryKey: ['staff-pr-requirements', requirementsId],
    queryFn: () => api.get(`/api/production-requests/${requirementsId}/requirements`),
    enabled: !!requirementsId,
  })

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 80, sorter: true, render: (v) => `#${v}` },
    { title: 'Sản phẩm', dataIndex: 'productName' },
    {
      title: 'Số lượng giao',
      dataIndex: 'quantity',
      sorter: true,
      width: 110,
      render: (v, r) => `${v} ${r.productUnit || ''}`,
    },
    {
      title: 'Tiến độ hoàn thành',
      key: 'progress',
      width: 190,
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
      render: (v) => <Tag className="erp-tag erp-tag-neutral">v{v}</Tag>,
    },
    { title: 'Người giao (WM)', dataIndex: 'warehouseManagerName', width: 140 },
    { title: 'Trạng thái', dataIndex: 'status', sorter: true, width: 120, render: (v) => <DocStatusTag status={v} /> },
    { title: 'Ngày giao', dataIndex: 'createdAt', sorter: true, width: 150, render: dateTime },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 320,
      render: (_, r) => (
        <Space size="small" wrap>
          <Button size="small" icon={<EyeOutlined />} onClick={() => setDetailId(r.id)}>
            Chi tiết
          </Button>
          <Button size="small" icon={<FileSearchOutlined />} onClick={() => setRequirementsId(r.id)}>
            Kiểm tra NVL
          </Button>
          {(r.status === 'PENDING' || r.status === 'IN_PROGRESS') && (
            <>
              <Button
                size="small"
                type="dashed"
                icon={<PlusOutlined />}
                onClick={() => navigate('/staff/material-requests', { state: { prefillPrId: r.id } })}
              >
                Xuất NVL
              </Button>
              <Button
                size="small"
                type="primary"
                icon={<ToolOutlined />}
                onClick={() => navigate('/staff/productions', { state: { prefillPrId: r.id } })}
              >
                Sản xuất
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
        title="Yêu cầu sản xuất của tôi"
        subtitle="Danh sách các lệnh và yêu cầu sản xuất được Quản lý kho giao cho bạn"
      />

      <PagedTable query={prQuery}
        filters={(
          <Space wrap>
            <Select
              allowClear
              style={{ width: 160 }}
              placeholder="Trạng thái"
              options={productionRequestStatusOptions}
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

      {/* Modal chi tiết yêu cầu sản xuất */}
      <Modal
        title={`Chi tiết yêu cầu sản xuất #${detailId}`}
        open={!!detailId}
        width={780}
        footer={[<Button key="close" onClick={() => setDetailId(null)}>Đóng</Button>]}
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
              <Descriptions.Item label="Số lượng giao">
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
              <Descriptions.Item label="Quản lý kho giao việc">
                {detailQuery.data.warehouseManagerName}
              </Descriptions.Item>
              <Descriptions.Item label="Ngày giao">
                {dateTime(detailQuery.data.createdAt)}
              </Descriptions.Item>
              {detailQuery.data.note && (
                <Descriptions.Item label="Ghi chú" span={2}>
                  {detailQuery.data.note}
                </Descriptions.Item>
              )}
            </Descriptions>

            <Divider style={{ margin: '16px 0 12px' }}>Các đợt sản xuất đã thực hiện</Divider>
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
                { title: 'Thời gian bắt đầu', dataIndex: 'startedAt', width: 140, render: dateTime },
                { title: 'Thời gian hoàn tất', dataIndex: 'completedAt', width: 140, render: dateTime },
              ]}
            />

            <Divider style={{ margin: '16px 0 12px' }}>Các phiếu đề nghị xuất nguyên vật liệu</Divider>
            <Table
              size="small"
              rowKey="id"
              pagination={false}
              dataSource={detailQuery.data.issueRequests || []}
              locale={{ emptyText: 'Chưa có phiếu xuất NVL nào' }}
              columns={[
                { title: 'Mã phiếu', dataIndex: 'id', width: 90, render: (v) => `#${v}` },
                { title: 'Số dòng NVL', dataIndex: 'itemCount', width: 100, render: (v) => `${v} loại` },
                { title: 'Trạng thái', dataIndex: 'status', width: 120, render: (v) => <DocStatusTag status={v} /> },
                { title: 'Ngày yêu cầu', dataIndex: 'requestedAt', width: 140, render: dateTime },
                { title: 'Người duyệt', dataIndex: 'reviewedByName', width: 130 },
              ]}
            />
          </>
        )}
      </Modal>

      {/* Modal kiểm tra NVL */}
      <Modal
        title={`Kiểm tra định mức NVL – Yêu cầu SX #${requirementsId}`}
        open={!!requirementsId}
        width={880}
        footer={[<Button key="close" onClick={() => setRequirementsId(null)}>Đóng</Button>]}
        onCancel={() => setRequirementsId(null)}
        destroyOnHidden
      >
        {reqQuery.data && (
          <>
            <Descriptions bordered size="small" column={3} style={{ marginBottom: 16 }}>
              <Descriptions.Item label="Sản phẩm">{reqQuery.data.productName}</Descriptions.Item>
              <Descriptions.Item label="Số lượng giao">{reqQuery.data.quantity}</Descriptions.Item>
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
                { title: 'Định mức 1 SP', dataIndex: 'bomQuantity', width: 100, render: (v) => quantity(v) },
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
                  width: 110,
                  render: (v) => <Typography.Text type={v > 0 ? 'success' : 'secondary'}>{quantity(v)}</Typography.Text>,
                },
                { title: 'Tồn kho', dataIndex: 'onHand', width: 90, render: (v) => quantity(v) },
                {
                  title: 'Thiếu hụt',
                  dataIndex: 'shortage',
                  width: 100,
                  render: (v) => {
                    const num = Number(v) || 0
                    return num > 0 ? (
                      <span className="erp-tag erp-tag-rejected">Thiếu: {quantity(num)}</span>
                    ) : (
                      <span className="erp-tag erp-tag-approved">Đủ</span>
                    )
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


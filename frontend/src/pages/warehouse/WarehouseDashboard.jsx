import { useNavigate } from 'react-router-dom'
import { Button, Card, Col, Row, Space, Statistic, Table, Tabs, Typography } from 'antd'
import {
  ArrowRightOutlined,
  AuditOutlined,
  BarChartOutlined,
  CalculatorOutlined,
  CheckCircleOutlined,
  FieldTimeOutlined,
  FileDoneOutlined,
  HistoryOutlined,
  InboxOutlined,
  PlusOutlined,
  ShopOutlined,
  ShoppingCartOutlined,
} from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { api } from '../../api/client'
import PageHeader from '../../components/PageHeader'
import { DocStatusTag, MovementTypeTag } from '../../components/StatusTag'
import { useAuth } from '../../auth/context'
import { dateTime, quantity } from '../../utils/format'
import { roleLabels } from '../../utils/statusMap'

const cards = [
  { key: 'mat-stock', label: 'Tồn NVL (mặt hàng)', icon: <InboxOutlined />, path: '/api/inventories/materials', to: '/warehouse/inventory' },
  { key: 'prod-stock', label: 'Tồn thành phẩm (loại)', icon: <ShopOutlined />, path: '/api/inventories/products', to: '/warehouse/inventory' },
  { key: 'boms', label: 'Công thức BOM', icon: <FileDoneOutlined />, path: '/api/boms', to: '/warehouse/boms' },
  { key: 'receipts', label: 'Phiếu nhập kho', icon: <ShoppingCartOutlined />, path: '/api/purchase-receipts', to: '/warehouse/receipts' },
  { key: 'pr', label: 'Yêu cầu sản xuất', icon: <AuditOutlined />, path: '/api/production-requests', to: '/warehouse/production-requests' },
  { key: 'mir', label: 'Phiếu xuất NVL chờ duyệt', icon: <FieldTimeOutlined />, path: '/api/material-issue-requests', query: { status: 'PENDING' }, to: '/warehouse/material-issues', highlight: true },
  { key: 'fg', label: 'Xuất TP chờ duyệt', icon: <FieldTimeOutlined />, path: '/api/fg-issue-requests', query: { status: 'PENDING' }, to: '/warehouse/fg-issues', highlight: true },
  { key: 'movements', label: 'Lịch sử biến động sổ kho', icon: <HistoryOutlined />, path: '/api/stock-movements', to: '/warehouse/movements' },
]

function StatCard({ item }) {
  const navigate = useNavigate()
  const query = useQuery({
    queryKey: [item.key, 'wm-count'],
    queryFn: ({ signal }) => api.get(item.path, { page: 0, size: 1, ...(item.query || {}) }, signal),
  })
  return (
    <Card
      className="stat-card"
      loading={query.isPending}
      onClick={() => navigate(item.to)}
      hoverable
      style={item.highlight ? { borderColor: '#d97706', background: '#fffbeb' } : {}}
    >
      <Statistic
        title={<span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>{item.icon} {item.label}</span>}
        value={query.data?.totalElements ?? 0}
        valueStyle={item.highlight ? { color: '#b45309', fontWeight: 700 } : {}}
      />
    </Card>
  )
}

function PendingMirTable() {
  const navigate = useNavigate()
  const query = useQuery({
    queryKey: ['warehouse-pending-mir'],
    queryFn: ({ signal }) =>
      api.get('/api/material-issue-requests', { status: 'PENDING', page: 0, size: 5, sort: 'requestedAt', direction: 'desc' }, signal),
  })

  const columns = [
    { title: 'Mã phiếu', dataIndex: 'id', width: 85, render: (v) => <Typography.Text code>#{v}</Typography.Text> },
    { title: 'Yêu cầu SX', dataIndex: 'productionRequestId', width: 110, render: (v) => `PR #${v}` },
    { title: 'Người yêu cầu', dataIndex: 'requestedByName', width: 140, ellipsis: true },
    { title: 'Thời gian gửi', dataIndex: 'requestedAt', width: 150, render: dateTime },
    { title: 'Trạng thái', dataIndex: 'status', width: 120, render: (v) => <DocStatusTag status={v} /> },
    {
      title: 'Thao tác',
      key: 'action',
      width: 100,
      render: () => (
        <Button size="small" type="link" onClick={() => navigate('/warehouse/material-issues')}>
          Xem duyệt
        </Button>
      ),
    },
  ]

  return (
    <Table
      size="small"
      rowKey="id"
      loading={query.isFetching}
      dataSource={query.data?.content || []}
      columns={columns}
      pagination={false}
      locale={{ emptyText: 'Không có phiếu xuất NVL nào đang chờ duyệt' }}
    />
  )
}

function PendingFgTable() {
  const navigate = useNavigate()
  const query = useQuery({
    queryKey: ['warehouse-pending-fg'],
    queryFn: ({ signal }) =>
      api.get('/api/fg-issue-requests', { status: 'PENDING', page: 0, size: 5, sort: 'requestedAt', direction: 'desc' }, signal),
  })

  const columns = [
    { title: 'Mã phiếu', dataIndex: 'id', width: 85, render: (v) => <Typography.Text code>#{v}</Typography.Text> },
    { title: 'Thành phẩm', dataIndex: 'productName', ellipsis: true },
    { title: 'Số lượng', dataIndex: 'quantity', width: 110, render: (v, r) => `${quantity(v)} ${r.productUnit || ''}` },
    { title: 'Khách hàng', dataIndex: 'customerName', width: 140, ellipsis: true },
    { title: 'Thời gian', dataIndex: 'requestedAt', width: 150, render: dateTime },
    { title: 'Trạng thái', dataIndex: 'status', width: 120, render: (v) => <DocStatusTag status={v} /> },
    {
      title: 'Thao tác',
      key: 'action',
      width: 100,
      render: () => (
        <Button size="small" type="link" onClick={() => navigate('/warehouse/fg-issues')}>
          Xem duyệt
        </Button>
      ),
    },
  ]

  return (
    <Table
      size="small"
      rowKey="id"
      loading={query.isFetching}
      dataSource={query.data?.content || []}
      columns={columns}
      pagination={false}
      locale={{ emptyText: 'Không có phiếu xuất thành phẩm nào đang chờ duyệt' }}
    />
  )
}

function RecentMovementsTable() {
  const query = useQuery({
    queryKey: ['warehouse-recent-movements'],
    queryFn: ({ signal }) =>
      api.get('/api/stock-movements', { page: 0, size: 5, sort: 'createdAt', direction: 'desc' }, signal),
  })

  const columns = [
    { title: 'Mã GD', dataIndex: 'id', width: 80, render: (v) => <Typography.Text code>#{v}</Typography.Text> },
    { title: 'Loại biến động', dataIndex: 'movementType', width: 140, render: (v) => <MovementTypeTag type={v} /> },
    {
      title: 'Mặt hàng',
      key: 'item',
      render: (_, r) => r.materialName
        ? `${r.materialCode ? `[${r.materialCode}] ` : ''}${r.materialName}`
        : (r.productName || '—'),
      ellipsis: true,
    },
    {
      title: 'Số lượng',
      dataIndex: 'quantity',
      width: 110,
      render: (v, r) => (
        <Typography.Text strong>
          {quantity(v)} {r.unit || ''}
        </Typography.Text>
      ),
    },
    { title: 'Người ghi', dataIndex: 'createdByUsername', width: 120, render: (v) => v || '—' },
    { title: 'Thời gian', dataIndex: 'createdAt', width: 150, render: dateTime },
  ]

  return (
    <Table
      size="small"
      rowKey="id"
      loading={query.isFetching}
      dataSource={query.data?.content || []}
      columns={columns}
      pagination={false}
      locale={{ emptyText: 'Chưa có giao dịch sổ kho nào phát sinh' }}
    />
  )
}

export default function WarehouseDashboard() {
  const { user } = useAuth()
  const navigate = useNavigate()

  return (
    <>
      <PageHeader
        title="Tổng quan kho"
        subtitle={`Xin chào ${user.fullName} – ${roleLabels[user.role]}`}
        extra={(
          <Space wrap>
            <Button
              type="primary"
              icon={<CalculatorOutlined />}
              onClick={() => navigate('/warehouse/planning')}
            >
              Lập kế hoạch MRP
            </Button>
            <Button
              icon={<PlusOutlined />}
              onClick={() => navigate('/warehouse/receipts')}
            >
              Tạo phiếu nhập kho
            </Button>
            <Button
              icon={<BarChartOutlined />}
              onClick={() => navigate('/warehouse/reports')}
            >
              Báo cáo kho
            </Button>
          </Space>
        )}
      />

      <Row gutter={[16, 16]}>
        {cards.map((item) => (
          <Col key={item.key} xs={24} sm={12} lg={6}>
            <StatCard item={item} />
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 20 }}>
        <Col xs={24} xl={16}>
          <Card
            title="Phiếu chờ phê duyệt & Biến động kho"
            extra={(
              <Button type="link" onClick={() => navigate('/warehouse/movements')}>
                Xem chi tiết sổ kho <ArrowRightOutlined />
              </Button>
            )}
            style={{ height: '100%' }}
          >
            <Tabs
              defaultActiveKey="mir"
              items={[
                {
                  key: 'mir',
                  label: (
                    <span>
                      <FieldTimeOutlined /> Xuất NVL chờ duyệt
                    </span>
                  ),
                  children: <PendingMirTable />,
                },
                {
                  key: 'fg',
                  label: (
                    <span>
                      <CheckCircleOutlined /> Xuất TP chờ duyệt
                    </span>
                  ),
                  children: <PendingFgTable />,
                },
                {
                  key: 'movements',
                  label: (
                    <span>
                      <HistoryOutlined /> Biến động kho gần đây
                    </span>
                  ),
                  children: <RecentMovementsTable />,
                },
              ]}
            />
          </Card>
        </Col>

        <Col xs={24} xl={8}>
          <Card title="Phím tắt chức năng Kho" style={{ height: '100%' }}>
            <Space direction="vertical" style={{ width: '100%' }} size={12}>
              <Button block type="primary" onClick={() => navigate('/warehouse/planning')}>
                <CalculatorOutlined /> Lập kế hoạch MRP cho sản phẩm
              </Button>
              <Button block onClick={() => navigate('/warehouse/receipts')}>
                <ShoppingCartOutlined /> Quản lý phiếu nhập kho NVL
              </Button>
              <Button block onClick={() => navigate('/warehouse/material-issues')}>
                <FieldTimeOutlined /> Phê duyệt phiếu xuất nguyên vật liệu
              </Button>
              <Button block onClick={() => navigate('/warehouse/fg-issues')}>
                <CheckCircleOutlined /> Phê duyệt phiếu xuất thành phẩm
              </Button>
              <Button block onClick={() => navigate('/warehouse/production-requests')}>
                <AuditOutlined /> Quản lý và theo dõi yêu cầu sản xuất
              </Button>
              <Button block onClick={() => navigate('/warehouse/inventory')}>
                <InboxOutlined /> Kiểm tra tồn kho & Điều chỉnh kiểm kê
              </Button>
              <Button block onClick={() => navigate('/warehouse/reports')}>
                <BarChartOutlined /> Báo cáo Xuất – Nhập – Tồn kho
              </Button>
            </Space>
          </Card>
        </Col>
      </Row>
    </>
  )
}

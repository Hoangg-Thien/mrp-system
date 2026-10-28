import QueryError from '../../components/QueryError'
import { useNavigate } from 'react-router-dom'
import { Button, Card, Col, Progress, Row, Space, Statistic, Table, Tabs, Typography } from 'antd'
import {
  AppstoreOutlined,
  ArrowRightOutlined,
  AuditOutlined,
  BarChartOutlined,
  HistoryOutlined,
  InboxOutlined,
  ProfileOutlined,
  TableOutlined,
  TagsOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { api } from '../../api/client'
import PageHeader from '../../components/PageHeader'
import { DocStatusTag, MovementTypeTag } from '../../components/StatusTag'
import { useAuth } from '../../auth/context'
import { dateTime, quantity } from '../../utils/format'
import { roleLabels } from '../../utils/statusMap'

const stats = [
  { key: 'users', label: 'Tài khoản người dùng', icon: <TeamOutlined />, path: '/api/admin/users', to: '/admin/users' },
  { key: 'products', label: 'Sản phẩm', icon: <TableOutlined />, path: '/api/products', to: '/admin/products' },
  { key: 'materials', label: 'Nguyên vật liệu', icon: <InboxOutlined />, path: '/api/materials', to: '/admin/materials' },
  { key: 'suppliers', label: 'Nhà cung cấp', icon: <ProfileOutlined />, path: '/api/suppliers', to: '/admin/suppliers' },
  { key: 'categories', label: 'Danh mục', icon: <TagsOutlined />, path: '/api/categories', to: '/admin/categories' },
  { key: 'pr', label: 'Yêu cầu sản xuất', icon: <AuditOutlined />, path: '/api/production-requests', to: '/admin/reports' },
]

function StatCard({ item }) {
  const navigate = useNavigate()
  const query = useQuery({
    queryKey: [item.key, 'count'],
    queryFn: ({ signal }) => api.get(item.path, { page: 0, size: 1 }, signal),
  })
  if (query.isError) return <QueryError query={query} />

  return (
    <Card className="stat-card" loading={query.isPending} onClick={() => navigate(item.to)} hoverable>
      <Statistic
        title={<span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>{item.icon} {item.label}</span>}
        value={query.data?.totalElements ?? 0}
      />
    </Card>
  )
}

function RecentProductionTable() {
  const query = useQuery({
    queryKey: ['admin-recent-pr'],
    queryFn: ({ signal }) =>
      api.get('/api/production-requests', { page: 0, size: 5, sort: 'createdAt', direction: 'desc' }, signal),
  })

  const columns = [
    { title: 'Mã PR', dataIndex: 'id', width: 85, render: (v) => <Typography.Text code>#{v}</Typography.Text> },
    { title: 'Sản phẩm', dataIndex: 'productName', ellipsis: true },
    {
      title: 'Số lượng',
      dataIndex: 'quantity',
      width: 110,
      render: (v, r) => `${v} ${r.productUnit || ''}`,
    },
    {
      title: 'Tiến độ',
      key: 'progress',
      width: 140,
      render: (_, r) => {
        const percent = Math.min(100, Math.round(((r.completedQuantity || 0) / (r.quantity || 1)) * 100))
        return (
          <Space direction="vertical" size={2} style={{ width: '100%' }}>
            <Progress percent={percent} size="small" status={r.status === 'COMPLETED' ? 'success' : 'active'} />
            <span style={{ fontSize: 11, color: '#6b7280' }}>
              Xong {r.completedQuantity || 0}/{r.quantity}
            </span>
          </Space>
        )
      },
    },
    { title: 'Nhân viên', dataIndex: 'assignedStaffName', width: 140, ellipsis: true },
    { title: 'Trạng thái', dataIndex: 'status', width: 120, render: (v) => <DocStatusTag status={v} /> },
    { title: 'Thời gian tạo', dataIndex: 'createdAt', width: 150, render: dateTime },
  ]

  if (query.isError) return <QueryError query={query} />

  return (
    <Table
      size="small"
      rowKey="id"
      loading={query.isFetching}
      dataSource={query.data?.content || []}
      columns={columns}
      pagination={false}
      locale={{ emptyText: 'Chưa có yêu cầu sản xuất nào phát sinh' }}
    />
  )
}

function RecentMovementsTable() {
  const query = useQuery({
    queryKey: ['admin-recent-movements'],
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

  if (query.isError) return <QueryError query={query} />

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

/** Tổng quan khu vực Admin. */
export default function DashboardPage() {
  const { user } = useAuth()
  const navigate = useNavigate()

  return (
    <>
      <PageHeader
        title="Tổng quan Quản trị"
        subtitle={`Xin chào ${user.fullName} – ${roleLabels[user.role]}`}
        extra={(
          <Button
            type="primary"
            icon={<BarChartOutlined />}
            onClick={() => navigate('/admin/reports')}
          >
            Báo cáo kho
          </Button>
        )}
      />

      <Row gutter={[16, 16]}>
        {stats.map((item) => (
          <Col key={item.key} xs={24} sm={12} lg={8}>
            <StatCard item={item} />
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 20 }}>
        <Col xs={24} xl={16}>
          <Card
            title="Hoạt động thời gian thực của hệ thống"
            extra={(
              <Button type="link" onClick={() => navigate('/admin/reports')}>
                Xem chi tiết báo cáo <ArrowRightOutlined />
              </Button>
            )}
            style={{ height: '100%' }}
          >
            <Tabs
              defaultActiveKey="pr"
              items={[
                {
                  key: 'pr',
                  label: (
                    <span>
                      <AuditOutlined /> Yêu cầu sản xuất mới nhất
                    </span>
                  ),
                  children: <RecentProductionTable />,
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
          <Card title="Phím tắt chức năng Quản trị" style={{ height: '100%' }}>
            <Space direction="vertical" style={{ width: '100%' }} size={12}>
              <Button block type="primary" onClick={() => navigate('/admin/users')}>
                <TeamOutlined /> Quản lý tài khoản & Phân quyền
              </Button>
              <Button block onClick={() => navigate('/admin/products')}>
                <TableOutlined /> Quản lý danh mục Sản phẩm
              </Button>
              <Button block onClick={() => navigate('/admin/materials')}>
                <AppstoreOutlined /> Quản lý Nguyên vật liệu
              </Button>
              <Button block onClick={() => navigate('/admin/suppliers')}>
                <ProfileOutlined /> Quản lý Nhà cung cấp
              </Button>
              <Button block onClick={() => navigate('/admin/staff')}>
                <TeamOutlined /> Danh sách Nhân viên (giao việc)
              </Button>
              <Button block onClick={() => navigate('/admin/reports')}>
                <BarChartOutlined /> Báo cáo Xuất – Nhập – Tồn kho
              </Button>
            </Space>
          </Card>
        </Col>
      </Row>
    </>
  )
}

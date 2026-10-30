import QueryError from '../../components/QueryError'
import { useNavigate } from 'react-router-dom'
import { Button, Card, Col, Row, Space, Statistic, Table, Typography } from 'antd'
import {
  AppstoreOutlined,
  ArrowRightOutlined,
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
import { MovementTypeTag } from '../../components/StatusTag'
import { useAuth } from '../../auth/context'
import { dateTime, quantity } from '../../utils/format'
import { roleLabels } from '../../utils/statusMap'

const stats = [
  { key: 'users', label: 'Tài khoản người dùng', icon: <TeamOutlined />, path: '/api/admin/users', to: '/admin/users' },
  { key: 'products', label: 'Sản phẩm', icon: <TableOutlined />, path: '/api/products', to: '/admin/products' },
  { key: 'materials', label: 'Nguyên vật liệu', icon: <InboxOutlined />, path: '/api/materials', to: '/admin/materials' },
  { key: 'suppliers', label: 'Nhà cung cấp', icon: <ProfileOutlined />, path: '/api/suppliers', to: '/admin/suppliers' },
  { key: 'categories', label: 'Danh mục', icon: <TagsOutlined />, path: '/api/categories', to: '/admin/categories' },
  { key: 'staff', label: 'Nhân viên sản xuất', icon: <TeamOutlined />, path: '/api/staff', to: '/admin/staff', isList: true },
]

function StatCard({ item }) {
  const navigate = useNavigate()
  const query = useQuery({
    queryKey: [item.key, 'count'],
    queryFn: ({ signal }) => api.get(item.path, item.isList ? undefined : { page: 0, size: 1 }, signal),
  })
  if (query.isError) return <QueryError query={query} />

  return (
    <Card className="stat-card" loading={query.isPending} onClick={() => navigate(item.to)} hoverable>
      <Statistic
        title={<span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>{item.icon} {item.label}</span>}
        value={(item.isList ? query.data?.length : query.data?.totalElements) ?? 0}
      />
    </Card>
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
            title={<span><HistoryOutlined /> Biến động kho gần đây</span>}
            extra={(
              <Button type="link" onClick={() => navigate('/admin/reports')}>
                Xem chi tiết báo cáo <ArrowRightOutlined />
              </Button>
            )}
            style={{ height: '100%' }}
          >
            <RecentMovementsTable />
          </Card>
        </Col>

        <Col xs={24} xl={8}>
          <Card title="Phím tắt chức năng Quản trị" style={{ height: '100%' }}>
            <Space orientation="vertical" style={{ width: '100%' }} size={12}>
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
                <TeamOutlined /> Danh sách Nhân viên sản xuất
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

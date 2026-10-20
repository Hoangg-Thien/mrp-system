import { useNavigate } from 'react-router-dom'
import { Alert, Card, Col, Row, Space, Statistic, Tag } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { api } from '../../api/client'
import PageHeader from '../../components/PageHeader'
import { useAuth } from '../../auth/context'
import { roleLabels } from '../../utils/statusMap'

const stats = [
  { key: 'users', label: 'Tài khoản', path: '/api/admin/users', to: '/admin/users' },
  { key: 'products', label: 'Sản phẩm', path: '/api/products', to: '/admin/products' },
  { key: 'materials', label: 'Nguyên vật liệu', path: '/api/materials', to: '/admin/materials' },
  { key: 'suppliers', label: 'Nhà cung cấp', path: '/api/suppliers', to: '/admin/suppliers' },
  { key: 'categories', label: 'Danh mục', path: '/api/categories', to: '/admin/categories' },
]

function StatCard({ item }) {
  const navigate = useNavigate()
  const query = useQuery({
    queryKey: [item.key, 'count'],
    queryFn: ({ signal }) => api.get(item.path, { page: 0, size: 1 }, signal),
  })
  return (
    <Card className="stat-card" loading={query.isPending} onClick={() => navigate(item.to)} hoverable>
      <Statistic title={item.label} value={query.data?.totalElements ?? 0} />
    </Card>
  )
}

/** Tổng quan khu vực Admin. */
export default function DashboardPage() {
  const { user } = useAuth()
  return (
    <>
      <PageHeader title="Tổng quan" subtitle={`Xin chào ${user.fullName} – ${roleLabels[user.role]}`} />
      <Row gutter={[16, 16]}>
        {stats.map((item) => (
          <Col key={item.key} xs={24} sm={12} lg={8} xxl={4}>
            <StatCard item={item} />
          </Col>
        ))}
      </Row>
      <Card className="welcome-card" title="Phạm vi đã triển khai">
        <Space direction="vertical" size={8}>
          <span><Tag color="green">W0</Tag> Nền tảng: đăng nhập JWT, guard theo role, layout 3 khu vực, API client, TanStack Query.</span>
          <span><Tag color="green">W1</Tag> Quản trị tài khoản: tạo, khóa/mở khóa, đổi vai trò, xóa, hồ sơ cá nhân.</span>
          <span><Tag color="green">W2</Tag> Danh mục dữ liệu: danh mục, sản phẩm, nhà cung cấp, nguyên vật liệu (tìm/lọc/sắp xếp/CRUD).</span>
          <span><Tag color="orange">W3–W8</Tag> Chờ backend P3–P9: tồn kho, phiếu nhập, BOM, kế hoạch MRP, sản xuất, xuất thành phẩm, báo cáo.</span>
        </Space>
        <Alert className="inline-alert" type="info" showIcon message="Khu vực quản lý kho và nhân viên"
          description="Hai khu vực này đã có layout và guard riêng, sẽ mở dần màn hình khi backend triển khai phase tương ứng." />
      </Card>
    </>
  )
}

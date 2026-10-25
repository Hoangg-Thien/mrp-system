import { useNavigate } from 'react-router-dom'
import { Button, Card, Col, Progress, Row, Space, Statistic, Table, Tabs, Typography } from 'antd'
import {
  ArrowRightOutlined,
  AuditOutlined,
  ClockCircleOutlined,
  FieldTimeOutlined,
  PlusOutlined,
  ShoppingCartOutlined,
  ShoppingOutlined,
  ToolOutlined,
} from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { api } from '../../api/client'
import PageHeader from '../../components/PageHeader'
import { DocStatusTag } from '../../components/StatusTag'
import { useAuth } from '../../auth/context'
import { dateTime, quantity } from '../../utils/format'
import { roleLabels } from '../../utils/statusMap'

function StaffRecentPrTable() {
  const navigate = useNavigate()
  const query = useQuery({
    queryKey: ['staff-recent-pr'],
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
    { title: 'Trạng thái', dataIndex: 'status', width: 120, render: (v) => <DocStatusTag status={v} /> },
    {
      title: 'Thao tác',
      key: 'action',
      width: 90,
      render: () => (
        <Button size="small" type="link" onClick={() => navigate('/staff/requests')}>
          Chi tiết
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
      locale={{ emptyText: 'Chưa có yêu cầu sản xuất nào được giao' }}
    />
  )
}

function StaffActiveProductionsTable() {
  const navigate = useNavigate()
  const query = useQuery({
    queryKey: ['staff-active-prod-table'],
    queryFn: ({ signal }) =>
      api.get('/api/productions', { status: 'IN_PROGRESS', page: 0, size: 5, sort: 'startedAt', direction: 'desc' }, signal),
  })

  const columns = [
    { title: 'Mã đợt SX', dataIndex: 'id', width: 95, render: (v) => <Typography.Text code>#{v}</Typography.Text> },
    { title: 'Yêu cầu SX', dataIndex: 'productionRequestId', width: 110, render: (v) => <Typography.Text strong>PR #{v}</Typography.Text> },
    {
      title: 'Số lượng SX',
      dataIndex: 'quantity',
      width: 130,
      render: (v) => <Typography.Text strong>{quantity(v, 0)} sản phẩm</Typography.Text>,
    },
    { title: 'Thời gian bắt đầu', dataIndex: 'startedAt', width: 150, render: dateTime },
    { title: 'Trạng thái', dataIndex: 'status', width: 120, render: (v) => <DocStatusTag status={v} /> },
    {
      title: 'Thao tác',
      key: 'action',
      width: 100,
      render: () => (
        <Button size="small" type="link" onClick={() => navigate('/staff/productions')}>
          Vào báo cáo
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
      locale={{ emptyText: 'Hiện không có đợt sản xuất nào đang chạy' }}
    />
  )
}

function StaffRecentMirTable() {
  const navigate = useNavigate()
  const query = useQuery({
    queryKey: ['staff-recent-mir-table'],
    queryFn: ({ signal }) =>
      api.get('/api/material-issue-requests', { page: 0, size: 5, sort: 'requestedAt', direction: 'desc' }, signal),
  })

  const columns = [
    { title: 'Mã phiếu', dataIndex: 'id', width: 85, render: (v) => <Typography.Text code>#{v}</Typography.Text> },
    { title: 'Mã PR', dataIndex: 'productionRequestId', width: 90, render: (v) => `PR #${v}` },
    { title: 'Thời gian gửi', dataIndex: 'requestedAt', width: 150, render: dateTime },
    { title: 'Trạng thái', dataIndex: 'status', width: 130, render: (v) => <DocStatusTag status={v} /> },
    {
      title: 'Thao tác',
      key: 'action',
      width: 90,
      render: () => (
        <Button size="small" type="link" onClick={() => navigate('/staff/material-requests')}>
          Xem
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
      locale={{ emptyText: 'Chưa có phiếu xuất nguyên vật liệu nào' }}
    />
  )
}

export default function StaffDashboard() {
  const { user } = useAuth()
  const navigate = useNavigate()

  const prQuery = useQuery({
    queryKey: ['staff-pr-count'],
    queryFn: () => api.get('/api/production-requests', { page: 0, size: 1 }),
  })

  const activePrQuery = useQuery({
    queryKey: ['staff-active-pr-count'],
    queryFn: () => api.get('/api/production-requests', { status: 'IN_PROGRESS', page: 0, size: 1 }),
  })

  const prodQuery = useQuery({
    queryKey: ['staff-prod-active-count'],
    queryFn: () => api.get('/api/productions', { status: 'IN_PROGRESS', page: 0, size: 1 }),
  })

  const mirPendingQuery = useQuery({
    queryKey: ['staff-mir-pending-count'],
    queryFn: () => api.get('/api/material-issue-requests', { status: 'PENDING', page: 0, size: 1 }),
  })

  const fgPendingQuery = useQuery({
    queryKey: ['staff-fg-pending-count'],
    queryFn: () => api.get('/api/fg-issue-requests', { status: 'PENDING', page: 0, size: 1 }),
  })

  return (
    <>
      <PageHeader
        title="Bàn làm việc Nhân viên"
        subtitle={`Xin chào ${user.fullName} – ${roleLabels[user.role]}`}
        extra={(
          <Space wrap>
            <Button
              type="primary"
              icon={<AuditOutlined />}
              onClick={() => navigate('/staff/requests')}
            >
              Yêu cầu SX của tôi
            </Button>
            <Button
              icon={<PlusOutlined />}
              onClick={() => navigate('/staff/material-requests')}
            >
              Đề nghị xuất NVL
            </Button>
            <Button
              icon={<ShoppingOutlined />}
              onClick={() => navigate('/staff/fg-requests')}
            >
              Xuất bán thành phẩm
            </Button>
          </Space>
        )}
      />

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} md={8} lg={4}>
          <Card
            className="stat-card"
            hoverable
            onClick={() => navigate('/staff/requests')}
            loading={prQuery.isPending}
          >
            <Statistic
              title="Tổng yêu cầu SX"
              value={prQuery.data?.totalElements ?? 0}
              prefix={<AuditOutlined />}
            />
          </Card>
        </Col>

        <Col xs={24} sm={12} md={8} lg={5}>
          <Card
            className="stat-card"
            hoverable
            onClick={() => navigate('/staff/requests')}
            loading={activePrQuery.isPending}
          >
            <Statistic
              title="Yêu cầu đang làm"
              value={activePrQuery.data?.totalElements ?? 0}
              prefix={<FieldTimeOutlined />}
              valueStyle={{ color: '#2563eb' }}
            />
          </Card>
        </Col>

        <Col xs={24} sm={12} md={8} lg={5}>
          <Card
            className="stat-card"
            hoverable
            onClick={() => navigate('/staff/productions')}
            loading={prodQuery.isPending}
          >
            <Statistic
              title="Đợt SX đang chạy"
              value={prodQuery.data?.totalElements ?? 0}
              prefix={<ToolOutlined />}
              valueStyle={{ color: '#d97706' }}
            />
          </Card>
        </Col>

        <Col xs={24} sm={12} md={8} lg={5}>
          <Card
            className="stat-card"
            hoverable
            onClick={() => navigate('/staff/material-requests')}
            loading={mirPendingQuery.isPending}
          >
            <Statistic
              title="Xuất NVL chờ duyệt"
              value={mirPendingQuery.data?.totalElements ?? 0}
              prefix={<ClockCircleOutlined />}
              valueStyle={{ color: '#059669' }}
            />
          </Card>
        </Col>

        <Col xs={24} sm={12} md={8} lg={5}>
          <Card
            className="stat-card"
            hoverable
            onClick={() => navigate('/staff/fg-requests')}
            loading={fgPendingQuery.isPending}
          >
            <Statistic
              title="Xuất bán TP chờ duyệt"
              value={fgPendingQuery.data?.totalElements ?? 0}
              prefix={<ShoppingCartOutlined />}
              valueStyle={{ color: '#7c3aed' }}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 20 }}>
        <Col xs={24} xl={16}>
          <Card
            title="Nhiệm vụ sản xuất & Phiếu xuất NVL của tôi"
            extra={(
              <Button type="link" onClick={() => navigate('/staff/requests')}>
                Xem tất cả yêu cầu <ArrowRightOutlined />
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
                      <AuditOutlined /> Yêu cầu sản xuất
                    </span>
                  ),
                  children: <StaffRecentPrTable />,
                },
                {
                  key: 'prod',
                  label: (
                    <span>
                      <ToolOutlined /> Đợt SX đang chạy
                    </span>
                  ),
                  children: <StaffActiveProductionsTable />,
                },
                {
                  key: 'mir',
                  label: (
                    <span>
                      <FieldTimeOutlined /> Phiếu xuất NVL
                    </span>
                  ),
                  children: <StaffRecentMirTable />,
                },
              ]}
            />
          </Card>
        </Col>

        <Col xs={24} xl={8}>
          <Card title="Truy cập nhanh" style={{ height: '100%' }}>
            <Space direction="vertical" style={{ width: '100%' }} size={12}>
              <Button block type="primary" onClick={() => navigate('/staff/requests')}>
                <AuditOutlined /> Danh sách yêu cầu sản xuất được giao
              </Button>
              <Button block onClick={() => navigate('/staff/material-requests')}>
                <PlusOutlined /> Quản lý phiếu đề nghị xuất nguyên vật liệu
              </Button>
              <Button block onClick={() => navigate('/staff/productions')}>
                <ToolOutlined /> Quản lý các đợt sản xuất (Bắt đầu / Hoàn tất)
              </Button>
              <Button block onClick={() => navigate('/staff/fg-requests')}>
                <ShoppingOutlined /> Lập phiếu xuất bán thành phẩm cho khách
              </Button>
            </Space>
          </Card>
        </Col>
      </Row>
    </>
  )
}

import { useMemo, useState } from 'react'
import { Alert, Button, Card, Input, Space, Table, Tag, Typography } from 'antd'
import { PlusOutlined, ReloadOutlined, TeamOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { api } from '../../api/client'
import PageHeader from '../../components/PageHeader'

/** W2 – GET /api/staff: danh sách nhân viên active (id là staff.id) để WM chọn khi giao việc. */
export default function StaffPage() {
  const navigate = useNavigate()
  const [keyword, setKeyword] = useState('')
  const query = useQuery({
    queryKey: ['staff'],
    queryFn: ({ signal }) => api.get('/api/staff', undefined, signal),
  })

  const filteredStaff = useMemo(() => {
    const list = query.data || []
    if (!keyword.trim()) return list
    const kw = keyword.toLowerCase().trim()
    return list.filter((s) => s.fullName?.toLowerCase().includes(kw) || String(s.id).includes(kw))
  }, [query.data, keyword])

  const columns = [
    {
      title: 'Mã hồ sơ (staff.id)',
      dataIndex: 'id',
      width: 180,
      render: (v) => <Typography.Text code>#{v}</Typography.Text>,
    },
    {
      title: 'Họ và tên nhân viên',
      dataIndex: 'fullName',
      render: (v) => <Typography.Text strong>{v}</Typography.Text>,
    },
    {
      title: 'Vai trò trong hệ thống',
      key: 'role',
      width: 200,
      render: () => <Tag className="erp-tag erp-tag-role">Nhân viên sản xuất (STAFF)</Tag>,
    },
    {
      title: 'Trạng thái hoạt động',
      key: 'status',
      width: 180,
      render: () => <Tag className="erp-tag erp-tag-approved">Đang làm việc</Tag>,
    },
  ]

  return (
    <>
      <PageHeader
        title="Nhân viên sản xuất"
        subtitle="Danh sách nhân viên xưởng đang hoạt động dùng cho phân công lệnh sản xuất"
        extra={(
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => navigate('/admin/users')}
          >
            Tạo tài khoản Nhân viên
          </Button>
        )}
      />

      <Alert
        type="info"
        showIcon
        icon={<TeamOutlined />}
        style={{ marginBottom: 16 }}
        message="Quản lý hồ sơ nhân viên trong hệ thống MRP"
        description="Hồ sơ nhân viên (staff) được tạo tự động khi bạn tạo tài khoản người dùng có vai trò là NHÂN VIÊN (STAFF) tại mục Quản lý Tài khoản. Quản lý kho sẽ dùng mã hồ sơ nhân viên này để phân công các Yêu cầu sản xuất (Production Requests)."
      />

      <Card>
        <Space style={{ marginBottom: 16 }} wrap>
          <Input.Search
            allowClear
            style={{ width: 280 }}
            placeholder="Tìm theo họ tên hoặc mã..."
            onSearch={setKeyword}
            onChange={(e) => !e.target.value && setKeyword('')}
          />
          <Button
            icon={<ReloadOutlined />}
            loading={query.isFetching}
            onClick={() => query.refetch()}
          >
            Làm mới
          </Button>
        </Space>

        <Table
          rowKey="id"
          loading={query.isFetching}
          dataSource={filteredStaff}
          columns={columns}
          pagination={{ pageSize: 10, showTotal: (sum) => `Tổng số ${sum} nhân viên` }}
          locale={{ emptyText: 'Chưa có nhân viên nào đang hoạt động' }}
        />
      </Card>
    </>
  )
}

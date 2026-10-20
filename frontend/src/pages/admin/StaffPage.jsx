import { Card, Table, Tag } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { api } from '../../api/client'
import PageHeader from '../../components/PageHeader'

/** W2 – GET /api/staff: danh sách nhân viên active (id là staff.id) để WM chọn khi giao việc. */
export default function StaffPage() {
  const query = useQuery({ queryKey: ['staff'], queryFn: ({ signal }) => api.get('/api/staff', undefined, signal) })
  const columns = [
    { title: 'ID hồ sơ nhân viên', dataIndex: 'id', width: 180 },
    { title: 'Họ tên', dataIndex: 'fullName' },
    { title: 'Vai trò', key: 'role', width: 160, render: () => <Tag color="green">Nhân viên</Tag> },
  ]
  return (
    <>
      <PageHeader title="Nhân viên (giao việc)" subtitle="Danh sách STAFF đang hoạt động, dùng để quản lý kho chọn người thực hiện" />
      <Card>
        <Table rowKey="id" loading={query.isFetching} dataSource={query.data || []} columns={columns}
          pagination={{ pageSize: 10, showTotal: (sum) => `${sum} nhân viên` }} locale={{ emptyText: 'Không có dữ liệu' }} />
      </Card>
    </>
  )
}

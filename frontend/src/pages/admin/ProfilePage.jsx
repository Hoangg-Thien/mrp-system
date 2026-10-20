import { Alert, Card, Descriptions } from 'antd'
import PageHeader from '../../components/PageHeader'
import { RoleTag } from '../../components/StatusTag'
import { useAuth } from '../../auth/context'
import { emptyText } from '../../utils/statusMap'

/** W1 – Hồ sơ cá nhân (chỉ xem; backend P1 chưa có API sửa hồ sơ / đổi mật khẩu). */
export default function ProfilePage() {
  const { user } = useAuth()
  return (
    <>
      <PageHeader title="Hồ sơ cá nhân" subtitle="Thông tin tài khoản đang đăng nhập" />
      <Card>
        <Descriptions
          column={1}
          bordered
          size="middle"
          items={[
            { key: 'username', label: 'Tên đăng nhập', children: user.username },
            { key: 'fullName', label: 'Họ tên', children: emptyText(user.fullName) },
            { key: 'role', label: 'Vai trò', children: <RoleTag role={user.role} /> },
            { key: 'email', label: 'Email', children: emptyText(user.email) },
            { key: 'phone', label: 'Số điện thoại', children: emptyText(user.phone) },
            { key: 'address', label: 'Địa chỉ', children: emptyText(user.address) },
            { key: 'profileId', label: 'ID hồ sơ (profile)', children: emptyText(user.profileId) },
          ]}
        />
        <Alert className="inline-alert" type="info" showIcon message="Chức năng sửa thông tin và đổi mật khẩu"
          description="Backend phase 1 mới cung cấp GET /api/auth/me. Khi backend bổ sung API cập nhật hồ sơ, màn hình này sẽ mở form sửa tương ứng." />
      </Card>
    </>
  )
}

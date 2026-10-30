import { useState } from 'react'
import { App, Avatar, Button, Card, Col, Descriptions, Row, Space, Tag, Typography } from 'antd'
import { useQueryClient } from '@tanstack/react-query'
import {
  IdcardOutlined,
  MailOutlined,
  PhoneOutlined,
  SafetyCertificateOutlined,
  UserOutlined,
  EditOutlined,
  LockOutlined,
} from '@ant-design/icons'
import PageHeader from '../../components/PageHeader'
import { RoleTag } from '../../components/StatusTag'
import { useAuth } from '../../auth/context'
import { emptyText, roleLabels } from '../../utils/statusMap'
import { api } from '../../api/client'
import { useSingleMutation } from '../../api/useSingleMutation'
import FormModal from '../../components/FormModal'

const profileFields = [
  { name: 'fullName', label: 'Họ và tên', required: true, max: 150, rules: [{ whitespace: true, message: 'Vui lòng nhập họ và tên' }] },
  { name: 'email', label: 'Email', type: 'email', max: 150 },
  { name: 'phone', label: 'Số điện thoại', max: 20 },
  { name: 'address', label: 'Địa chỉ', type: 'textarea', max: 255 },
]
const passwordFields = [
  { name: 'currentPassword', label: 'Mật khẩu hiện tại', type: 'password', required: true, max: 100, autoComplete: 'current-password' },
  { name: 'newPassword', label: 'Mật khẩu mới', type: 'password', required: true, min: 8, max: 72,
    dependencies: ['currentPassword'], rules: [({ getFieldValue }) => ({ validator(_, value) {
      if (value && value === getFieldValue('currentPassword')) return Promise.reject(new Error('Mật khẩu mới phải khác mật khẩu hiện tại'))
      if (value && new TextEncoder().encode(value).length > 72) return Promise.reject(new Error('Mật khẩu mới quá dài, vui lòng giảm số ký tự'))
      return Promise.resolve()
    } })] },
  { name: 'confirmPassword', label: 'Nhập lại mật khẩu mới', type: 'password', required: true, max: 72,
    dependencies: ['newPassword'], rules: [({ getFieldValue }) => ({ validator(_, value) {
      return !value || value === getFieldValue('newPassword') ? Promise.resolve() : Promise.reject(new Error('Nhập lại mật khẩu mới không khớp'))
    } })] },
]

/** Hồ sơ cá nhân người dùng đang đăng nhập. */
export default function ProfilePage() {
  const { user, logout } = useAuth()
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const [editOpen, setEditOpen] = useState(false)
  const [passwordOpen, setPasswordOpen] = useState(false)
  const updateProfile = useSingleMutation({
    mutationFn: (values) => api.put('/api/auth/me', values),
    onSuccess: (updated) => {
      queryClient.setQueriesData({ queryKey: ['me'] }, updated)
      queryClient.invalidateQueries({ queryKey: ['users'] })
      queryClient.invalidateQueries({ queryKey: ['staff'] })
      setEditOpen(false)
      message.success('Đã cập nhật hồ sơ')
    },
    onError: (err) => message.error(err.message),
  })
  const changePassword = useSingleMutation({
    mutationFn: (values) => api.post('/api/auth/change-password', values),
    onSuccess: () => {
      setPasswordOpen(false)
      message.success('Đổi mật khẩu thành công. Vui lòng đăng nhập lại')
      logout()
    },
    onError: (err) => message.error(err.message),
  })
  if (!user) return null

  return (
    <>
      <PageHeader
        title="Hồ sơ cá nhân"
        subtitle="Thông tin tài khoản và phạm vi quyền hạn trong hệ thống"
        extra={<Space wrap>
          <Button icon={<EditOutlined />} type="primary" onClick={() => setEditOpen(true)}>Chỉnh sửa hồ sơ</Button>
          <Button icon={<LockOutlined />} onClick={() => setPasswordOpen(true)}>Đổi mật khẩu</Button>
        </Space>}
      />

      <Row gutter={[20, 20]}>
        <Col xs={24} md={8}>
          <Card style={{ textAlign: 'center' }}>
            <Avatar size={84} icon={<UserOutlined />} style={{ backgroundColor: '#10312a', marginBottom: 16 }} />
            <Typography.Title level={4} style={{ marginBottom: 4 }}>
              {user.fullName || user.username}
            </Typography.Title>
            <div style={{ marginBottom: 12 }}>
              <RoleTag role={user.role} />
            </div>
            <Typography.Text type="secondary" style={{ display: 'block', fontSize: 13 }}>
              Tên đăng nhập: <strong>{user.username}</strong>
            </Typography.Text>
            {user.profileId && (
              <Tag className="erp-tag erp-tag-neutral">
                Mã hồ sơ: #{user.profileId}
              </Tag>
            )}
          </Card>
        </Col>

        <Col xs={24} md={16}>
          <Card title="Thông tin chi tiết">
            <Descriptions column={1} bordered size="middle">
              <Descriptions.Item label={<Space><UserOutlined /> Tên đăng nhập</Space>}>
                <Typography.Text copyable>{user.username}</Typography.Text>
              </Descriptions.Item>
              <Descriptions.Item label={<Space><IdcardOutlined /> Họ và tên</Space>}>
                {emptyText(user.fullName)}
              </Descriptions.Item>
              <Descriptions.Item label={<Space><SafetyCertificateOutlined /> Vai trò</Space>}>
                {roleLabels[user.role] || user.role}
              </Descriptions.Item>
              <Descriptions.Item label={<Space><MailOutlined /> Địa chỉ Email</Space>}>
                {emptyText(user.email)}
              </Descriptions.Item>
              <Descriptions.Item label={<Space><PhoneOutlined /> Số điện thoại</Space>}>
                {emptyText(user.phone)}
              </Descriptions.Item>
              <Descriptions.Item label="Địa chỉ liên hệ">
                {emptyText(user.address)}
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>
      </Row>
      <FormModal title="Chỉnh sửa hồ sơ" open={editOpen} fields={profileFields} initialValues={user}
        confirmLoading={updateProfile.isPending} onCancel={() => setEditOpen(false)} onSubmit={updateProfile.mutate} />
      <FormModal title="Đổi mật khẩu" open={passwordOpen} fields={passwordFields}
        confirmLoading={changePassword.isPending} onCancel={() => setPasswordOpen(false)} onSubmit={changePassword.mutate} />
    </>
  )
}

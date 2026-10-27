import { Avatar, Card, Col, Descriptions, Row, Space, Tag, Typography } from 'antd'
import {
  IdcardOutlined,
  MailOutlined,
  PhoneOutlined,
  SafetyCertificateOutlined,
  UserOutlined,
} from '@ant-design/icons'
import PageHeader from '../../components/PageHeader'
import { RoleTag } from '../../components/StatusTag'
import { useAuth } from '../../auth/context'
import { emptyText, roleLabels } from '../../utils/statusMap'

/** Hồ sơ cá nhân người dùng đang đăng nhập. */
export default function ProfilePage() {
  const { user } = useAuth()
  if (!user) return null

  return (
    <>
      <PageHeader
        title="Hồ sơ cá nhân"
        subtitle="Thông tin chi tiết về tài khoản và quyền hạn trong hệ thống MRP"
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
    </>
  )
}

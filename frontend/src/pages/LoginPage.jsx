import { useState } from 'react'
import { Alert, Button, Card, Form, Input, Typography } from 'antd'
import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/context'
import { homeByRole } from '../utils/statusMap'
import AuthLayout from '../layouts/AuthLayout'

/** Đăng nhập: gọi API lấy JWT rồi điều hướng theo đúng vai trò trả về từ backend. */
export default function LoginPage() {
  const { login, user } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  if (user) return <Navigate to={homeByRole[user.role] || '/login'} replace />

  const onFinish = async (values) => {
    setLoading(true)
    setError(null)
    try {
      const profile = await login(values)
      navigate(homeByRole[profile.role] || '/login', { replace: true })
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthLayout>
      <Card className="login-card" title="Đăng nhập">
        {error && <Alert type="error" showIcon message={error} className="login-alert" />}
        <Form layout="vertical" onFinish={onFinish} requiredMark={false}>
          <Form.Item name="username" label="Tên đăng nhập" rules={[{ required: true, message: 'Vui lòng nhập tên đăng nhập' }]}>
            <Input prefix={<UserOutlined />} placeholder="username" autoComplete="username" size="large" />
          </Form.Item>
          <Form.Item name="password" label="Mật khẩu" rules={[{ required: true, message: 'Vui lòng nhập mật khẩu' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="••••••••" autoComplete="current-password" size="large" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block size="large" loading={loading}>Đăng nhập</Button>
        </Form>
        <Typography.Paragraph className="login-hint">
          Tài khoản do quản trị viên cấp. Admin đầu tiên được backend tạo từ <code>BOOTSTRAP_ADMIN_USERNAME</code> / <code>BOOTSTRAP_ADMIN_PASSWORD</code>.
        </Typography.Paragraph>
      </Card>
    </AuthLayout>
  )
}

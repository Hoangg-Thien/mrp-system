import { Button, Result } from 'antd'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/context'
import { homeByRole } from '../utils/statusMap'

export default function NotFoundPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  return (
    <Result
      status="404"
      title="404"
      subTitle="Không tìm thấy trang bạn yêu cầu"
      extra={<Button type="primary" onClick={() => navigate(user ? homeByRole[user.role] : '/login')}>Về trang chủ</Button>}
    />
  )
}

import { Navigate } from 'react-router-dom'
import { useAuth } from './context'
import { homeByRole } from '../utils/statusMap'

/**
 * Chặn theo khu vực: chưa đăng nhập -> /login; đăng nhập nhưng sai role -> về
 * đúng khu vực của role đó (plan mục 3).
 */
export default function RequireRole({ role, children }) {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  if (role && user.role !== role) return <Navigate to={homeByRole[user.role] || '/login'} replace />
  return children
}

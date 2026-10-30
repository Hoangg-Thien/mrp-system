import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Avatar, Button, Layout, Menu } from 'antd'
import { LogoutOutlined, ShopOutlined, UserOutlined } from '@ant-design/icons'
import { useAuth } from '../auth/context'
import { roleLabels } from '../utils/statusMap'
import { useConfirm } from '../utils/confirm'

/**
 * Khung chung cho khu vực WM / STAFF. Menu nhận từ `menu` để mở dần theo phase backend
 * (W3–W7); hiện backend mới có P1/P2 nên hai khu vực này đang ở trạng thái chờ.
 */
export default function AreaLayout({ areaKey, title, menu = [] }) {
  const { confirmLogout } = useConfirm()
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const items = [{ key: `/${areaKey}`, icon: <ShopOutlined />, label: <NavLink to={`/${areaKey}`}>{title}</NavLink> }, ...menu]
  const selected = [location.pathname]
  const handleLogout = () => confirmLogout(() => { logout(); navigate('/login') })

  return (
    <Layout className="app-shell">
      <Layout.Sider width={250} className="app-sider">
        <div className="sider-brand">
          <span className="brand-logo-icon">MRP</span>
          <span className="brand-text">ĐIỀU HÀNH CHUNG</span>
        </div>
        <Menu theme="dark" mode="inline" inlineIndent={16} selectedKeys={selected} items={items} />
        <div className="sider-footer">
          <div className="sider-user-card" onClick={() => navigate(`/${areaKey}`)} title="Tài khoản">
            <Avatar icon={<UserOutlined />} className="user-avatar" />
            <div className="sider-user-info">
              <span className="sider-user-name">{user.fullName}</span>
              <span className="sider-user-role">{roleLabels[user.role]}</span>
            </div>
            <Button
              type="text"
              icon={<LogoutOutlined />}
              className="sider-logout-btn"
              title="Đăng xuất"
              onClick={(e) => {
                e.stopPropagation()
                handleLogout()
              }}
            />
          </div>
        </div>
      </Layout.Sider>
      <Layout>
        <Layout.Header className="app-header">
          <span className="header-title">{title}</span>
        </Layout.Header>
        <Layout.Content className="app-content">
          <Outlet />
        </Layout.Content>
      </Layout>
    </Layout>
  )
}

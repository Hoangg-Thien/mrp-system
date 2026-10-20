import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Avatar, Button, Dropdown, Layout, Menu, Tag } from 'antd'
import { LogoutOutlined, ShopOutlined, UserOutlined } from '@ant-design/icons'
import { useAuth } from '../auth/context'
import { roleLabels } from '../utils/statusMap'

/**
 * Khung chung cho khu vực WM / STAFF. Menu nhận từ `menu` để mở dần theo phase backend
 * (W3–W7); hiện backend mới có P1/P2 nên hai khu vực này đang ở trạng thái chờ.
 */
export default function AreaLayout({ areaKey, title, menu = [] }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const items = [{ key: `/${areaKey}`, icon: <ShopOutlined />, label: <NavLink to={`/${areaKey}`}>{title}</NavLink> }, ...menu]
  const selected = [location.pathname]

  return (
    <Layout className="app-shell">
      <Layout.Sider width={232} className="app-sider">
        <div className="sider-brand">MRP SYSTEM</div>
        <Menu theme="dark" mode="inline" selectedKeys={selected} items={items} />
      </Layout.Sider>
      <Layout>
        <Layout.Header className="app-header">
          <span className="header-title">{title}</span>
          <div className="header-right">
            <Dropdown
              menu={{
                items: [
                  { key: 'logout', icon: <LogoutOutlined />, label: 'Đăng xuất', onClick: () => { logout(); navigate('/login') } },
                ],
              }}
            >
              <Button type="text" className="user-btn">
                <Avatar size="small" icon={<UserOutlined />} />
                <span className="user-name">{user.fullName}</span>
                <Tag color="blue">{roleLabels[user.role]}</Tag>
              </Button>
            </Dropdown>
          </div>
        </Layout.Header>
        <Layout.Content className="app-content">
          <Outlet />
        </Layout.Content>
      </Layout>
    </Layout>
  )
}

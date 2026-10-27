import { useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Avatar, Button, Dropdown, Layout, Menu, Tooltip } from 'antd'
import {
  AuditOutlined,
  DashboardOutlined,
  FieldTimeOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ShoppingCartOutlined,
  ShoppingOutlined,
  ToolOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { useAuth } from '../auth/context'
import { roleLabels } from '../utils/statusMap'

/** Khu vực NHÂN VIÊN (STAFF) – tách biệt hoàn toàn với ADMIN và WM (plan mục 3.3). */
export default function StaffLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [collapsed, setCollapsed] = useState(false)

  const items = [
    {
      type: 'group',
      label: 'BÀN LÀM VIỆC',
      children: [
        { key: '/staff', icon: <DashboardOutlined />, label: <NavLink to="/staff">Bàn làm việc</NavLink> },
      ],
    },
    {
      type: 'group',
      label: 'QUY TRÌNH',
      children: [
        {
          key: 'production-group',
          icon: <ToolOutlined />,
          label: 'Sản xuất',
          children: [
            { key: '/staff/requests', icon: <AuditOutlined />, label: <NavLink to="/staff/requests">Yêu cầu SX của tôi</NavLink> },
            { key: '/staff/material-requests', icon: <FieldTimeOutlined />, label: <NavLink to="/staff/material-requests">Đề xuất xuất NVL</NavLink> },
            { key: '/staff/productions', icon: <ToolOutlined />, label: <NavLink to="/staff/productions">Thực hiện sản xuất</NavLink> },
          ],
        },
        {
          key: 'sales-group',
          icon: <ShoppingOutlined />,
          label: 'Bán hàng',
          children: [
            { key: '/staff/fg-requests', icon: <ShoppingCartOutlined />, label: <NavLink to="/staff/fg-requests">Xuất bán thành phẩm</NavLink> },
          ],
        },
      ],
    },
    {
      type: 'group',
      label: 'CÀI ĐẶT',
      children: [
        {
          key: '/staff/profile',
          icon: <UserOutlined />,
          label: <NavLink to="/staff/profile">Hồ sơ cá nhân</NavLink>,
        },
      ],
    },
  ]

  const selected = [location.pathname]

  return (
    <Layout className="app-shell">
      <Layout.Sider collapsible collapsed={collapsed} trigger={null} width={250} className="app-sider">
        <div className="sider-brand">
          <span className="brand-logo-icon">M</span>
          <span className="brand-text">MRP SYSTEM</span>
        </div>
        <Menu
          theme="dark"
          mode="inline"
          inlineIndent={16}
          selectedKeys={selected}
          items={items}
        />
        <div className="sider-footer">
          {collapsed ? (
            <Dropdown
              placement="topRight"
              menu={{
                items: [
                  {
                    key: 'user-info',
                    disabled: true,
                    label: (
                      <div style={{ padding: '2px 0' }}>
                        <div style={{ fontWeight: 600, color: '#1f2937' }}>{user.fullName}</div>
                        <div style={{ fontSize: 12, color: '#6b7280' }}>{roleLabels[user.role]}</div>
                      </div>
                    ),
                  },
                  { type: 'divider' },
                  { key: 'profile', icon: <UserOutlined />, label: 'Hồ sơ cá nhân', onClick: () => navigate('/staff/profile') },
                  { key: 'logout', icon: <LogoutOutlined />, label: 'Đăng xuất', danger: true, onClick: () => { logout(); navigate('/login') } },
                ],
              }}
            >
              <div className="sider-user-card-collapsed" title={`${user.fullName} (${roleLabels[user.role]})`}>
                <Avatar icon={<UserOutlined />} className="user-avatar" />
              </div>
            </Dropdown>
          ) : (
            <div className="sider-user-card" onClick={() => navigate('/staff/profile')} title="Xem hồ sơ cá nhân">
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
                  logout()
                  navigate('/login')
                }}
              />
            </div>
          )}
        </div>
      </Layout.Sider>
      <Layout>
        <Layout.Header className="app-header">
          <div className="header-left">
            <Tooltip title={collapsed ? 'Mở rộng sidebar' : 'Thu gọn sidebar'}>
              <Button
                type="text"
                className="collapse-btn"
                icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                onClick={() => setCollapsed(!collapsed)}
              />
            </Tooltip>
            <span className="header-title">Phân hệ Nhân viên Sản xuất</span>
          </div>
        </Layout.Header>
        <Layout.Content className="app-content">
          <Outlet />
        </Layout.Content>
        <Layout.Footer className="app-footer">MRP System – Phân hệ Nhân viên</Layout.Footer>
      </Layout>
    </Layout>
  )
}


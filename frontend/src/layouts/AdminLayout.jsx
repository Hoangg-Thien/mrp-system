import { useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Avatar, Button, Dropdown, Layout, Menu, Tooltip } from 'antd'
import {
  AppstoreOutlined,
  BarChartOutlined,
  DashboardOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ProfileOutlined,
  TableOutlined,
  TagsOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { useAuth } from '../auth/context'
import { roleLabels } from '../utils/statusMap'
import { confirmLogout } from '../utils/confirm'

/** Khu vực ADMIN – tách biệt hoàn toàn với /warehouse và /staff (plan mục 3.1). */
export default function AdminLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [collapsed, setCollapsed] = useState(false)
  const handleLogout = () => confirmLogout(() => { logout(); navigate('/login') })

  const items = [
    {
      type: 'group',
      label: 'HỆ THỐNG',
      children: [
        { key: '/admin', icon: <DashboardOutlined />, label: <NavLink to="/admin">Tổng quan</NavLink> },
        { key: '/admin/users', icon: <TeamOutlined />, label: <NavLink to="/admin/users">Tài khoản</NavLink> },
      ],
    },
    {
      type: 'group',
      label: 'DỮ LIỆU & BÁO CÁO',
      children: [
        {
          key: 'catalog',
          icon: <AppstoreOutlined />,
          label: 'Danh mục dữ liệu',
          children: [
            { key: '/admin/categories', icon: <TagsOutlined />, label: <NavLink to="/admin/categories">Danh mục</NavLink> },
            { key: '/admin/products', icon: <TableOutlined />, label: <NavLink to="/admin/products">Sản phẩm</NavLink> },
            { key: '/admin/suppliers', icon: <ProfileOutlined />, label: <NavLink to="/admin/suppliers">Nhà cung cấp</NavLink> },
            { key: '/admin/materials', icon: <AppstoreOutlined />, label: <NavLink to="/admin/materials">Nguyên vật liệu</NavLink> },
            { key: '/admin/staff', icon: <TeamOutlined />, label: <NavLink to="/admin/staff">Nhân viên sản xuất</NavLink> },
          ],
        },
        { key: '/admin/reports', icon: <BarChartOutlined />, label: <NavLink to="/admin/reports">Báo cáo kho</NavLink> },
      ],
    },
    {
      type: 'group',
      label: 'CÀI ĐẶT',
      children: [
        { key: '/admin/profile', icon: <UserOutlined />, label: <NavLink to="/admin/profile">Hồ sơ cá nhân</NavLink> },
      ],
    },
  ]
  const selected = [location.pathname]

  return (
    <Layout className="app-shell">
      <Layout.Sider collapsible collapsed={collapsed} trigger={null} width={250} className="app-sider">
        <div className="sider-brand">
          <span className="brand-logo-icon">QT</span>
          <span className="brand-text">QUẢN TRỊ HỆ THỐNG</span>
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
                  { key: 'profile', icon: <UserOutlined />, label: 'Hồ sơ cá nhân', onClick: () => navigate('/admin/profile') },
                  { key: 'logout', icon: <LogoutOutlined />, label: 'Đăng xuất', danger: true, onClick: handleLogout },
                ],
              }}
            >
              <div className="sider-user-card-collapsed" title={`${user.fullName} (${roleLabels[user.role]})`}>
                <Avatar icon={<UserOutlined />} className="user-avatar" />
              </div>
            </Dropdown>
          ) : (
            <div className="sider-user-card" onClick={() => navigate('/admin/profile')} title="Xem hồ sơ cá nhân">
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
            <span className="header-title">Hệ thống Quản lý Kế hoạch Sản xuất & Kho vận</span>
          </div>
        </Layout.Header>
        <Layout.Content className="app-content">
          <Outlet />
        </Layout.Content>
        <Layout.Footer className="app-footer">Khu vực Quản trị Hệ thống</Layout.Footer>
      </Layout>
    </Layout>
  )
}

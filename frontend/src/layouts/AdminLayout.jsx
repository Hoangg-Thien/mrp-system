import { useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Avatar, Button, Dropdown, Layout, Menu, Tag } from 'antd'
import {
  AppstoreOutlined,
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

/** Khu vực ADMIN – tách biệt hoàn toàn với /warehouse và /staff (plan mục 3.1). */
export default function AdminLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [collapsed, setCollapsed] = useState(false)

  const items = [
    { key: '/admin', icon: <DashboardOutlined />, label: <NavLink to="/admin">Tổng quan</NavLink> },
    { key: '/admin/users', icon: <TeamOutlined />, label: <NavLink to="/admin/users">Tài khoản</NavLink> },
    {
      key: 'catalog',
      icon: <AppstoreOutlined />,
      label: 'Danh mục dữ liệu',
      children: [
        { key: '/admin/categories', icon: <TagsOutlined />, label: <NavLink to="/admin/categories">Danh mục</NavLink> },
        { key: '/admin/products', icon: <TableOutlined />, label: <NavLink to="/admin/products">Sản phẩm</NavLink> },
        { key: '/admin/suppliers', icon: <ProfileOutlined />, label: <NavLink to="/admin/suppliers">Nhà cung cấp</NavLink> },
        { key: '/admin/materials', icon: <AppstoreOutlined />, label: <NavLink to="/admin/materials">Nguyên vật liệu</NavLink> },
        { key: '/admin/staff', icon: <TeamOutlined />, label: <NavLink to="/admin/staff">Nhân viên (giao việc)</NavLink> },
      ],
    },
    { key: '/admin/profile', icon: <UserOutlined />, label: <NavLink to="/admin/profile">Hồ sơ cá nhân</NavLink> },
  ]
  const selected = [location.pathname]

  return (
    <Layout className="app-shell">
      <Layout.Sider collapsible collapsed={collapsed} trigger={null} width={232} className="app-sider">
        <div className="sider-brand">{collapsed ? 'M' : 'MRP SYSTEM'}</div>
        <Menu theme="dark" mode="inline" selectedKeys={selected} defaultOpenKeys={['catalog']} items={items} />
      </Layout.Sider>
      <Layout>
        <Layout.Header className="app-header">
          <Button
            type="text"
            className="collapse-btn"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed(!collapsed)}
          />
          <div className="header-right">
            <Dropdown
              menu={{
                items: [
                  { key: 'profile', icon: <UserOutlined />, label: 'Hồ sơ cá nhân', onClick: () => navigate('/admin/profile') },
                  { type: 'divider' },
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
        <Layout.Footer className="app-footer">MRP System – Khu vực quản trị</Layout.Footer>
      </Layout>
    </Layout>
  )
}

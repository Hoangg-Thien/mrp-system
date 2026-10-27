import { useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Avatar, Button, Dropdown, Layout, Menu, Tooltip } from 'antd'
import {
  AuditOutlined,
  BarChartOutlined,
  CalculatorOutlined,
  CheckCircleOutlined,
  CheckSquareOutlined,
  DashboardOutlined,
  FieldTimeOutlined,
  FileDoneOutlined,
  HistoryOutlined,
  InboxOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ShopOutlined,
  ShoppingCartOutlined,
  SolutionOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { useAuth } from '../auth/context'
import { roleLabels } from '../utils/statusMap'
import { confirmLogout } from '../utils/confirm'

/** Khu vực QUẢN LÝ KHO (WM) – tách biệt với ADMIN và STAFF (plan mục 3.2). */
export default function WarehouseLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [collapsed, setCollapsed] = useState(false)
  const handleLogout = () => confirmLogout(() => { logout(); navigate('/login') })

  const items = [
    {
      type: 'group',
      label: 'TỔNG QUAN',
      children: [
        { key: '/warehouse', icon: <DashboardOutlined />, label: <NavLink to="/warehouse">Tổng quan kho</NavLink> },
      ],
    },
    {
      type: 'group',
      label: 'NGHIỆP VỤ KHO',
      children: [
        {
          key: 'inventory-group',
          icon: <InboxOutlined />,
          label: 'Quản lý kho',
          children: [
            { key: '/warehouse/inventory', icon: <ShopOutlined />, label: <NavLink to="/warehouse/inventory">Tồn kho & Kiểm kê</NavLink> },
            { key: '/warehouse/movements', icon: <HistoryOutlined />, label: <NavLink to="/warehouse/movements">Sổ kho biến động</NavLink> },
            { key: '/warehouse/receipts', icon: <ShoppingCartOutlined />, label: <NavLink to="/warehouse/receipts">Phiếu nhập kho</NavLink> },
          ],
        },
        {
          key: 'planning-group',
          icon: <SolutionOutlined />,
          label: 'Kế hoạch & Sản xuất',
          children: [
            { key: '/warehouse/boms', icon: <FileDoneOutlined />, label: <NavLink to="/warehouse/boms">Công thức BOM</NavLink> },
            { key: '/warehouse/planning', icon: <CalculatorOutlined />, label: <NavLink to="/warehouse/planning">Kế hoạch MRP</NavLink> },
            { key: '/warehouse/production-requests', icon: <AuditOutlined />, label: <NavLink to="/warehouse/production-requests">Yêu cầu sản xuất</NavLink> },
          ],
        },
        {
          key: 'approvals-group',
          icon: <CheckCircleOutlined />,
          label: 'Phê duyệt xuất kho',
          children: [
            { key: '/warehouse/material-issues', icon: <FieldTimeOutlined />, label: <NavLink to="/warehouse/material-issues">Duyệt xuất NVL</NavLink> },
            { key: '/warehouse/fg-issues', icon: <CheckSquareOutlined />, label: <NavLink to="/warehouse/fg-issues">Duyệt xuất TP</NavLink> },
          ],
        },
      ],
    },
    {
      type: 'group',
      label: 'BÁO CÁO & CÀI ĐẶT',
      children: [
        {
          key: '/warehouse/reports',
          icon: <BarChartOutlined />,
          label: <NavLink to="/warehouse/reports">Báo cáo xuất nhập tồn</NavLink>,
        },
        {
          key: '/warehouse/profile',
          icon: <UserOutlined />,
          label: <NavLink to="/warehouse/profile">Hồ sơ cá nhân</NavLink>,
        },
      ],
    },
  ]

  const selected = [location.pathname]

  return (
    <Layout className="app-shell">
      <Layout.Sider collapsible collapsed={collapsed} trigger={null} width={250} className="app-sider">
        <div className="sider-brand">
          <span className="brand-logo-icon">KHO</span>
          <span className="brand-text">QUẢN LÝ KHO</span>
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
                  { key: 'profile', icon: <UserOutlined />, label: 'Hồ sơ cá nhân', onClick: () => navigate('/warehouse/profile') },
                  { key: 'logout', icon: <LogoutOutlined />, label: 'Đăng xuất', danger: true, onClick: handleLogout },
                ],
              }}
            >
              <div className="sider-user-card-collapsed" title={`${user.fullName} (${roleLabels[user.role]})`}>
                <Avatar icon={<UserOutlined />} className="user-avatar" />
              </div>
            </Dropdown>
          ) : (
            <div className="sider-user-card" onClick={() => navigate('/warehouse/profile')} title="Xem hồ sơ cá nhân">
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
            <span className="header-title">Phân hệ Quản lý Kho</span>
          </div>
        </Layout.Header>
        <Layout.Content className="app-content">
          <Outlet />
        </Layout.Content>
        <Layout.Footer className="app-footer">Phân hệ Quản lý Kho – Chuỗi cung ứng &amp; Tồn kho</Layout.Footer>
      </Layout>
    </Layout>
  )
}


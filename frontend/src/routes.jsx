import { Suspense, lazy } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { Spin } from 'antd'
import RequireRole from './auth/RequireRole'
import { useAuth } from './auth/context'
import { homeByRole } from './utils/statusMap'
import AdminLayout from './layouts/AdminLayout'
import AreaLayout from './layouts/AreaLayout'

const LoginPage = lazy(() => import('./pages/LoginPage'))
const NotFoundPage = lazy(() => import('./pages/NotFoundPage'))
const PendingPage = lazy(() => import('./pages/PendingPage'))
const DashboardPage = lazy(() => import('./pages/admin/DashboardPage'))
const UsersPage = lazy(() => import('./pages/admin/UsersPage'))
const ProfilePage = lazy(() => import('./pages/admin/ProfilePage'))
const CategoriesPage = lazy(() => import('./pages/admin/CategoriesPage'))
const ProductsPage = lazy(() => import('./pages/admin/ProductsPage'))
const SuppliersPage = lazy(() => import('./pages/admin/SuppliersPage'))
const MaterialsPage = lazy(() => import('./pages/admin/MaterialsPage'))
const StaffPage = lazy(() => import('./pages/admin/StaffPage'))

/** Màn hình chờ hiển thị trong khu vực WM/STAFF đến khi backend P3–P9 hoàn tất. */
const warehousePhases = [
  { phase: 2, label: 'P2 – Danh mục dữ liệu (backend đã có)', detail: 'Sản phẩm, nguyên vật liệu, nhà cung cấp do quản trị viên cấu hình.' },
  { phase: 3, label: 'P3 – Quản lý kho (frontend W3)', detail: 'Tồn kho, thẻ kho, lịch sử nhập xuất, điều chỉnh.' },
  { phase: 4, label: 'P4 – Phiếu nhập kho (frontend W4)', detail: 'Tạo phiếu nhập nhiều dòng NVL, hoàn tất, hủy.' },
  { phase: 6, label: 'P6 – Kế hoạch MRP (frontend W5–W6)', detail: 'Phê duyệt kế hoạch, yêu cầu sản xuất, duyệt phiếu xuất NVL.' },
  { phase: 8, label: 'P8 – Xuất kho thành phẩm (frontend W6)', detail: 'Duyệt phiếu xuất thành phẩm do nhân viên lập.' },
]

const staffPhases = [
  { phase: 2, label: 'P2 – Danh mục dữ liệu (backend đã có)', detail: 'Xem sản phẩm, nguyên vật liệu.' },
  { phase: 5, label: 'P5 – BOM (frontend W4)', detail: 'Xem cấu trúc sản phẩm và định mức nguyên vật liệu.' },
  { phase: 7, label: 'P7 – Sản xuất (frontend W5–W6)', detail: 'Nhận việc, yêu cầu xuất NVL, báo cáo sản xuất, lập phiếu xuất thành phẩm.' },
  { phase: 9, label: 'P9 – Báo cáo (frontend W7)', detail: 'Thống kê tồn kho, nhập xuất, sản xuất theo tháng/quý/năm.' },
]

function HomeRedirect() {
  const { user } = useAuth()
  return <Navigate to={user ? homeByRole[user.role] : '/login'} replace />
}

/** Ba khu vực tách biệt theo role; vào sai khu vực sẽ bị điều hướng về khu vực của mình. */
export default function AppRoutes() {
  return (
    <Suspense fallback={<div className="center-screen"><Spin size="large" /></div>}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<HomeRedirect />} />

        <Route path="/admin" element={<RequireRole role="ADMIN"><AdminLayout /></RequireRole>}>
          <Route index element={<DashboardPage />} />
          <Route path="users" element={<UsersPage />} />
          <Route path="profile" element={<ProfilePage />} />
          <Route path="categories" element={<CategoriesPage />} />
          <Route path="products" element={<ProductsPage />} />
          <Route path="suppliers" element={<SuppliersPage />} />
          <Route path="materials" element={<MaterialsPage />} />
          <Route path="staff" element={<StaffPage />} />
        </Route>

        <Route path="/warehouse" element={<RequireRole role="WAREHOUSE_MANAGER"><AreaLayout areaKey="warehouse" title="Quản lý kho" /></RequireRole>}>
          <Route index element={<PendingPage area="quản lý kho" items={warehousePhases} />} />
        </Route>

        <Route path="/staff" element={<RequireRole role="STAFF"><AreaLayout areaKey="staff" title="Khu vực nhân viên" /></RequireRole>}>
          <Route index element={<PendingPage area="nhân viên" items={staffPhases} />} />
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  )
}

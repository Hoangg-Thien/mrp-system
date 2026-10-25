import { Suspense, lazy } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { Spin } from 'antd'
import RequireRole from './auth/RequireRole'
import { useAuth } from './auth/context'
import { homeByRole } from './utils/statusMap'
import AdminLayout from './layouts/AdminLayout'
import WarehouseLayout from './layouts/WarehouseLayout'
import StaffLayout from './layouts/StaffLayout'

// Common & Auth
const LoginPage = lazy(() => import('./pages/LoginPage'))
const NotFoundPage = lazy(() => import('./pages/NotFoundPage'))

// Admin Pages
const DashboardPage = lazy(() => import('./pages/admin/DashboardPage'))
const UsersPage = lazy(() => import('./pages/admin/UsersPage'))
const ProfilePage = lazy(() => import('./pages/admin/ProfilePage'))
const CategoriesPage = lazy(() => import('./pages/admin/CategoriesPage'))
const ProductsPage = lazy(() => import('./pages/admin/ProductsPage'))
const SuppliersPage = lazy(() => import('./pages/admin/SuppliersPage'))
const MaterialsPage = lazy(() => import('./pages/admin/MaterialsPage'))
const StaffPage = lazy(() => import('./pages/admin/StaffPage'))

// Warehouse Manager Pages
const WarehouseDashboard = lazy(() => import('./pages/warehouse/WarehouseDashboard'))
const InventoryPage = lazy(() => import('./pages/warehouse/InventoryPage'))
const MovementsPage = lazy(() => import('./pages/warehouse/MovementsPage'))
const PurchaseReceiptsPage = lazy(() => import('./pages/warehouse/PurchaseReceiptsPage'))
const BomsPage = lazy(() => import('./pages/warehouse/BomsPage'))
const MrpPlanningPage = lazy(() => import('./pages/warehouse/MrpPlanningPage'))
const ProductionRequestsPage = lazy(() => import('./pages/warehouse/ProductionRequestsPage'))
const MaterialIssuesPage = lazy(() => import('./pages/warehouse/MaterialIssuesPage'))
const FgIssuesPage = lazy(() => import('./pages/warehouse/FgIssuesPage'))
const StockReportsPage = lazy(() => import('./pages/warehouse/StockReportsPage'))

// Staff Pages
const StaffDashboard = lazy(() => import('./pages/staff/StaffDashboard'))
const StaffProductionRequestsPage = lazy(() => import('./pages/staff/StaffProductionRequestsPage'))
const StaffMaterialRequestsPage = lazy(() => import('./pages/staff/StaffMaterialRequestsPage'))
const StaffProductionsPage = lazy(() => import('./pages/staff/StaffProductionsPage'))
const StaffFgRequestsPage = lazy(() => import('./pages/staff/StaffFgRequestsPage'))

function HomeRedirect() {
  const { user } = useAuth()
  return <Navigate to={user ? homeByRole[user.role] : '/login'} replace />
}

/** Ba khu vực tách biệt hoàn toàn theo role; vào sai khu vực sẽ bị điều hướng về khu vực của mình. */
export default function AppRoutes() {
  return (
    <Suspense fallback={<div className="center-screen"><Spin size="large" /></div>}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<HomeRedirect />} />

        {/* 1. ADMIN AREA */}
        <Route path="/admin" element={<RequireRole role="ADMIN"><AdminLayout /></RequireRole>}>
          <Route index element={<DashboardPage />} />
          <Route path="users" element={<UsersPage />} />
          <Route path="profile" element={<ProfilePage />} />
          <Route path="categories" element={<CategoriesPage />} />
          <Route path="products" element={<ProductsPage />} />
          <Route path="suppliers" element={<SuppliersPage />} />
          <Route path="materials" element={<MaterialsPage />} />
          <Route path="staff" element={<StaffPage />} />
          <Route path="reports" element={<StockReportsPage />} />
        </Route>

        {/* 2. WAREHOUSE MANAGER AREA */}
        <Route path="/warehouse" element={<RequireRole role="WAREHOUSE_MANAGER"><WarehouseLayout /></RequireRole>}>
          <Route index element={<WarehouseDashboard />} />
          <Route path="inventory" element={<InventoryPage />} />
          <Route path="movements" element={<MovementsPage />} />
          <Route path="receipts" element={<PurchaseReceiptsPage />} />
          <Route path="boms" element={<BomsPage />} />
          <Route path="planning" element={<MrpPlanningPage />} />
          <Route path="production-requests" element={<ProductionRequestsPage />} />
          <Route path="material-issues" element={<MaterialIssuesPage />} />
          <Route path="fg-issues" element={<FgIssuesPage />} />
          <Route path="reports" element={<StockReportsPage />} />
          <Route path="profile" element={<ProfilePage />} />
        </Route>

        {/* 3. STAFF AREA */}
        <Route path="/staff" element={<RequireRole role="STAFF"><StaffLayout /></RequireRole>}>
          <Route index element={<StaffDashboard />} />
          <Route path="requests" element={<StaffProductionRequestsPage />} />
          <Route path="material-requests" element={<StaffMaterialRequestsPage />} />
          <Route path="productions" element={<StaffProductionsPage />} />
          <Route path="fg-requests" element={<StaffFgRequestsPage />} />
          <Route path="profile" element={<ProfilePage />} />
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  )
}

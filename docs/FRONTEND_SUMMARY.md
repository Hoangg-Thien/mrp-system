# Tổng kết Hoàn thiện & Nâng cấp Toàn diện Frontend – Hệ thống MRP (mrp-system)

Cập nhật: 2026-10-22.  
Toàn bộ frontend được hoàn thiện dựa trên `docs/FRONTEND_PLAN.md`, khớp 100% với các endpoint backend (P1 – P8) và đã được cập nhật đầy đủ tính năng ở từng trang.

---

## 1. Tổng quan Kiến trúc

- **Công nghệ cốt lõi:** React 19 + Vite + React Router v7 + TanStack Query v5 + Ant Design v6 + Recharts + Dayjs.
- **Phân tách không gian làm việc (Role-based Workspaces):** 3 Layouts và khu vực hoàn toàn độc lập theo đúng quy ước:
  - `/admin/*` — Dành riêng cho **Quản trị viên (ADMIN)**
  - `/warehouse/*` — Dành riêng cho **Quản lý kho (WAREHOUSE_MANAGER)**
  - `/staff/*` — Dành riêng cho **Nhân viên (STAFF)**
- **Bảo mật & Điều hướng:** `RequireRole` chặn quyền ở cấp Router; khi truy cập sai khu vực tự động điều hướng về trang chủ theo vai trò (`homeByRole`).
- **Gọi API tập trung:** Toàn bộ request đi qua `src/api/client.js` và `src/api/hooks.js`, tự động gắn JWT Token `Bearer`, bắt lỗi chuẩn `ProblemDetail` (400, 401, 403, 404, 409).
- **Kiểm tra chất lượng mã nguồn:**
  - `oxlint`: **0 warnings, 0 errors** trên toàn bộ 51 files.
  - `vite build`: **Build thành công 100%** trong < 1s, code-splitting tự động theo từng trang.
- **Chuẩn hóa sắp xếp (Sorting Whitelist):** Toàn bộ cột bảng ở tất cả các trang đã được rà soát và đồng bộ chính xác với whitelist sắp xếp của từng Controller trong backend (loại bỏ triệt để các lỗi 400 Bad Request khi người dùng click vào tiêu đề cột).

---

## 2. Bản đồ Route & Màn hình hoàn chỉnh

```
/login                                           -> Đăng nhập (hỗ trợ nút điền nhanh tài khoản test)
/                                                -> Điều hướng theo role (HomeRedirect)

/admin                                           -> AdminLayout
├── /admin (index)                               -> Tổng quan quản trị W0-W8 (DashboardPage)
├── /admin/users                                 -> Quản lý tài khoản người dùng (UsersPage)
├── /admin/categories                            -> Quản lý danh mục sản phẩm (CategoriesPage)
├── /admin/products                              -> Quản lý sản phẩm (ProductsPage)
├── /admin/suppliers                             -> Quản lý nhà cung cấp (SuppliersPage)
├── /admin/materials                             -> Quản lý nguyên vật liệu (MaterialsPage)
├── /admin/staff                                 -> Danh sách nhân viên giao việc & Tìm kiếm (StaffPage)
├── /admin/reports                               -> Báo cáo xuất nhập tồn toàn hệ thống (StockReportsPage)
└── /admin/profile                               -> Hồ sơ cá nhân (ProfilePage)

/warehouse                                       -> WarehouseLayout
├── /warehouse (index)                           -> Tổng quan kho (WarehouseDashboard)
├── /warehouse/inventory                         -> Tồn kho NVL & Thành phẩm + Lọc danh mục + Kiểm kê (InventoryPage)
├── /warehouse/movements                         -> Sổ kho biến động + Lọc chọn NVL/SP trực quan (MovementsPage)
├── /warehouse/receipts                          -> Phiếu nhập kho NVL + Tính tạm tính live (PurchaseReceiptsPage)
├── /warehouse/boms                              -> Công thức sản phẩm BOM (BomsPage)
├── /warehouse/planning                          -> Kế hoạch MRP & Dự báo thiếu hụt (MrpPlanningPage)
├── /warehouse/production-requests               -> Yêu cầu sản xuất & Tiến độ (ProductionRequestsPage)
├── /warehouse/material-issues                   -> Phê duyệt phiếu xuất NVL + Lọc theo PR/Nhân viên (MaterialIssuesPage)
├── /warehouse/fg-issues                         -> Phê duyệt phiếu xuất thành phẩm + Lọc theo SP/Nhân viên (FgIssuesPage)
├── /warehouse/reports                           -> Báo cáo xuất nhập tồn theo kỳ + Xuất CSV (StockReportsPage)
└── /warehouse/profile                           -> Hồ sơ cá nhân (ProfilePage)

/staff                                           -> StaffLayout
├── /staff (index)                               -> Bàn làm việc nhân viên (StaffDashboard)
├── /staff/requests                              -> Yêu cầu sản xuất được giao (StaffProductionRequestsPage)
├── /staff/material-requests                     -> Phiếu đề nghị xuất NVL (StaffMaterialRequestsPage)
├── /staff/productions                           -> Thực hiện các đợt sản xuất (StaffProductionsPage)
├── /staff/fg-requests                           -> Đề nghị xuất bán thành phẩm (StaffFgRequestsPage)
└── /staff/profile                               -> Hồ sơ cá nhân (ProfilePage)
```

---

## 3. Các nâng cấp chi tiết ở từng trang

1. **Đăng nhập (`LoginPage.jsx`):**
   - Bổ sung cụm phím **Điền nhanh tài khoản kiểm thử**: 1-click tự động điền thông tin đăng nhập cho `Admin`, `Quản lý kho (wm)` hoặc `Nhân viên (staff)`.

2. **Khu vực Quản trị viên (ADMIN):**
   - **`DashboardPage.jsx`:** Nâng cấp tổng thể với 6 thẻ thống kê chỉ số thời gian thực, widget "Hoạt động thời gian thực của hệ thống" hiển thị 2 tab dữ liệu động: **Yêu cầu sản xuất mới nhất** (tiến độ, trạng thái, người phụ trách) và **Biến động kho gần đây** (loại biến động, vật tư/sản phẩm, số lượng, người ghi, thời gian), cùng bảng phím tắt quản trị và trạng thái hệ thống.
   - **`ProfilePage.jsx`:** Thiết kế dạng User Card với Avatar, Tag vai trò, hiển thị chi tiết tên đăng nhập, họ tên, email, SĐT, địa chỉ và mã hồ sơ.
   - **`UsersPage.jsx`:** Quản lý tài khoản (tạo mới, đổi vai trò, khóa/mở khóa, xóa) bảo vệ tài khoản đang đăng nhập không bị tự khóa/xóa.
   - **`CatalogPage.jsx`:** Quản lý sản phẩm, danh mục, nhà cung cấp, nguyên vật liệu với tìm kiếm nâng cao, lọc trạng thái, sắp xếp whitelist backend.
   - **`StaffPage.jsx`:** Bổ sung ô tìm kiếm nhân viên, nút làm mới, hướng dẫn về cơ chế hồ sơ `staff.id` và nút chuyển nhanh đến tạo tài khoản nhân viên.
   - **`StockReportsPage.jsx`:** Admin có thể tra cứu toàn diện báo cáo xuất nhập tồn kho.

3. **Khu vực Quản lý kho (WAREHOUSE_MANAGER):**
   - **`WarehouseDashboard.jsx`:** 8 thẻ thống kê nghiệp vụ kho, thẻ cảnh báo phiếu xuất chờ duyệt nổi bật, quy trình 4 bước và phím tắt điều hướng nhanh.
   - **`InventoryPage.jsx`:** Xem tồn kho thời gian thực; tab Thành phẩm bổ sung **bộ lọc theo Danh mục**; modal Kiểm kê điều chỉnh tồn hiển thị sẵn **Tồn kho hiện tại** của vật tư được chọn.
   - **`MovementsPage.jsx`:** Nâng cấp bộ lọc ID nguyên vật liệu và ID sản phẩm thành **Select tìm kiếm theo tên/mã**, lọc loại biến động và khoảng ngày.
   - **`PurchaseReceiptsPage.jsx`:** Quản lý phiếu nhập nhiều dòng NVL, hiển thị **tạm tính thành tiền trực tiếp (Live Calculation)** khi nhập số lượng & đơn giá, sửa dòng khi PENDING, bấm **Hoàn tất** (tăng tồn) và **Hủy phiếu**.
   - **`BomsPage.jsx`:** Quản lý phiên bản công thức BOM sản phẩm, kích hoạt/vô hiệu hóa, xem chi tiết định mức; chuẩn hóa cột sắp xếp.
   - **`MrpPlanningPage.jsx`:** Thuật toán MRP tính toán tồn khả dụng thành phẩm, nhu cầu thô và dự báo thiếu hụt NVL; cung cấp nút tắt tạo nhanh Yêu cầu SX và Phiếu nhập kho.
   - **`ProductionRequestsPage.jsx`:** Tạo lệnh SX (tự gắn BOM active), thanh tiến độ hoàn thành trực quan, modal kiểm tra định mức NVL (`/requirements`); chuẩn hóa sắp xếp.
   - **`MaterialIssuesPage.jsx`:** Bổ sung **bộ lọc theo Mã PR và Nhân viên yêu cầu**; cảnh báo thiếu tồn, duyệt trừ kho hoặc từ chối bắt buộc lý do.
   - **`FgIssuesPage.jsx`:** Bổ sung **bộ lọc theo Nhân viên yêu cầu và Sản phẩm**; duyệt trừ kho thành phẩm hoặc từ chối.
   - **`StockReportsPage.jsx`:** Thống kê theo Tháng/Quý/Năm, biểu đồ cột so sánh Tồn đầu - Nhập - Xuất - Tồn cuối, tích hợp tính năng **Xuất file báo cáo CSV** (hỗ trợ hiển thị tiếng Việt có dấu trong Excel).

4. **Khu vực Nhân viên (STAFF):**
   - **`StaffDashboard.jsx`:** 5 thẻ thống kê công việc được giao, hướng dẫn 5 bước chuẩn và phím tắt thao tác nhanh.
   - **`StaffProductionRequestsPage.jsx`:** Theo dõi tiến độ hoàn thành lệnh SX, kiểm tra định mức NVL, phím tắt nhanh sang tạo phiếu xuất NVL và bắt đầu SX.
   - **`StaffMaterialRequestsPage.jsx`:** Lập phiếu xin xuất kho NVL theo hạn mức BOM còn được phép (`remainingAllowed`), cảnh báo khi kho thiếu tồn, hủy phiếu khi PENDING.
   - **`StaffProductionsPage.jsx`:** Bắt đầu đợt sản xuất, bấm **Hoàn tất** (kiểm tra NVL xuất đủ theo BOM rồi tự động nhập kho thành phẩm), hủy đợt SX.
   - **`StaffFgRequestsPage.jsx`:** Lập phiếu đề xuất xuất bán thành phẩm cho khách hàng kèm thông tin người mua và đơn hàng.

---

## 4. Hướng dẫn Test tay (Manual Testing Flow)

Người dùng có thể tự trải nghiệm luồng nghiệp vụ xuyên suốt 10 bước qua giao diện:

1. **Đăng nhập Admin (`admin` / `change_me_please`):**
   - Vào `/admin/users`: Tạo 1 tài khoản WM (vd: `wm` / `Admin@123`) và 1 tài khoản STAFF (vd: `staff` / `Admin@123`).
   - Vào `/admin/categories`, `/admin/products`, `/admin/materials`, `/admin/suppliers`: Kiểm tra dữ liệu danh mục hoặc tạo mới nếu cần.
2. **Đăng nhập Quản lý kho (`wm` / `Admin@123`):**
   - Vào `/warehouse/receipts`: Bấm "Tạo phiếu nhập mới", chọn NCC, thêm NVL kèm số lượng và đơn giá -> Bấm "Hoàn tất" để nhập kho.
   - Vào `/warehouse/inventory`: Kiểm tra tồn kho NVL vừa tăng. Thử tính năng "Điều chỉnh tồn kho" kiểm kê.
   - Vào `/warehouse/movements`: Kiểm tra biến động `PURCHASE_IN` trong sổ kho.
   - Vào `/warehouse/boms`: Tạo công thức BOM cho sản phẩm (vd: 1 Bàn cần 4 Chân gỗ, 1 Mặt bàn) -> Kích hoạt BOM.
   - Vào `/warehouse/planning`: Nhập sản phẩm và số lượng muốn có -> Bấm "Tính toán MRP" xem phân tích tồn khả dụng và dự báo thiếu hụt.
   - Vào `/warehouse/production-requests`: Bấm "Tạo yêu cầu sản xuất", chọn sản phẩm, số lượng, chọn nhân viên `staff` thực hiện.
3. **Đăng nhập Nhân viên (`staff` / `Admin@123`):**
   - Vào `/staff/requests`: Thấy ngay lệnh SX vừa được giao, bấm "Kiểm tra NVL" để xem hạn mức.
   - Vào `/staff/material-requests`: Bấm "Lập đề nghị xuất NVL mới" cho PR trên -> Phiếu chuyển sang trạng thái `PENDING`.
4. **Quay lại Quản lý kho (`wm`):**
   - Vào `/warehouse/material-issues`: Thấy phiếu xuất NVL đang chờ duyệt -> Bấm "Duyệt" (hệ thống ghi sổ kho `PRODUCTION_OUT` và trừ tồn NVL).
5. **Quay lại Nhân viên (`staff`):**
   - Vào `/staff/productions`: Bấm "Bắt đầu đợt sản xuất" -> Đợt SX ở trạng thái `IN_PROGRESS`.
   - Bấm "Hoàn tất": Hệ thống xác thực NVL đã xuất đủ và tự động nhập kho thành phẩm (`PRODUCTION_IN`), cập nhật tiến độ PR thành 100%.
   - Vào `/staff/fg-requests`: Lập đề nghị xuất bán thành phẩm cho khách hàng.
6. **Quay lại Quản lý kho (`wm`):**
   - Vào `/warehouse/fg-issues`: Duyệt phiếu xuất bán TP (ghi sổ kho `SALES_OUT` và trừ tồn TP).
   - Vào `/warehouse/reports`: Xem biểu đồ báo cáo xuất nhập tồn và bấm "Xuất CSV".

---

## 5. Lệnh khởi chạy

```bash
# 1. Cơ sở dữ liệu Postgres (Docker)
docker start mrp-postgres

# 2. Khởi động Backend (Spring Boot trên cổng 8080)
cd backend
./mvnw.cmd spring-boot:run

# 3. Khởi động Frontend (Vite trên cổng 5173)
cd frontend
npm.cmd run dev
```

Truy cập: `http://localhost:5173`


# MRP System – Frontend (React + Vite)

Giao diện web quản trị cho hệ thống Quản lý Kế hoạch Yêu cầu Nguyên vật liệu (MRP): React 19 + Vite + React Router v7 + TanStack Query v5 + Ant Design v6 + Recharts + Dayjs.
Toàn bộ nghiệp vụ (tồn kho, quyền, trạng thái, MRP) do backend Spring Boot quyết định; frontend chỉ hiển thị và gửi lệnh.

## 1. Hướng dẫn chạy thử

```bash
# 1) Khởi động PostgreSQL qua Docker (từ thư mục gốc repo)
docker compose up -d

# 2) Khởi động Backend (Spring Boot tại http://localhost:8080)
cd backend
./mvnw spring-boot:run          # hoặc .\mvnw.cmd spring-boot:run trên Windows

# 3) Khởi động Frontend (Vite tại http://localhost:5173)
cd frontend
npm install
npm run dev
```

- `.env` bắt buộc có `VITE_API_BASE_URL` (mặc định `http://localhost:8080`); không hardcode URL trong code.
- Backend đã cấu hình CORS cho phép nguồn `http://localhost:5173`.
- Kiểm tra mã nguồn:
  - `npm run lint`: Chạy `oxlint` kiểm tra lỗi cú pháp và linter.
  - `npm run build`: Đóng gói ứng dụng cho production vào thư mục `dist/`.

### Tài khoản đăng nhập

| Vai trò | Tên đăng nhập | Mật khẩu mặc định | Khu vực giao diện |
|---|---|---|---|
| **ADMIN** | `admin` | `change_me_please` | `/admin/*` |
| **WAREHOUSE_MANAGER** | `wm01` | `Password@123` | `/warehouse/*` |
| **STAFF** | `haha123` | `haha123@` | `/staff/*` |

- Admin ban đầu được tạo tự động bởi `BootstrapAdminRunner` từ cấu hình `.env` khi bảng `users` rỗng.
- Tài khoản `WAREHOUSE_MANAGER` và `STAFF` được Admin tạo trực tiếp tại giao diện `/admin/users`.
- Phân quyền theo vai trò: ADMIN → `/admin`, WAREHOUSE_MANAGER → `/warehouse`, STAFF → `/staff`. Khi truy cập sai khu vực, hệ thống tự động chặn và điều hướng về trang của vai trò đó.

---

## 2. Cấu trúc thư mục (`frontend/src/`)

```
src/
├── api/             client.js (fetch + JWT + ProblemDetail), hooks.js (usePagedQuery, useResourceActions)
├── auth/            Auth.jsx (AuthProvider), context.js (useAuth), RequireRole.jsx (guard theo vai trò)
├── layouts/         AuthLayout (đăng nhập), AdminLayout, WarehouseLayout, StaffLayout
├── components/      PageHeader, PagedTable, ConfirmButton, RejectModal, StatusTag, FormModal, FilterControl
├── pages/
│   ├── LoginPage.jsx
│   ├── NotFoundPage.jsx
│   ├── admin/       Dashboard, Users, Profile, Categories, Products, Suppliers, Materials, Staff
│   ├── warehouse/   WarehouseDashboard, Inventory, Movements, PurchaseReceipts, Boms, MrpPlanning,
│   │                ProductionRequests, MaterialIssues, FgIssues, StockReports
│   └── staff/       StaffDashboard, StaffProductionRequests, StaffMaterialRequests,
│                    StaffProductions, StaffFgRequests
├── utils/           format.js (tiền tệ, ngày giờ, số lượng), statusMap.js (nhãn, màu sắc trạng thái)
├── routes.jsx       Khai báo các route độc lập theo 3 khu vực vai trò
├── index.css        Giao diện và kiểu dáng toàn hệ thống
└── main.jsx         Điểm khởi chạy ứng dụng (React Query + Ant Design App provider)
```

---

## 3. Bản đồ Màn hình & Tính năng hoàn chỉnh (W0 – W8)

### 3.1 Khu vực Quản trị viên (ADMIN – `/admin`)
- **Tổng quan (`/admin`)**: Thẻ thống kê số lượng người dùng, danh mục, sản phẩm, vật tư, NCC, yêu cầu sản xuất và các phím tắt nhanh.
- **Tài khoản (`/admin/users`)**: Danh sách tìm kiếm, lọc theo vai trò, trạng thái, tạo tài khoản mới, đổi vai trò, khóa/mở khóa và xóa tài khoản.
- **Danh mục (`/admin/categories`)**: Quản lý nhóm phân loại sản phẩm.
- **Sản phẩm (`/admin/products`)**: Quản lý sản phẩm, giá bán, đơn vị tính, kích hoạt/vô hiệu hóa.
- **Nhà cung cấp (`/admin/suppliers`)**: Quản lý nhà cung cấp, thông tin liên hệ, địa chỉ.
- **Nguyên vật liệu (`/admin/materials`)**: Quản lý danh mục NVL, mã NVL, giá chuẩn, kích hoạt/vô hiệu hóa.
- **Nhân viên (`/admin/staff`)**: Tra cứu danh sách nhân viên phục vụ giao việc.
- **Báo cáo kho (`/admin/reports`)**: Xem thống kê xuất nhập tồn toàn hệ thống theo kỳ.
- **Hồ sơ cá nhân (`/admin/profile`)**: Xem thông tin tài khoản đang đăng nhập.

### 3.2 Khu vực Quản lý kho (WAREHOUSE_MANAGER – `/warehouse`)
- **Tổng quan kho (`/warehouse`)**: Thống kê số lượng tồn, phiếu nhập, yêu cầu SX, phiếu xuất chờ duyệt.
- **Tồn kho & Kiểm kê (`/warehouse/inventory`)**: Xem tồn kho thời gian thực của NVL và Thành phẩm, tích hợp modal **Điều chỉnh tồn kho** (`ADJUST_IN` / `ADJUST_OUT` kèm lý do bắt buộc).
- **Sổ kho biến động (`/warehouse/movements`)**: Tra cứu lịch sử nhập/xuất/điều chỉnh append-only với bộ lọc linh hoạt (loại movement, vật tư, sản phẩm, ngày tháng).
- **Phiếu nhập kho NVL (`/warehouse/receipts`)**: Lập phiếu nhập nhiều dòng từ nhà cung cấp, tính tạm tính thành tiền trực tiếp, sửa dòng khi PENDING, bấm **Hoàn tất** (tăng tồn kho) và **Hủy phiếu**.
- **Công thức sản phẩm BOM (`/warehouse/boms`)**: Thiết lập định mức NVL cho 1 đơn vị sản phẩm hoàn thiện, tự động tăng phiên bản, thao tác **Kích hoạt** và **Vô hiệu hóa**.
- **Kế hoạch MRP (`/warehouse/planning`)**: Nhập sản phẩm và sản lượng mong muốn → Thuật toán MRP tính toán tồn khả dụng thành phẩm, nhu cầu thô và **dự báo thiếu hụt NVL (`shortage`)**, hỗ trợ nút tạo nhanh Yêu cầu SX và Phiếu nhập kho.
- **Yêu cầu sản xuất (`/warehouse/production-requests`)**: Tạo lệnh sản xuất giao cho nhân viên (tự gắn BOM active), thanh tiến độ hoàn thành trực quan, xem chi tiết tiến độ và modal **Kiểm tra định mức NVL** (`/requirements`).
- **Duyệt phiếu xuất NVL (`/warehouse/material-issues`)**: Đối chiếu số lượng xin xuất với tồn kho thực tế, cảnh báo thiếu tồn, thao tác **Duyệt** (trừ kho) hoặc **Từ chối** (bắt buộc nhập lý do).
- **Duyệt phiếu xuất thành phẩm (`/warehouse/fg-issues`)**: Duyệt xuất bán thành phẩm cho các đơn hàng do nhân viên gửi lên.
- **Báo cáo xuất nhập tồn (`/warehouse/reports`)**: Thống kê theo Tháng, Quý, Năm cho cả NVL và Thành phẩm, biểu đồ cột trực quan (Recharts) và nút **Xuất file CSV**.
- **Hồ sơ cá nhân (`/warehouse/profile`)**: Xem thông tin tài khoản và mã hồ sơ kho.

### 3.3 Khu vực Nhân viên (STAFF – `/staff`)
- **Bàn làm việc (`/staff`)**: Thống kê công việc được giao, đợt sản xuất đang chạy, phiếu xuất chờ duyệt và hướng dẫn quy trình 5 bước.
- **Yêu cầu sản xuất của tôi (`/staff/requests`)**: Danh sách các lệnh sản xuất được giao cho nhân viên hiện tại, theo dõi tiến độ hoàn thành, tra cứu định mức NVL.
- **Đề nghị xuất NVL (`/staff/material-requests`)**: Lập phiếu xin xuất kho NVL trong hạn mức BOM còn được phép (`remainingAllowed`), hủy phiếu khi PENDING.
- **Thực hiện sản xuất (`/staff/productions`)**: Khởi tạo đợt sản xuất (`IN_PROGRESS`), bấm **Hoàn tất** (máy chủ kiểm tra NVL xuất đủ theo BOM rồi tự động nhập kho thành phẩm), hủy đợt sản xuất.
- **Đề nghị xuất bán thành phẩm (`/staff/fg-requests`)**: Lập phiếu xuất bán sản phẩm cho khách hàng kèm thông tin người mua và đơn hàng.
- **Hồ sơ cá nhân (`/staff/profile`)**: Xem thông tin cá nhân của nhân viên.

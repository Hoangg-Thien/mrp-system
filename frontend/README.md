# MRP System – Frontend (React + Vite)

Giao diện web quản trị cho hệ thống MRP: React 19 + Vite + React Router + TanStack Query + Ant Design.
Toàn bộ nghiệp vụ (tồn kho, quyền, trạng thái, MRP) do backend quyết định; frontend chỉ hiển thị và gửi lệnh.

## 1. Chạy thử

```bash
# 1) Backend + PostgreSQL (từ thư mục gốc repo)
docker compose up -d
cd backend && ./mvnw spring-boot:run     # http://localhost:8080

# 2) Frontend
cd frontend
npm install
copy .env.example .env                   # hoặc: cp .env.example .env
npm run dev                              # http://localhost:5173
```

- `.env` bắt buộc có `VITE_API_BASE_URL` (mặc định `http://localhost:8080`); không hardcode URL trong code.
- Backend đã cho phép CORS từ `http://localhost:5173`.
- Kiểm tra trước khi commit: `npm run lint`, `npm run build`, và `npx tsc --noEmit -p jsconfig.json` (type-check file có `// @ts-check`).
- `jsconfig.json` cung cấp kiểu cho VS Code (`vite/client`, JSX, module resolution) để `import.meta.env` và các file `.jsx` không bị báo lỗi.
- `npm run gen:api` sinh `src/api/schema.d.ts` từ OpenAPI của backend (cần backend chạy ở `http://localhost:8080`) — dùng cho JSDoc ở lớp `api/`.

Tài khoản đăng nhập (backend **không** seed user demo):

- Admin đầu tiên do `BootstrapAdminRunner` tạo khi bảng `users` rỗng, đọc từ biến môi trường phía backend:
  `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_PASSWORD` (tối thiểu 8 ký tự), `BOOTSTRAP_ADMIN_FULL_NAME`.
- Tài khoản `WAREHOUSE_MANAGER` / `STAFF` do ADMIN tạo trong `/admin/users` (hệ thống không có public registration).
- Khu vực theo role: ADMIN → `/admin`, WAREHOUSE_MANAGER → `/warehouse`, STAFF → `/staff`.

## 2. Cấu trúc `src/`

```
api/        client.js (fetch + JWT + xử lý ProblemDetail), hooks.js (query/mutation dùng chung)
auth/       Auth.jsx (AuthProvider), context.js (useAuth), RequireRole.jsx (guard theo role)
layouts/    AuthLayout, AdminLayout, AreaLayout (WM/STAFF)
components/ PageHeader, PagedTable, FilterControl, FormModal, StatusTag/RoleTag
pages/      LoginPage, PendingPage, NotFoundPage
pages/admin Dashboard, Users, Profile, Categories, Products, Suppliers, Materials, Staff (+ resources.js, userFields.js)
utils/      format.js (tiền, ngày giờ), statusMap.js (nhãn role/trạng thái, home theo role)
routes.jsx  main.jsx  index.css
```

## 3. Phạm vi đã triển khai

| Phase | Nội dung | Trạng thái |
|---|---|---|
| W0 | Đăng nhập JWT, `GET /api/auth/me`, guard 3 khu vực, layout + menu, API client, TanStack Query | Xong |
| W1 | Quản lý tài khoản: danh sách (tìm/lọc role/trạng thái/sắp xếp), tạo, khóa/mở khóa, đổi vai trò, xóa; hồ sơ cá nhân | Xong |
| W2 | Danh mục: categories, products, suppliers, materials – tìm/lọc/sắp xếp/phân trang/CRUD, activate–deactivate; danh sách nhân viên (`GET /api/staff`) | Xong |
| W3–W8 | Kho, phiếu nhập, BOM, MRP, sản xuất, xuất thành phẩm, báo cáo | Chờ backend P3–P9 (màn hình chờ đã có ở `/warehouse`, `/staff`) |

Quy ước quan trọng:

- Phân trang/sắp xếp luôn gửi lên backend (`page`, `size`, `sort`, `direction`). Trường sắp xếp phải nằm trong whitelist backend:
  users `username|createdAt|role|active`; categories `id|name`; products `id|name|sellingPrice|createdAt`;
  suppliers `id|name|phone|email`; materials `id|code|name|standardCost|createdAt`.
- Lỗi hiển thị đúng `detail` + `errors` từ ProblemDetail; không tự suy luận nghiệp vụ, không mock số liệu.
- 401 → xóa token và quay lại `/login`; role sai khu vực → điều hướng về trang chủ của role đó.

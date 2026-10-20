# Plan frontend – mrp-system (bản chung, chốt chi tiết khi backend xong)

Cập nhật: 2026-10-07. Một web admin bằng **React + Vite** (`frontend/`, không dùng React Native) cho 3 role: **ADMIN**, **WAREHOUSE_MANAGER (WM)**, **STAFF**.
Backend (`docs/BACKEND_E2E_PLAN.md`) chưa xong, nên plan này **chỉ chốt khung, màn hình và quy ước**. Endpoint chính xác lấy từ Swagger khi backend xong (mục 6).

## 0. Nguyên tắc

- Frontend **không tự định nghĩa nghiệp vụ**: tồn kho, thiếu hụt MRP, trạng thái phiếu, quyền đều do backend quyết định; frontend chỉ hiển thị và gửi lệnh.
- **Contract-first:** kiểu dữ liệu API sinh từ OpenAPI của backend, không viết tay.
- Đề yêu cầu **giao diện Admin tách biệt** với giao diện quản lý kho / nhân viên: ba khu vực riêng, không dùng chung màn hình (mục 3).
- Mỗi phase xong khi `lint` và `build` đều qua và chạy tay đúng kịch bản. Giao diện tiếng Việt.
- Giao diện cần đủ đẹp và có đủ màn hình để **chụp ảnh đưa vào báo cáo** (mục thiết kế giao diện, 5đ) và demo.

## 1. Quyết định công nghệ (mặc định, đổi được)

| # | Vấn đề | Mặc định |
|---|---|---|
| F1 | Ngôn ngữ | **JavaScript** (giữ template Vite hiện tại, không cần TypeScript). Thêm `// @ts-check` và JSDoc ở lớp `api/` để VS Code gạch chỗ sai tên field. |
| F2 | Thư viện | Vite + React 19 (đã có; đây là web nên không cần React Native / Expo) + **React Router** + **TanStack Query** + **Ant Design** (Table, Form, Modal, Steps, DatePicker) + một thư viện biểu đồ nhẹ cho báo cáo (vd `recharts`). |
| F3 | Gọi API | `openapi-typescript` sinh `schema.d.ts` (chỉ để JSDoc / VS Code gợi ý kiểu) + `openapi-fetch` hoặc `fetch` thuần. Một thư mục `api/` duy nhất. |
| F4 | Trạng thái | Dữ liệu server: TanStack Query. Chỉ một store nhỏ cho đăng nhập (token + `me`). |
| F5 | Token | JWT trong `Authorization: Bearer`, lưu `localStorage`. Không refresh token: gặp 401 thì xóa token, về đăng nhập. |
| F6 | Cấu hình | `VITE_API_BASE_URL` trong `.env`; không hardcode URL. |

## 2. Cấu trúc thư mục (`frontend/src`)

```
api/            client.js, schema.d.ts (sinh tự động), error.js
auth/           AuthProvider, useAuth, RequireRole
layouts/        AuthLayout (đăng nhập), AdminLayout, WarehouseLayout, StaffLayout
pages/
  admin/        users, products, categories, suppliers, materials
  warehouse/    receipts, boms, planning, production-requests, approvals, finished-goods, inventory, reports
  staff/        my-requests, requirements, material-requests, productions, fg-requests
components/     StatusTag, QuantityText, PageHeader, ConfirmButton, DataTable (lọc / sắp xếp / phân trang)
utils/          format.js, statusMap.js
routes.jsx  main.jsx
```

## 3. Màn hình theo khu vực

Đăng nhập xong, chuyển thẳng vào khu vực của role: `/admin/*`, `/warehouse/*`, `/staff/*`. Role khác vào sai khu vực bị chặn.

### 3.1 ADMIN (`/admin`) — tách biệt hoàn toàn
| Màn hình | Nội dung |
|---|---|
| Tài khoản | danh sách (tìm / lọc role / trạng thái / sắp xếp), tạo STAFF / WM / ADMIN, khóa / mở khóa, đổi role, xóa |
| Sản phẩm | tìm, tìm kiếm nâng cao (danh mục, khoảng giá, trạng thái), sắp xếp, lọc; thêm / sửa / xóa / vô hiệu hóa |
| Nhà cung cấp | tìm theo tên / SĐT / email, sắp xếp; CRUD |
| Nguyên vật liệu, danh mục | CRUD, vô hiệu hóa NVL |
| Hồ sơ cá nhân | sửa thông tin, đổi mật khẩu |

### 3.2 Quản lý kho – WM (`/warehouse`)
| Màn hình | Nội dung |
|---|---|
| Phiếu nhập kho | tạo (chọn NCC, nhiều dòng NVL), sửa khi chờ, Hoàn tất, Hủy |
| Công thức sản phẩm (BOM) | xem theo sản phẩm, tạo phiên bản mới (1 bàn = 1 mặt + 4 chân), kích hoạt / vô hiệu |
| **Kế hoạch MRP** | chọn sản phẩm + số lượng muốn có sẵn → bảng kết quả: Thành phẩm (tồn, đã giữ, đang sản xuất, cần sản xuất) và từng NVL (cần, tồn, đã cam kết, dùng được, **thiếu**, hành động đề nghị nhập thêm) |
| Yêu cầu sản xuất | tạo (chọn sản phẩm, số lượng, nhân viên được giao), danh sách, tiến độ, hủy |
| Duyệt phiếu xuất NVL | danh sách chờ duyệt, xem dòng + tồn hiện tại, Duyệt / Từ chối (nhập lý do); lỗi thiếu tồn hiển thị rõ NVL nào |
| Duyệt phiếu xuất thành phẩm | tương tự |
| Tồn kho và sổ kho | tồn NVL / thành phẩm; lịch sử biến động có lọc; điều chỉnh tồn (kèm ghi chú bắt buộc) |
| Báo cáo | tồn / nhập / xuất theo **tháng, quý, năm**, cho NVL và thành phẩm; bảng + biểu đồ |

### 3.3 Nhân viên – STAFF (`/staff`)
| Màn hình | Nội dung |
|---|---|
| Yêu cầu sản xuất của tôi | danh sách được giao, tiến độ |
| Kiểm tra NVL | từng NVL: cần, đã xuất, đang chờ duyệt, còn được yêu cầu, tồn, thiếu bao nhiêu |
| Lập phiếu đề nghị xuất NVL | chọn NVL trong hạn mức BOM, cảnh báo thiếu tồn; theo dõi trạng thái chờ / duyệt / từ chối; hủy khi còn chờ |
| Sản xuất | bắt đầu, hoàn tất từng đợt; hiển thị lỗi nếu NVL đã xuất chưa đủ |
| Lập phiếu đề nghị xuất thành phẩm để bán | chọn sản phẩm + số lượng, ghi chú người mua; cảnh báo khi vượt tồn; theo dõi trạng thái |

## 4. Quy ước dùng chung

- **Lỗi:** backend trả `ProblemDetail` (400 đầu vào sai, 401 chưa đăng nhập, 403 sai quyền, 404, 409 sai trạng thái / thiếu tồn / vi phạm quy tắc, 500). `api/error.js` chuẩn hóa thành `{status, code, message, fieldErrors}`. 400 → lỗi từng ô form; 409 → hiển thị thông báo backend; 401 → về đăng nhập.
- **Số lượng:** NVL có thể lẻ 3 chữ số thập phân, thành phẩm là số nguyên; định dạng ở `utils/format.js`. Frontend **không tự tính** thiếu hụt / tồn; lấy từ backend (kế hoạch MRP, kiểm tra NVL).
- **Không gửi** trường server quyết định: `status`, người duyệt, `created_by`, số tiền / tổng tiền.
- **Trạng thái:** mọi enum (phiếu, yêu cầu sản xuất, sản xuất) map sang nhãn tiếng Việt + màu ở `statusMap`. Nút Duyệt / Từ chối / Hoàn tất chỉ hiện đúng role và đúng trạng thái; backend vẫn là chốt chặn cuối.
- **Danh sách:** tìm / lọc / sắp xếp / phân trang gọi API; ô tìm debounce; chỉ dùng trường sắp xếp backend cho phép.
- **Mỗi màn hình có đủ 3 trạng thái:** đang tải, rỗng, lỗi (nút thử lại). Nút gửi bị vô hiệu khi đang gọi (chống bấm đúp); duyệt / từ chối / hủy / xóa có hộp thoại xác nhận.
- **Từ chối** luôn bắt nhập lý do.

## 5. Các phase

**Làm ngay được, không cần backend:** W0 (khung dự án, 3 layout, route guard theo role, component dùng chung, đăng nhập với dữ liệu giả).

| Phase | Nội dung | Cần backend phase |
|---|---|---|
| W0 | Router, Query, Ant Design, `.env`, 3 layout + menu theo role, guard, `api/` client, đăng nhập | P0 |
| W1 | Admin: tài khoản, hồ sơ | P1 |
| W2 | Admin: sản phẩm, danh mục, NCC, NVL (tìm / lọc / sắp xếp); danh sách nhân viên cho WM chọn | P2 |
| W3 | WM: tồn kho, sổ kho, điều chỉnh | P3 |
| W4 | WM: phiếu nhập kho; BOM | P4, P5 |
| W5 | Kế hoạch MRP, yêu cầu sản xuất, kiểm tra NVL, phiếu xuất NVL, sản xuất (WM + STAFF) | P6 |
| W6 | Phiếu xuất thành phẩm (STAFF lập, WM duyệt) | P7 |
| W7 | Báo cáo tháng / quý / năm + biểu đồ | P8 |
| W8 | Hoàn thiện, chụp ảnh màn hình cho báo cáo, README | P9 |

Thứ tự khi thiếu thời gian: W0–W2 (Admin, 20đ) và W4–W6 (luồng MRP, 30đ) trước; W7 báo cáo là phần bắt buộc nhưng làm sau cùng.

## 6. Ghép với backend khi backend xong

1. Chạy backend (docker-compose), mở Swagger, lưu `openapi.json` vào repo (`frontend/openapi.json`) để làm việc khi backend tắt.
2. Thêm script `gen:api` (sinh `src/api/schema.d.ts` từ `openapi.json`). Khi mở file API trong VS Code, chỗ nào dùng sai tên field sẽ bị gạch đỏ (nhờ `// @ts-check` + JSDoc); thấy lệch thì sửa theo backend.
3. Điền bảng ánh xạ bên dưới bằng endpoint thật (đối chiếu Swagger); phần "dự kiến" lấy từ plan backend.
4. Dùng tài khoản demo của backend (P9) chạy tay từng kịch bản.
5. Mỗi lần backend đổi API: lưu lại `openapi.json`, chạy `gen:api`, sửa lỗi kiểu.

| Màn hình | Endpoint dự kiến | Đã đối chiếu Swagger |
|---|---|---|
| Đăng nhập, hồ sơ | `POST /api/auth/login`, `GET /api/auth/me` | [ ] |
| Tài khoản (Admin) | `/api/admin/users/**`, `GET /api/staff` | [ ] |
| Sản phẩm, danh mục, NCC, NVL | `/api/products`, `/api/categories`, `/api/suppliers`, `/api/materials` | [ ] |
| Tồn kho, sổ kho, điều chỉnh | `/api/inventories/**`, `/api/stock-movements`, `/api/stock-adjustments` | [ ] |
| Phiếu nhập kho | `/api/purchase-receipts` + `complete`, `cancel` | [ ] |
| BOM | `/api/boms` + `activate`, `deactivate` | [ ] |
| Kế hoạch MRP | `GET /api/mrp/plan` | [ ] |
| Yêu cầu sản xuất, kiểm tra NVL | `/api/production-requests`, `/{id}/requirements` | [ ] |
| Phiếu xuất NVL | `/api/material-issue-requests` + `approve`, `reject`, `cancel` | [ ] |
| Sản xuất | `/api/productions` + `complete`, `cancel` | [ ] |
| Phiếu xuất thành phẩm | `/api/fg-issue-requests` + `approve`, `reject`, `cancel` | [ ] |
| Báo cáo | `GET /api/reports/stock` | [ ] |

## 7. Định nghĩa hoàn thành

- Chạy được toàn bộ luồng demo: nhập kho → BOM → kế hoạch MRP → yêu cầu sản xuất → phiếu xuất NVL → duyệt → sản xuất → phiếu xuất thành phẩm → duyệt → báo cáo, bằng đúng 3 tài khoản của 3 role.
- Khu vực Admin tách biệt; role sai vào khu vực khác bị chặn.
- Không còn lỗi `lint`; `build` thành công; không viết tay kiểu API (dùng `schema.d.ts` sinh tự động).
- Mỗi màn hình có trạng thái tải / rỗng / lỗi; nút gửi chống bấm đúp.
- Có ảnh chụp từng màn hình chính và README hướng dẫn chạy (cài, `.env`, trỏ tới backend).

## 8. Cách giao cho Antigravity (khi backend xong)

Mỗi lần một phase, prompt mẫu:
> Đọc `frontend/GEMINI.md` (nếu có), `docs/FRONTEND_PLAN.md` và `frontend/openapi.json`. Task: làm phase <W2 / W5 ...>. Chỉ dùng kiểu sinh từ `openapi.json` (qua JSDoc), không viết tay kiểu API. Làm đủ trạng thái tải / rỗng / lỗi, chống bấm đúp. Chạy `npm run lint`, `npm run build` và báo kết quả thật. Không sửa backend, không thêm thư viện ngoài mục 1.

Quy tắc ngắn nên đặt trong `frontend/GEMINI.md` (hoặc `.gemini/GEMINI.md`): JavaScript (React + Vite), file API có `// @ts-check` và JSDoc lấy kiểu từ `schema.d.ts`; API chỉ qua `src/api`; không hardcode URL / token; số lượng và ngày chỉ format ở `utils/format`; không tự tính tồn / thiếu hụt; không gửi trường server quyết định; thêm thư viện phải hỏi.

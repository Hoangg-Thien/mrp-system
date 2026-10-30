# Kiểm thử chức năng Admin — 29/10/2026

## Cập nhật sau khi bổ sung hồ sơ và mật khẩu

Đã triển khai hai modal trên trang hồ sơ dùng chung cho ADMIN, WAREHOUSE_MANAGER và STAFF:

- `PUT /api/auth/me`: sửa họ tên/email/điện thoại/địa chỉ của tài khoản lấy từ JWT; giữ username và role. Giao diện cập nhật cache người đăng nhập để tên trên sidebar thay đổi ngay.
- `POST /api/auth/change-password`: kiểm tra mật khẩu hiện tại, mật khẩu mới tối thiểu 8 ký tự, xác nhận khớp và khác mật khẩu cũ. Giới hạn 72 byte UTF-8 theo BCrypt. Thành công trả 204 và giao diện đăng xuất.
- JWT mới có dấu HMAC của thông tin xác thực, kiểm tra theo database ở mỗi request. Đổi mật khẩu vô hiệu hóa toàn bộ token trước đó, không cần sửa schema. Token trước bản triển khai này không có dấu kiểm tra nên cũng cần đăng nhập lại một lần.

Lượt kiểm tra sau thay đổi: **43 test backend đạt, không lỗi/bỏ qua**, gồm 21 test Admin, 7 test service tài khoản, 3 test đăng nhập, 3 test quyền yêu cầu sản xuất và 9 test hồ sơ/mật khẩu mới. Lint frontend và build production đều thành công. Đây là lượt kiểm tra riêng, không cộng trùng các test đã chạy bên dưới.

Test mới ở `backend/src/test/java/mrp/auth/ProfileAndPasswordIntegrationTest.java`: sửa hồ sơ cả ba role, giữ nguyên định danh/quyền, không sửa tài khoản khác; kiểm tra token cũ bị 401, mật khẩu cũ không đăng nhập được, mật khẩu mới đăng nhập được; validation, nhập sai mật khẩu vẫn giữ phiên và chặn request chưa xác thực. Dùng H2 tạm và rollback, không đổi dữ liệu tài khoản ứng dụng đang chạy. Chưa xác minh thao tác UI bằng trình duyệt do kết nối công cụ không khả dụng ở lượt kiểm tra trước.

Các phát hiện bên dưới là kết quả kiểm tra ban đầu; mục sửa hồ sơ/đổi mật khẩu đã được xử lý trong bản cập nhật này.

## Kết quả chạy mới

- Backend: **61 ca đạt, 0 lỗi, 0 bỏ qua** qua hai lượt chạy: 40 ca hồi quy có sẵn và 21 ca tích hợp Admin mới.
- Frontend: **4/4 test đạt**, build production thành công.
- Lint hoàn tất với 2 cảnh báo import không dùng (`Alert`, `TeamOutlined`) tại `frontend/src/pages/admin/StaffPage.jsx`.
- Test tích hợp dùng profile `test`, database H2 trong bộ nhớ và transaction rollback. Không ghi vào database PostgreSQL của ứng dụng.

## Phạm vi đã xác minh

| Nhóm | Kiểm tra | Kết quả |
|---|---|---|
| Danh mục, NCC, sản phẩm, NVL | Tạo, đọc chi tiết, tìm từ khóa, phân trang, sắp xếp, sửa, xóa bản ghi test và kiểm tra 404 sau xóa | Đạt API |
| Sản phẩm, NVL | Vô hiệu hóa, lọc active/inactive, kích hoạt lại | Đạt API |
| Sản phẩm | Lọc danh mục/khoảng giá; chặn xóa danh mục đang có sản phẩm | Đạt API |
| NCC | Kết hợp lọc tên, điện thoại, email | Đạt API |
| Validation | Chặn payload thiếu/không hợp lệ ở 4 tài nguyên | Đạt API |
| Tài khoản | Tạo cả 3 role và hồ sơ tương ứng; chặn trùng username; tìm/lọc; khóa/mở khóa; đổi vai trò | Đạt API |
| Đăng nhập | Tài khoản mới đăng nhập được; khóa thì 401; mở khóa đăng nhập lại; đăng nhập sau đổi role phản ánh role mới | Đạt API |
| Dashboard, nhân viên, hồ sơ | Các API dữ liệu dashboard, `/api/staff`, `/api/auth/me` trả 200 cho Admin | Đạt API |
| Phân quyền | Admin đọc yêu cầu sản xuất trả 403; STAFF/WM không quản lý tài khoản và không tạo dữ liệu danh mục | Đạt API |
| Báo cáo | Admin đọc NVL/thành phẩm theo tháng, quý, năm; test hồi quy tính số liệu/boundary/validation | Đạt API/service |
| Bảo vệ tài khoản | Test service chặn tự khóa, bảo vệ Admin cuối cùng, chặn đổi role có dữ liệu tham chiếu | Đạt service |
| Frontend dùng chung | Lấy đủ trang lựa chọn, lỗi trang sau, chặn gửi trùng, thử lại sau lỗi | 4/4 đạt |

## Phần thiếu so với plan và điểm cần lưu ý

1. `ProfilePage.jsx` hiện chỉ hiển thị thông tin. Chưa có sửa hồ sơ/đổi mật khẩu; backend auth hiện chỉ có login và me. Đây là chức năng chưa triển khai theo mục 3.1 của `FRONTEND_PLAN.md`.
2. Chưa có xóa tài khoản trên `UsersPage.jsx` hoặc endpoint xóa tại `AdminUserController`. Plan có yêu cầu này.
3. `Phase0SecurityAndDocsTest.java` và `Phase1AuthAndAdminApiTest.java` đang là file rỗng; không được tính vào số ca đã chạy.
4. Qua đọc mã nguồn, dropdown danh mục sản phẩm trong `CatalogPage.jsx` chỉ lấy trang đầu size=100. Khi có trên 100 danh mục, lựa chọn có thể thiếu. Chưa tái hiện bằng UI hoặc fixture lớn trong lượt này.

## Giới hạn xác minh UI

Frontend cổng 5173 trả HTTP 200 và backend cổng 8080 yêu cầu xác thực. Tuy nhiên, Computer Use báo `Computer Use native pipe is unavailable` (đã thử lại), còn browser inventory báo `The browser access host is unavailable`. Chưa chạy được thao tác bấm form, điều hướng, bộ lọc hoặc kiểm tra console trên trình duyệt trong lượt này. Build thành công không thay thế kiểm thử UI.

## Chạy lại

Từ `backend/`:

```powershell
.\mvnw.cmd '-Dtest=AdminFunctionsIntegrationTest,AdminUserServiceTest,AuthServiceTest,StockReportServiceTest,StockReportIntegrationTest,ProductionRequestAuthorizationTest' '-Ddebug=false' '-Dlogging.level.root=WARN' test
```

Từ `frontend/`:

```powershell
npm.cmd run lint
node --test src/api/allPages.test.js src/api/singleFlight.test.js
npm.cmd run build
```

Test mới: `backend/src/test/java/mrp/admin/AdminFunctionsIntegrationTest.java`. Kết quả máy đọc được ở `backend/target/surefire-reports/TEST-*.xml` (các file khác trong thư mục có thể thuộc lượt chạy trước).

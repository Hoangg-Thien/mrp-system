# Phase 1 – Authentication & Admin Account Management

## 1. Tổng quan

Phase 1 đã triển khai API đăng nhập, xác thực JWT và quản trị tài khoản người dùng cho hệ thống MRP, **không thay đổi schema PostgreSQL hiện có**.

Phạm vi triển khai:

- Đăng nhập bằng username/password.
- Phát hành JWT Bearer token.
- Xác thực JWT theo dữ liệu hiện tại trong database.
- API lấy thông tin tài khoản hiện tại.
- ADMIN quản lý tài khoản STAFF, WAREHOUSE_MANAGER và ADMIN.
- Bootstrap admin đầu tiên bằng biến môi trường.
- Bảo vệ tài khoản admin cuối cùng còn hoạt động.

## 2. API đã triển khai

### Authentication

| Method | Endpoint | Quyền | Mô tả |
|---|---|---|---|
| `POST` | `/api/auth/login` | Public | Đăng nhập và nhận JWT token |
| `GET` | `/api/auth/me` | Authenticated | Lấy thông tin user/profile hiện tại |

### Admin account management

Tất cả endpoint bên dưới yêu cầu role `ADMIN`.

| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/api/admin/users` | Danh sách user có phân trang, lọc role/trạng thái và tìm kiếm keyword |
| `POST` | `/api/admin/users` | Tạo user và profile tương ứng với role |
| `GET` | `/api/admin/users/{id}` | Xem chi tiết user |
| `POST` | `/api/admin/users/{id}/role` | Đổi role user |
| `POST` | `/api/admin/users/{id}/lock` | Khóa user |
| `POST` | `/api/admin/users/{id}/unlock` | Mở khóa user |
| `DELETE` | `/api/admin/users/{id}` | Xóa user khi không còn được tham chiếu |

## 3. Quy tắc nghiệp vụ chính

- Không có public registration; tài khoản chỉ được tạo bởi ADMIN hoặc bootstrap ban đầu.
- Mật khẩu được lưu dưới dạng hash bằng `PasswordEncoder`, không lưu plaintext.
- Username phải duy nhất.
- User bị khóa không thể đăng nhập.
- JWT được kiểm tra lại với database ở mỗi request; thay đổi `role` hoặc `is_active` có hiệu lực ngay cả khi token cũ chưa hết hạn.
- Không trả lộ thông tin để phân biệt username không tồn tại với mật khẩu sai khi đăng nhập.
- Không cho phép thao tác làm mất admin cuối cùng đang hoạt động, bao gồm khóa, xóa hoặc đổi role admin cuối cùng sang role khác.
- Khi đổi role, profile tương ứng được tạo/xóa theo role mới và các ràng buộc tham chiếu của database được kiểm tra trước.
- Các lỗi nghiệp vụ được chuyển thành mã lỗi HTTP phù hợp theo quy ước chung của backend.

## 4. Bootstrap admin

`BootstrapAdminRunner` chạy khi ứng dụng khởi động và chỉ tạo admin khi:

1. Bảng `users` đang rỗng.
2. `BOOTSTRAP_ADMIN_USERNAME` không rỗng.
3. `BOOTSTRAP_ADMIN_PASSWORD` có tối thiểu 8 ký tự.

Cấu hình mẫu trong `.env.example`:

```dotenv
BOOTSTRAP_ADMIN_USERNAME=admin
BOOTSTRAP_ADMIN_PASSWORD=change_me_please
BOOTSTRAP_ADMIN_FULL_NAME=System Administrator
```

Trong môi trường thực tế, cần thay mật khẩu mẫu bằng mật khẩu mạnh và không commit file `.env` chứa secret.

## 5. Cấu hình bảo mật và database

- Spring Boot `3.5.13`, Java `21`.
- JWT HS256 thông qua Spring Security OAuth2 Resource Server.
- `ddl-auto: validate`, nên Hibernate không tự tạo hoặc sửa bảng.
- `open-in-view: false`.
- Datasource PostgreSQL đọc từ `DB_URL`, `DB_USERNAME` và `DB_PASSWORD`.
- JWT đọc từ `JWT_SECRET` và `JWT_EXPIRATION_SECONDS`.
- CORS đọc từ `CORS_ALLOWED_ORIGINS`.
- `docker-compose.yml` mount `database/mrp.sql` vào thư mục init của PostgreSQL.

Các bảng/profile được sử dụng theo schema hiện tại gồm `users`, `staff`, `warehouse_managers` và `admins`; không bổ sung cột hoặc bảng mới.

## 6. Kiểm thử và trạng thái xác minh

### Đã xác nhận

Các unit test nghiệp vụ đã pass theo Surefire reports:

| Test suite | Kết quả |
|---|---:|
| `mrp.auth.AuthServiceTest` | 3 tests, 0 failures, 0 errors |
| `mrp.admin.AdminUserServiceTest` | 7 tests, 0 failures, 0 errors |
|

Các nhóm logic đã được kiểm tra gồm:

- Login thành công và tạo token.
- Username không tồn tại hoặc password sai trả lỗi xác thực tổng quát.
- Tạo user và profile.
- Khóa/mở khóa user.
- Đổi role.
- Bảo vệ admin cuối cùng.
- Kiểm tra user còn được tham chiếu trước khi xóa.

### Chưa pass/chưa xác minh đầy đủ

`mrp.ApplicationTests.contextLoads` hiện lỗi khi khởi tạo ApplicationContext:

```text
Unable to determine Dialect without JDBC metadata
```

Nguyên nhân được ghi nhận là môi trường chạy test không cung cấp kết nối PostgreSQL/JDBC metadata tại thời điểm context load. Vì vậy, kết quả full `mvn test` chưa được xem là xanh.

PostgreSQL thực tế, việc chạy schema `database/mrp.sql`, khả năng `ddl-auto: validate` khớp hoàn toàn với entity và hành vi bootstrap trên database thật **chưa được xác minh trong phiên này**. Không thực hiện kiểm tra Docker do trước đó thao tác kiểm tra Docker đã bị từ chối.

## 7. Các file chính

- `backend/src/main/java/mrp/auth/` – login, JWT và user view.
- `backend/src/main/java/mrp/admin/` – API và service quản trị user.
- `backend/src/main/java/mrp/security/` – security filter/configuration và DB-backed JWT validation.
- `backend/src/main/java/mrp/user/` – entity, repository và role user.
- `backend/src/main/java/mrp/common/` – exception/business error dùng chung.
- `backend/src/main/java/mrp/config/BootstrapAdminRunner.java` – tạo admin đầu tiên.
- `backend/src/test/java/mrp/auth/AuthServiceTest.java` – unit test authentication.
- `backend/src/test/java/mrp/admin/AdminUserServiceTest.java` – unit test admin account management.
- `.env.example` – biến môi trường mẫu.
- `backend/src/main/resources/application.yaml` – cấu hình Spring Boot.

## 8. Việc cần làm tiếp theo

1. Cung cấp PostgreSQL đang chạy hoặc cấu hình test database phù hợp.
2. Chạy lại toàn bộ test Maven và xử lý `ApplicationTests.contextLoads`.
3. Xác minh schema bằng `database/mrp.sql` với `ddl-auto: validate`.
4. Xác minh bootstrap admin trên database rỗng và database đã có dữ liệu.
5. Bổ sung/chạy E2E test cho HTTP status `401`, `403`, `409` và các endpoint Swagger.
6. Sau khi môi trường database sẵn sàng, cập nhật lại trạng thái xác minh trong tài liệu này.

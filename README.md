# Quản lý kho và sản xuất nội thất

## Tổng quan

Ứng dụng lập kế hoạch nhu cầu nguyên vật liệu (MRP) cho xưởng nội thất. Quản lý kho xác định số lượng cần sản xuất và vật tư cần nhập thêm. Nhân viên nhận việc và bàn giao thành phẩm theo từng đợt.

## Tính năng chính

- **Lập kế hoạch vật tư.** MRP tính lượng cần sản xuất từ tồn thành phẩm, lượng chờ xuất và sản lượng dự kiến. Hệ thống đối chiếu nhu cầu theo định mức vật tư (BOM) với lượng tồn chưa cam kết để xác định vật tư thiếu.
- **Cấp vật tư và sản xuất.** Mỗi yêu cầu sản xuất gắn với một phiên bản BOM. Quản lý kho duyệt cấp vật tư cho nhân viên. Khi ghi nhận thành phẩm, hệ thống kiểm tra lượng vật tư đã cấp theo định mức cho sản lượng hoàn thành lũy kế.
- **Nhập xuất và kiểm kê.** Quản lý kho ghi nhận vật tư nhập từ nhà cung cấp, duyệt xuất thành phẩm và điều chỉnh chênh lệch kiểm kê. Báo cáo tháng, quý và năm thể hiện tồn đầu kỳ, phát sinh trong kỳ và tồn cuối kỳ.

## Công nghệ

- **Backend** dùng Java 21, Spring Boot 3.5, Spring Data JPA, JDBC và PostgreSQL 17.
- **Giao diện** dùng React 19, Vite, Ant Design và TanStack Query.
- **Kiểm thử** dùng JUnit 5 cho phép tính MRP, MockMvc cho phân quyền API và Testcontainers để kiểm tra báo cáo kho trên PostgreSQL.

## Kiến trúc hệ thống

Giao diện React gọi REST API của backend Spring Boot. Các phân hệ nhập hàng, kho và sản xuất dùng chung `StockLedger` để ghi biến động kho. Spring Security xác thực bằng JWT, dùng BCrypt để băm mật khẩu và phân quyền cho quản trị viên, quản lý kho và nhân viên.

Khi duyệt xuất, backend cập nhật trạng thái phiếu và ghi vào sổ `stock_movements` trong cùng giao dịch. Sổ chỉ cho phép ghi thêm. Trigger PostgreSQL cập nhật số dư trong `inventories`, còn các ràng buộc ngăn tồn âm và ghi trùng dòng chứng từ. [Lược đồ SQL](database/mrp.sql)

## Chạy dự án

Cần JDK 21, Node.js 22.12 trở lên kèm npm và Docker có Compose. Từ thư mục gốc, tạo hai tệp cấu hình cục bộ.

```sh
cp .env.example .env
cp frontend/.env.example frontend/.env
```

Trong `.env` ở thư mục gốc, đặt `DB_PASSWORD`, `JWT_SECRET` (ít nhất 32 byte) và `BOOTSTRAP_ADMIN_PASSWORD` (ít nhất 8 ký tự). Lệnh sau khởi động PostgreSQL và nạp `database/mrp.sql` khi khởi tạo volume lần đầu.

```sh
docker compose up -d postgres
```

Khi PostgreSQL sẵn sàng, chạy hai khối lệnh trong hai terminal riêng, đều bắt đầu từ thư mục gốc. Với PowerShell trên Windows, dùng `.\mvnw.cmd` và `npm.cmd`.

```sh
cd backend
sh mvnw spring-boot:run
```

```sh
cd frontend
npm ci
npm run dev
```

Backend tạo tài khoản quản trị khi bảng `users` trống. Đăng nhập bằng `BOOTSTRAP_ADMIN_USERNAME` và `BOOTSTRAP_ADMIN_PASSWORD` trong `.env`. [Mở ứng dụng](http://localhost:5173) · [Swagger UI](http://localhost:8080/swagger-ui)

# Hệ thống quản lý kho và sản xuất tích hợp MRP

Đồ án Hệ thống thông tin doanh nghiệp về quản lý kho và sản xuất nội thất.
Nhóm 11, học phần 841065, học kỳ 1 năm học 2026–2027

## Thành viên theo biên bản SGU

- Nguyễn Duy Khôi
- Lưu Gia Huy
- Lê Viết Hoàng Thiên
- Châu Uy Vủ

## Phạm vi phần mềm

ADMIN quản lý tài khoản, vai trò, khóa/mở khóa và danh mục dùng chung.
WAREHOUSE_MANAGER nhập NVL, quản lý BOM, tính MRP, giao sản xuất,
duyệt xuất NVL/thành phẩm, kiểm kê và xem báo cáo kho.
STAFF nhận việc được giao, đề nghị cấp NVL, thực hiện sản xuất,
đề nghị xuất thành phẩm và cập nhật hồ sơ cá nhân.

## Cài đặt trên Windows

Chuẩn bị Java 21, Node.js tương thích Vite 8 và PostgreSQL 17.
Cấu hình Docker Compose dùng postgres:17. Kiểm tra ngày 19/11/2026
được thực hiện bằng Java 21, Node.js 24.21 và PostgreSQL 18.6.

1. Tại thư mục dự án, tạo .env từ .env.example và sửa thông tin
   kết nối CSDL, JWT_SECRET và mật khẩu admin khởi tạo.
   Nếu dùng Docker, chạy docker compose up -d.
   Nếu dùng PostgreSQL cục bộ, tạo database trống và chạy
   database/mrp.sql bằng psql hoặc pgAdmin. Script chỉ khởi tạo cấu trúc.
   Khi khởi tạo thủ công, cập nhật DB_URL, DB_USERNAME và DB_PASSWORD.

2. Tại backend, chạy .\mvnw.cmd spring-boot:run.
   Backend mặc định dùng cổng 8080. Admin khởi tạo được tạo khi bảng
   users trống, theo thông tin BOOTSTRAP_ADMIN_* trong .env.

3. Tại frontend, tạo .env từ .env.example.
   VITE_API_BASE_URL mặc định là http://localhost:8080.
   Chạy npm.cmd ci rồi npm.cmd run dev.
   Truy cập http://localhost:5173.

4. Admin tạo tài khoản quản lý kho và nhân viên, khai báo danh mục,
   nhà cung cấp, nguyên vật liệu và sản phẩm trước khi demo.

## Luồng demo

Nhập NVL và hoàn tất phiếu nhập.
Tạo BOM cho sản phẩm, tính MRP và giao yêu cầu sản xuất cho nhân viên.
Nhân viên đề nghị xuất NVL, quản lý kho duyệt đủ lượng theo BOM.
Nhân viên bắt đầu và hoàn tất đợt sản xuất để nhập thành phẩm.
Nhân viên lập đề nghị xuất bán, quản lý kho duyệt để giảm thành phẩm.
Đối chiếu tồn kho, sổ biến động và báo cáo tháng/quý/năm.

## Lưu ý nghiệp vụ

Lượng sản xuất chưa hoàn thành chỉ được cộng vào khả dụng dự kiến.
Khi hoàn tất sản xuất, hệ thống kiểm tra tổng NVL đã duyệt xuất cho
yêu cầu theo BOM, bao gồm lượng dùng cho các đợt đã hoàn thành trước.
Các giao dịch xuất thiếu tồn bị chặn. Phiếu kết thúc được giữ để truy vết.
Tài khoản đã có lịch sử được quản lý bằng khóa/mở khóa.

## Kiểm thử

Tại backend, chạy .\mvnw.cmd test.
Các lớp Testcontainers cần Docker. Các lớp PostgreSQL dùng biến
MRP_REGRESSION_DB_URL phải chạy trên CSDL thử dùng một lần, đã nạp
database/mrp.sql, cùng MRP_REGRESSION_DB_USER và MRP_REGRESSION_DB_PASSWORD.
Tại frontend, chạy npm.cmd run lint và npm.cmd run build.
Các lệnh test:refresh, test:fg-note, test:stock-report,
test:selections và test:mir-warning dùng Playwright.
Các script hiện tại tìm Playwright tại backend/target/ui-test-tools/node_modules/playwright.
Những script hỗ trợ PLAYWRIGHT_MODULE cũng nhận đường dẫn qua biến đó.

## Sao lưu và phục hồi

Dùng pg_dump -Fc để sao lưu CSDL PostgreSQL, ghi rõ máy chủ, cổng,
tài khoản và tên CSDL trong tham số lệnh.
Dùng pg_restore để phục hồi sang một CSDL trống riêng.
Sau phục hồi, đối chiếu tài khoản, chứng từ, sổ kho và số dư tồn kho.

Xem thêm [tài liệu frontend](frontend/README.md) và [sơ đồ cơ sở dữ liệu](database/mrp.png).

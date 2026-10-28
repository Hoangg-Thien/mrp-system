# MRP System — Quản lý kế hoạch sản xuất và kho

Ứng dụng quản lý nguyên vật liệu, định mức BOM, kế hoạch nhu cầu vật tư (MRP), sản xuất và xuất nhập tồn. Backend cung cấp REST API với xác thực JWT; frontend chia khu vực làm việc theo ba vai trò.

## Chức năng

| Vai trò | Khu vực | Chức năng chính |
|---|---|---|
| ADMIN | `/admin` | Quản lý tài khoản, danh mục, sản phẩm, nhà cung cấp, nguyên vật liệu; xem nhân viên và báo cáo kho. |
| WAREHOUSE_MANAGER | `/warehouse` | Nhập kho, quản lý tồn và sổ kho, điều chỉnh kho, tạo BOM, tính MRP, giao yêu cầu sản xuất, duyệt/từ chối phiếu xuất, xem báo cáo. |
| STAFF | `/staff` | Xem yêu cầu được giao, đề nghị xuất nguyên vật liệu, thực hiện sản xuất, đề nghị xuất thành phẩm. |

Luồng chính:

1. Admin tạo tài khoản và dữ liệu danh mục.
2. Quản lý kho tạo, hoàn tất phiếu nhập nguyên vật liệu.
3. Quản lý kho thiết lập BOM, tính MRP và giao yêu cầu sản xuất.
4. Nhân viên lập đề nghị xuất NVL; quản lý kho duyệt.
5. Nhân viên tạo đợt sản xuất và hoàn tất để nhập kho thành phẩm.
6. Nhân viên đề nghị xuất thành phẩm; quản lý kho duyệt và theo dõi báo cáo.

## Công nghệ và yêu cầu

| Thành phần | Công nghệ / yêu cầu |
|---|---|
| Backend | Java 21, Spring Boot 3.5.13, Spring Security, Spring Data JPA, Bean Validation |
| Database | PostgreSQL 17; schema và ràng buộc nghiệp vụ trong `database/mrp.sql` |
| Frontend | React 19, Vite 8, Ant Design 6, TanStack Query 5, React Router 7, Recharts |
| Công cụ frontend | Node.js 22.12+ hoặc Node.js 20.19+, npm; phiên bản dependency được khóa trong `package-lock.json` |
| Kiểm thử | JUnit, Spring Boot Test, MockMvc, H2 và PostgreSQL Testcontainers; Node test runner |
| Môi trường local | Git, JDK 21, Docker Engine/Desktop và Docker Compose v2 |

Repo có Maven Wrapper nên không cần cài Maven riêng. Lần build đầu cần kết nối mạng để tải dependency và Docker image.

## Cấu trúc

```text
mrp-system/
├── backend/
│   ├── src/main/java/mrp/       # Các module nghiệp vụ và cấu hình
│   ├── src/main/resources/     # Cấu hình Spring Boot
│   ├── src/test/               # Unit và integration tests
│   ├── mvnw / mvnw.cmd         # Maven Wrapper
│   └── pom.xml
├── frontend/
│   ├── src/api/                # HTTP client, query/mutation hooks
│   ├── src/auth/               # Xác thực
│   ├── src/components/         # Thành phần dùng chung
│   ├── src/layouts/            # Layout theo role
│   ├── src/pages/              # Màn hình nghiệp vụ
│   └── package.json
├── database/
│   ├── mrp.sql                 # Schema PostgreSQL
│   ├── mrp.png                 # Sơ đồ database
│   └── test_proper.sql         # 31 ca kiểm tra toàn vẹn database
├── .env.example
├── docker-compose.yml          # Chỉ chạy PostgreSQL
└── README.md
```

`docs/` và `docx/` dành cho tài liệu/báo cáo local và không được Git theo dõi. Seed, backup và các file khác trong `database/` cũng chỉ giữ local. Bản clone không kèm dữ liệu demo, tài khoản cá nhân hoặc script QA tạm trong `backend/target/`.

## Cài đặt và chạy local

Các lệnh dưới đây dành cho **bản cài local mới**. Nếu đã có `.env`, database hoặc Docker volume, giữ lại cấu hình/dữ liệu hiện tại và kiểm tra trước khi chạy. Không cần nạp lại schema mỗi lần khởi động.

### 1. Chuẩn bị cấu hình

Clone repo, mở terminal tại thư mục gốc. Trên PowerShell, tạo file cấu hình nếu chưa có:

```powershell
if (!(Test-Path .env)) { Copy-Item .env.example .env }
if (!(Test-Path frontend/.env)) { Copy-Item frontend/.env.example frontend/.env }
```

Trên Linux/macOS, sao chép hai file `.env.example` tương ứng sang `.env` nếu chưa tồn tại.

Chỉnh `.env` ở thư mục gốc:

| Biến | Ý nghĩa |
|---|---|
| `DB_NAME` | Tên database do Docker khởi tạo, mặc định `mrp`. |
| `DB_USERNAME`, `DB_PASSWORD` | Tài khoản PostgreSQL. Đặt mật khẩu riêng thay giá trị mẫu. |
| `DB_PORT` | Cổng PostgreSQL trên máy host, mặc định `5432`. |
| `DB_URL` | JDBC URL, ví dụ `jdbc:postgresql://localhost:5432/mrp`. Phải khớp tên DB và cổng host. |
| `JWT_SECRET` | Khóa ký JWT riêng, tối thiểu 32 ký tự; thay khóa mẫu trước khi sử dụng. |
| `JWT_EXPIRATION_SECONDS` | Thời hạn access token; mẫu cấu hình là `86400` giây. |
| `CORS_ALLOWED_ORIGINS` | Origin frontend được cho phép, mặc định `http://localhost:5173`. |
| `BOOTSTRAP_ADMIN_USERNAME` | Tên tài khoản admin đầu tiên. |
| `BOOTSTRAP_ADMIN_PASSWORD` | Mật khẩu admin đầu tiên, ít nhất 8 ký tự; thay giá trị mẫu. |
| `BOOTSTRAP_ADMIN_FULL_NAME` | Tên hiển thị của admin đầu tiên. |

Trong `frontend/.env`:

```dotenv
VITE_API_BASE_URL=http://localhost:8080
```

Biến `VITE_*` được đưa vào frontend và có thể đọc từ trình duyệt; không đặt bí mật trong các biến này. `.env` và `.env.e2e` không được commit.

### 2. Khởi động PostgreSQL

Tại thư mục gốc:

```sh
docker compose up -d postgres
docker compose ps
docker compose logs --tail=50 postgres
```

Compose dùng PostgreSQL 17, tên container `mrp-postgres`, volume `mrp_pgdata`. Script `database/mrp.sql` chỉ tự chạy khi PostgreSQL khởi tạo thư mục dữ liệu trống lần đầu. Sửa `.env` hoặc khởi động lại container không tự đổi tài khoản/database đã tồn tại trong volume.

Schema phải có trước khi backend chạy: Hibernate được cấu hình `ddl-auto: validate`, không tự tạo hoặc cập nhật bảng. Không xóa volume để xử lý lỗi cấu hình khi cần giữ dữ liệu.

### 3. Chạy backend

Mở terminal riêng tại `backend/`.

PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Linux/macOS:

```sh
./mvnw spring-boot:run
```

Backend đọc `.env` tại thư mục chạy và thư mục cha. Cổng mặc định là `8080`; có thể cấu hình bằng biến môi trường `SERVER_PORT`.

| Địa chỉ | Mục đích |
|---|---|
| `http://localhost:8080/swagger-ui` | Swagger UI để xem endpoint và thử API |
| `http://localhost:8080/v3/api-docs` | OpenAPI JSON |

Admin bootstrap chỉ được tạo khi bảng `users` trống, username không rỗng và mật khẩu dài ít nhất 8 ký tự. Nếu DB đã có user, đổi biến bootstrap không tạo lại hay đổi mật khẩu tài khoản cũ.

### 4. Chạy frontend

Mở terminal riêng tại `frontend/`:

```sh
npm ci
npm run dev
```

Truy cập `http://localhost:5173`, đăng nhập bằng admin đã cấu hình. Admin tạo tài khoản `WAREHOUSE_MANAGER` và `STAFF` trong màn hình quản lý tài khoản; không có bộ tài khoản demo mặc định được chia sẻ trong repo.

Nếu Vite chuyển sang cổng khác vì cổng 5173 đang bận, cần dùng đúng địa chỉ và cập nhật `CORS_ALLOWED_ORIGINS`. Khởi động lại dịch vụ tương ứng sau khi đổi `.env`.

## API và quy ước sử dụng

Swagger là nơi tra cứu đầy đủ method, request body và response. Các nhóm API chính:

| Nhóm | Đường dẫn |
|---|---|
| Đăng nhập / thông tin tài khoản | `/api/auth` |
| Quản trị user | `/api/admin/users` |
| Danh mục | `/api/categories`, `/api/products`, `/api/materials`, `/api/suppliers` |
| Kho | `/api/inventories`, `/api/stock-movements`, `/api/stock-adjustments` |
| Nhập kho | `/api/purchase-receipts` |
| BOM / MRP | `/api/boms`, `/api/mrp` |
| Sản xuất | `/api/production-requests`, `/api/material-issue-requests`, `/api/productions` |
| Xuất thành phẩm | `/api/fg-issue-requests` |
| Báo cáo | `/api/reports` |

API được bảo vệ sử dụng header `Authorization: Bearer <access_token>`. Quyền truy cập được kiểm tra theo role và phạm vi nghiệp vụ. Không đưa token vào source, ảnh chụp hay log chia sẻ.

Số lượng thành phẩm/sản xuất phải là số nguyên; NVL hỗ trợ số thập phân. Tồn kho thay đổi qua nghiệp vụ nhập, xuất, sản xuất và điều chỉnh kho; không cập nhật trực tiếp bảng tồn để mô phỏng thao tác nghiệp vụ.

## Kiểm thử

### Backend

Tại `backend/`, bật Docker để chạy cả PostgreSQL Testcontainers:

```powershell
.\mvnw.cmd -q test
```

Linux/macOS dùng `./mvnw -q test`. Xem kết quả từng suite trong `backend/target/surefire-reports/`. Lớp `E2eFindingsIntegrationTest` tạo PostgreSQL riêng; có thể bị skip khi Docker không khả dụng, vì vậy cần xem số test skipped thay vì chỉ nhìn exit code.

### Frontend

Tại `frontend/`:

```sh
npm run lint
node --test src/api/allPages.test.js src/api/singleFlight.test.js
npm run build
```

### Kiểm tra toàn vẹn PostgreSQL

`database/test_proper.sql` gồm 31 ca thử cho schema hiện có. Chỉ chạy trên **DB test riêng**, đã nạp `mrp.sql`, ví dụ `mrp_e2e`:

```sh
psql -h localhost -p 5432 -U postgres -d mrp_e2e -W -v ON_ERROR_STOP=1 -f database/test_proper.sql
```

Lệnh chạy từ thư mục gốc, yêu cầu `psql` trong PATH; thay host, port và user bằng cấu hình DB test của bạn. `-W` yêu cầu nhập mật khẩu tương tác.

Script dùng transaction và rollback dữ liệu thử ở cuối, nhưng sequence có thể tăng. Kiểm tra dòng tổng kết **31 total / 31 PASS / 0 FAIL**: script không cố ý trả lỗi cuối chỉ vì có test FAIL, nên exit code 0 chưa đủ chứng minh bộ test đạt.

Nếu DB test chạy trong Docker, dùng `docker cp` đưa file vào đúng container test rồi `psql -f` trong container. Trên PowerShell tránh truyền nội dung SQL qua pipe hoặc toán tử `<` để giữ nguyên tiếng Việt. Không dùng container/database đang chứa dữ liệu làm việc để thử các ca ghi dữ liệu.

### E2E thủ công

Dùng DB riêng, backend trỏ rõ vào DB đó, frontend trỏ đúng backend test và tài khoản cho ba role. File `.env.e2e` là cấu hình local, **không được backend tự nạp chỉ vì file tồn tại**; cần truyền biến môi trường hoặc cấu hình Spring phù hợp khi khởi động.

Đi qua luồng nghiệp vụ ở đầu README, đối chiếu tồn kho sau từng bước; kiểm tra từ chối/hủy, thiếu NVL, quyền giữa các role và truy cập chứng từ của nhân viên khác. Script QA tạm và báo cáo local không đi kèm bản clone.

## Build và chạy bản build

Backend, tại `backend/`:

```powershell
.\mvnw.cmd clean package
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

Linux/macOS thay lệnh wrapper bằng `./mvnw clean package`. Cấu hình datasource và JWT vẫn phải được cung cấp khi chạy JAR. `clean` xóa `backend/target/`, gồm cả script QA tạm nếu đang lưu ở đó.

Frontend, tại `frontend/`:

```sh
npm run build
npm run preview
```

Build frontend tạo thư mục `frontend/dist/`. `preview` dùng để xem thử bản build; nếu chạy ở origin khác, cấu hình CORS tương ứng. Khi triển khai, cấu hình máy chủ web phục vụ `dist/` với SPA fallback về `index.html` cho các route như `/admin`, `/warehouse`, `/staff`. API base URL phải được đặt đúng trước lúc build.

Khi cần cập nhật khai báo kiểu API từ backend đang chạy ở cổng 8080:

```sh
npm run gen:api
```

Lệnh này chạy tại `frontend/` và ghi lại `src/api/schema.d.ts`; kiểm tra diff trước khi commit.

## Xử lý lỗi thường gặp

| Hiện tượng | Cách kiểm tra |
|---|---|
| Backend không kết nối DB | Kiểm tra `docker compose ps`, `DB_URL`, cổng host và tài khoản đã được tạo trong volume. |
| Backend báo thiếu bảng | Kiểm tra đúng DB và schema đã được nạp; script init Docker không chạy lại trên volume có sẵn. |
| Không đăng nhập được admin bootstrap | Kiểm tra bảng user đã có dữ liệu chưa và tài khoản đã tạo trước đó; bootstrap không reset mật khẩu. |
| UI báo không kết nối máy chủ | Kiểm tra backend đang chạy, `VITE_API_BASE_URL` và Network trong DevTools. |
| Lỗi CORS | So sánh origin frontend chính xác với `CORS_ALLOWED_ORIGINS`, kể cả host và port. |
| Testcontainers bị skip/lỗi kết nối Docker | Khởi động Docker, kiểm tra `docker info`, rồi chạy lại suite. |
| Đổi `.env` nhưng không có hiệu lực | Khởi động lại backend/Vite; với frontend đã build cần build lại. |

Trước khi chia sẻ thay đổi, chạy test/lint/build và kiểm tra `git status`. Giữ `.env`, `.env.e2e`, backup DB và báo cáo cá nhân ở local. `.gitignore` không xóa các file đã tồn tại trong lịch sử commit.

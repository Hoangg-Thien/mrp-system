# Kết luận Phase 2 – Danh mục và dữ liệu đầu vào MRP

Ngày thực hiện: 2026-10-13. Đối chiếu mục P2 trong `docs/BACKEND_E2E_PLAN.md` và schema `database/mrp.sql`.

## 1. Kết luận

Đã triển khai phần code Phase 2: CRUD danh mục, sản phẩm, nhà cung cấp, nguyên vật liệu; bật/tắt sản phẩm và NVL; tìm kiếm, lọc, sắp xếp, phân trang; danh sách nhân viên active để giao việc.

Compile bằng Maven Wrapper/JDK 21 thành công, `EXIT_CODE=0`. Không chạy test theo yêu cầu của người dùng. Chưa xác minh API runtime, Swagger runtime hoặc schema validation trên PostgreSQL. Vì vậy đây là hoàn thành implementation, chưa phải hoàn thành toàn bộ tiêu chí nghiệm thu phase trong kế hoạch.

## 2. API và phân quyền

| Đường dẫn | Chức năng | Quyền |
|---|---|---|
| `/api/categories` | GET list, POST | Đọc: STAFF/WM/ADMIN; ghi: ADMIN |
| `/api/categories/{id}` | GET, PUT, DELETE | Đọc: STAFF/WM/ADMIN; ghi: ADMIN |
| `/api/products` và `/{id}` | GET list/detail, POST, PUT, DELETE | Đọc: STAFF/WM/ADMIN; ghi: ADMIN |
| `/api/suppliers` và `/{id}` | GET list/detail, POST, PUT, DELETE | Đọc: STAFF/WM/ADMIN; ghi: ADMIN |
| `/api/materials` và `/{id}` | GET list/detail, POST, PUT, DELETE | Đọc: STAFF/WM/ADMIN; ghi: ADMIN |
| `/api/products/{id}/activate`, `/deactivate` | POST | ADMIN |
| `/api/materials/{id}/activate`, `/deactivate` | POST | ADMIN |
| `/api/staff` | GET: `[{id, fullName}]`; id là `staff.id` | WM/ADMIN |

DELETE thành công trả 204. POST/PUT và activate/deactivate trả đối tượng với 200, nhất quán với controller Phase 1. Category/supplier không có endpoint activate/deactivate vì schema không có `is_active`.

## 3. Tìm kiếm và phân trang

Mọi danh sách catalog nhận `page` (mặc định 0), `size` (20, giới hạn 1–100), `sort`, `direction` (asc/desc, mặc định asc). Sort mặc định `name`. Thêm id làm tiêu chí phụ để thứ tự phân trang ổn định. Keyword không phân biệt hoa/thường; ký tự `%`, `_` và backslash được escape để tìm theo chuỗi nhập.

| Danh sách | Bộ lọc | Sort hợp lệ |
|---|---|---|
| Categories | keyword theo name | id, name |
| Products | keyword theo name/description, categoryId, minPrice, maxPrice, active | id, name, sellingPrice, createdAt |
| Suppliers | keyword theo name/phone/email; name, phone, email có thể kết hợp | id, name, phone, email |
| Materials | keyword theo code/name, active | id, code, name, standardCost, createdAt |

Products mặc định chỉ active, kể cả ADMIN. ADMIN có thể dùng `active=false` để xem inactive hoặc `active=true` để xem active; STAFF/WM truyền bộ lọc active sẽ nhận 403. Materials không truyền active thì trả cả hai trạng thái. Detail trả dữ liệu theo id kể cả inactive để giữ khả năng xem dữ liệu cũ.

Ví dụ: `GET /api/products?keyword=ban&categoryId=1&minPrice=0&maxPrice=5000000&sort=sellingPrice&direction=desc&page=0&size=20`.

## 4. Validation và nghiệp vụ

- Request riêng cho từng loại; tên/mã/đơn vị bắt buộc, độ dài theo schema. Giá và standardCost bắt buộc, không âm, tối đa 13 chữ số nguyên và 2 chữ số thập phân. Email được validation.
- Tên category và mã material được trim, pre-check unique; DB vẫn chặn xung đột đồng thời.
- Product kiểm tra category tồn tại trước khi tạo/cập nhật; không nhận active/id/createdAt từ request nghiệp vụ.
- Khoảng giá âm hoặc minPrice > maxPrice; page/size/sort/direction sai → 400 ProblemDetail.
- Resource không tồn tại → 404. Trùng unique hoặc vi phạm nghiệp vụ/tham chiếu → 409.
- Xóa category bị chặn khi còn product; supplier khi có purchase_receipt. Product/material kiểm tra tất cả bảng FK tương ứng trong schema, kể cả inventory, BOM, chứng từ và stock movement. Product/material đã dùng có thông báo gợi ý vô hiệu hóa. DB FK là chốt cuối nếu phát sinh tham chiếu đồng thời.
- Material không được đổi unit sau khi có stock movement hoặc BOM item. Update, activate/deactivate và delete material dùng `findByIdForUpdate` trong transaction.
- GET staff lọc user active và role STAFF; chỉ trả profile id và họ tên, sắp xếp theo họ tên/id.
- `requireActiveProduct` và `requireActiveMaterial` trong CatalogService trả 409 khi inactive. Các phase tiếp theo phải gọi chúng khi tạo BOM/phiếu nhập/phiếu xuất mới.

## 5. File thêm và sửa

Thêm package `backend/src/main/java/mrp/catalog/` với 16 file:

- Entity: Category, Product, Supplier, Material.
- Repository: CategoryRepository, ProductRepository, SupplierRepository, MaterialRepository.
- Controller: CategoryController, ProductController, SupplierController, MaterialController.
- CatalogRequests: request records và validation.
- CatalogQuery: tìm kiếm, whitelist sort và giới hạn phân trang.
- CatalogReferenceChecker: kiểm tra tham chiếu bằng JdbcTemplate, theo schema hiện có.
- CatalogService: transaction, CRUD, active checks và nghiệp vụ.

Thêm `common/BadRequestException.java`, `user/StaffController.java`, `user/StaffView.java`. Sửa `user/StaffRepository.java` để có projection nhân viên active.

Sửa `common/GlobalExceptionHandler.java`:

- Map BadRequestException và lỗi chuyển kiểu query/path parameter thành 400.
- Map AccessDeniedException từ method security thành 403, tránh handler tổng quát trả 500.
- Bỏ việc trả raw SQL driver message cho P0001; dùng thông báo public an toàn.

Không sửa schema, dependency, cấu hình datasource hay service xác thực/admin. Tài liệu này và `.gemini/MEMORY.md` ghi lại trạng thái P2.

## 6. Kiểm tra và phần còn chờ

Đã chạy `backend/mvnw.cmd -B -DskipTests compile` với JAVA_HOME trỏ tới JDK 21. Log `phase2-compile.log` ghi BUILD SUCCESS và EXIT_CODE=0, biên dịch 49 source files. Đã rà soát mapping entity, endpoint, whitelist sort và FK reference queries; diff check không in cảnh báo whitespace. Công cụ terminal báo không quan sát được completion ở một số lệnh nên kết quả compile được xác nhận từ nội dung log, không dựa trên trạng thái tool.

Chưa chạy CAT-01 đến CAT-08, test hồi quy P1, context/schema validation PostgreSQL hoặc kiểm tra OpenAPI runtime. Không tạo hoặc chạy container. Compile không xác minh JPQL khi khởi động, DB queries hoặc phân quyền qua HTTP.

CAT-07 cần P4/P5 có API phiếu nhập/BOM; điều kiện inactive đã được cung cấp ở P2 nhưng việc tích hợp và kiểm thử xuyên phase còn chờ. Khi triển khai các phase đó, phải gọi active checks trước khi ghi dòng mới. Với thao tác đồng thời đổi đơn vị NVL/tạo BOM hoặc movement, writer ở phase sau cần phối hợp khóa cùng material; riêng khóa ở P2 không bảo đảm mọi writer tương lai tuân thủ rule.

## 7. Hướng dẫn nghiệm thu sau này

1. Chạy compile/test với JDK 21 và Maven Wrapper; test Docker-free dùng H2 không thay thế PostgreSQL schema/triggers.
2. Trên PostgreSQL đã nạp mrp.sql, khởi động backend với ddl-auto validate và kiểm tra `/v3/api-docs` có các endpoint mới.
3. CAT-01: ADMIN CRUD; STAFF/WM ghi 403; anonymous 401.
4. CAT-02/03: lọc kết hợp, thứ tự và phân trang; sort lạ/size >100 → 400; mặc định products active; ADMIN active=false thấy inactive.
5. CAT-04/05: giá âm →400; unique→409; dùng dữ liệu tham chiếu thật để kiểm tra delete 409 và delete chưa dùng 204.
6. CAT-06: material đã có movement hoặc BOM không đổi unit được; transaction không đổi dữ liệu khi bị từ chối.
7. CAT-08: staff chỉ WM/ADMIN đọc, profile id đúng và không có tài khoản inactive.
8. CAT-07 sau P4/P5: NVL inactive không được thêm vào phiếu nhập/BOM; kiểm tra dữ liệu cũ vẫn nguyên vẹn. Kiểm tra product inactive ở BOM/phiếu xuất khi các API tương ứng được triển khai.
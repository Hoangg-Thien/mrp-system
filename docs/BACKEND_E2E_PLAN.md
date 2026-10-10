# Plan E2E backend – mrp-system (bản MRP-only, 20 bảng)

Cập nhật: 2026-10-06. Chỉ phần backend (Spring Boot 3.5 / Java 21 / PostgreSQL).

## 0. Phạm vi

- Repo này chỉ làm **Phần II (Admin + CSDL)** và **Phần III mục 5 – MRP** của đề. Phía khách hàng (tài khoản khách, giỏ hàng, đơn hàng, khuyến mãi, voucher) đã tách sang đồ án kia, **không có trong DB và không làm ở đây**.
- Actor chỉ có 3 role: **STAFF**, **WAREHOUSE_MANAGER (WM)**, **ADMIN**. Không có đăng ký công khai; tài khoản do ADMIN tạo.
- Schema: `database/mrp.sql` (20 bảng) là nguồn sự thật. Kèm `mrp_database_test_proper.sql` (31 test, đã chạy 31/31 PASS trên PostgreSQL 16 ở môi trường kiểm thử) và `mrp_erd.sql` để vẽ ERD.
- Không có đơn hàng nên **nhu cầu thành phẩm do WM quyết định** (đúng mục 5.1.6: "đề nghị số lượng thành phẩm cần và gửi cho nhân viên"), và phiếu xuất thành phẩm "để bán" của nhân viên **không gắn với đơn**.
- Ký hiệu: **[DB]** = DB đã chặn, service vẫn pre-check để trả lỗi dễ hiểu; **[SVC]** = DB không chặn, chỉ service chặn, bắt buộc có test.

## 1. Audit nhanh repo và schema

| # | Phát hiện | Hệ quả |
|---|---|---|
| A1 | Backend mới có `Application.java`, `application.yaml` chỉ có `spring.application.name`. Chưa có datasource. | Phải làm P0 trước. |
| A2 | `pom.xml` thiếu `security`, `oauth2-resource-server`, `springdoc`, `testcontainers`, `spring-security-test`. `GEMINI.md` từng cấm thêm dependency khi chưa được yêu cầu. | Duyệt rõ danh sách dependency (D9). |
| A3 | Hai loại id: `stock_movements.created_by` trỏ `users.id`; còn `requested_by`, `reviewed_by`, `assigned_to`, `produced_by`, `warehouse_manager_id` trỏ id bảng profile (`staff.id`, `warehouse_managers.id`). | Token có cả `uid` (users.id) và `pid` (id profile). |
| A4 | DB chỉ khóa chứng từ ở trạng thái cuối (APPROVED / REJECTED / CANCELLED / COMPLETED). Chuyển trạng thái giữa chừng không được kiểm: `production_requests` đi thẳng PENDING→COMPLETED được dù chưa sản xuất. | Service phải giữ state machine; test từng bước nhảy sai. |
| A5 | Phiếu xuất thành phẩm **không còn giới hạn theo đơn**. DB chỉ chặn lúc duyệt (CHECK tồn ≥ 0); nhiều phiếu PENDING có thể cùng xin một số tồn. | Service cảnh báo khi xin vượt tồn khả dụng (D5); chặn thật ở bước duyệt. |
| A6 | Không có ràng buộc DB cho `purchase_receipts.total_amount` = tổng dòng. | Service tính; kiểm bằng invariant (mục 6). |
| A7 | `users.role` không còn default (bắt buộc điền) và chỉ nhận STAFF / WAREHOUSE_MANAGER / ADMIN. Không còn đường tự đăng ký, nên cần cách tạo ADMIN đầu tiên. | Bootstrap admin ở P1 (D10). |
| A8 | Đổi role user bị khóa bởi FK `(user_id, role)` sang bảng profile; xóa user bị khóa bởi FK từ phiếu, production, sổ kho. | Quy tắc xóa / đổi role rõ ràng (D7). |
| A9 | Không có cột lý do từ chối ở các phiếu duyệt (chỉ có `note`). Schema không sửa. | D6. |
| A10 | Phiếu xuất NVL đã duyệt mà hủy production_request: không có đường trả NVL, chỉ ADJUST_IN thủ công. | D8. |
| A11 | ADMIN không ghi được `stock_movements` (trigger chỉ cho WM; STAFF riêng cho nhập thành phẩm từ sản xuất). | Đúng ý đồ. API điều chỉnh kho chỉ cho WM, test 403 cho ADMIN. |
| A12 | `products.selling_price` vẫn còn nhưng không có luồng bán hàng. | Chỉ là thuộc tính sản phẩm cho Admin quản lý, MRP không dùng. |
| A13 | Repo: README rỗng, `artifactId` là `demo`, chưa có docker-compose, chưa có dữ liệu demo. | Xếp vào P9. |

## 2. Quyết định cần chốt (kèm mặc định để agent làm tiếp được)

| # | Vấn đề | Mặc định đề xuất |
|---|---|---|
| D1 | Khóa tài khoản / đổi role có hiệu lực ngay? | JWT converter đọc lại `role`, `is_active` từ DB mỗi request (tra theo PK). Khóa là có hiệu lực ngay. |
| D2 | Công thức MRP (xem `GET /api/mrp/plan`, P6) | WM nhập `productId` và `quantity` = số lượng thành phẩm muốn **có sẵn để bán**. `fgOnHand` = tồn thành phẩm; `fgReserved` = tổng số lượng ở các phiếu xuất thành phẩm PENDING của sản phẩm; `fgIncoming` = Σ (số lượng yêu cầu − đã hoàn thành) của production_request PENDING/IN_PROGRESS của sản phẩm. `available = fgOnHand − fgReserved + fgIncoming`. `toProduce = max(0, quantity − available)`. Với mỗi NVL trong BOM active: `gross = BOM × toProduce`; `committed` = Σ trên mọi production_request đang mở (mọi sản phẩm) của `max(0, BOM × số lượng yêu cầu − đã xuất APPROVED)`; `usable = max(0, tồn − committed)`; `shortage = max(0, gross − usable)`; đề nghị nhập thêm = shortage. |
| D3 | Nhân viên tạo phiếu xuất NVL khi kho đang thiếu? | Cho tạo (trong hạn mức BOM), response kèm cảnh báo thiếu. Việc chặn nằm ở bước duyệt. |
| D4 | Trạng thái production_request | PENDING → IN_PROGRESS khi bắt đầu production đầu tiên; → COMPLETED tự động khi tổng production COMPLETED = số lượng yêu cầu. |
| D5 | Nhân viên tạo phiếu xuất thành phẩm khi chưa đủ tồn? | Cho tạo, response kèm cảnh báo nếu tổng đang xin (kể cả các phiếu PENDING khác) vượt tồn. Chặn nằm ở bước duyệt (CHECK tồn ≥ 0). `note` ghi người mua / lý do bán. |
| D6 | Lý do từ chối phiếu | Bắt buộc nhập, ghi nối vào `note` dạng `[TỪ CHỐI] ...`. Không sửa schema. |
| D7 | Xóa / đổi role account | Khuyến khích khóa. Xóa cứng chỉ khi chưa được tham chiếu, còn lại 409. Đổi role (giữa STAFF / WM / ADMIN): xóa profile cũ, đổi `users.role`, tạo profile mới; nếu profile cũ đã bị tham chiếu thì 409. Admin không tự khóa / xóa / hạ quyền mình; luôn còn ít nhất 1 admin active. |
| D8 | Hủy production_request | Chỉ khi chưa có phiếu xuất NVL APPROVED và chưa có production không-CANCELLED. Ngược lại 409. |
| D9 | Dependency thêm | `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, `springdoc-openapi-starter-webmvc-ui`, `spring-security-test`, `spring-boot-testcontainers`, `testcontainers` (postgresql, junit-jupiter). Cập nhật `GEMINI.md` thành "ngoài danh sách đã duyệt". |
| D10 | Dữ liệu demo và admin đầu tiên | Admin đầu tiên: `ApplicationRunner` tạo khi bảng `users` trống, username / mật khẩu lấy từ biến môi trường (`BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_PASSWORD`). Dữ liệu demo nạp qua `ApplicationRunner` ở profile `demo`, gọi service (không SQL thô). Tồn đầu kỳ nhập bằng ADJUST_IN có note. |
| D11 | Quy ước mã lỗi (toàn dự án) | 400 dữ liệu đầu vào sai; 401 chưa đăng nhập / token sai hoặc hết hạn; 403 sai role; 404 không tồn tại, không sở hữu hoặc không được giao (không lộ tồn tại); 409 sai trạng thái, vi phạm rule nghiệp vụ, lỗi từ trigger / CHECK / unique; 500 chỉ cho lỗi không lường trước. Service pre-check để lỗi trigger không lọt ra thành 500. |
| D12 | Khóa đồng thời | Mọi thao tác đổi trạng thái chứng từ (approve / reject / cancel / complete) nạp chứng từ bằng `@Lock(PESSIMISTIC_WRITE)` (method `findByIdForUpdate`) trong transaction; DB vẫn là chốt chặn cuối. Không dùng `@Version` cho `inventories`. |

## 3. Quyền hạn (ma trận tóm tắt)

| Nhóm API | STAFF | WAREHOUSE_MANAGER | ADMIN | Anonymous |
|---|---|---|---|---|
| login | ok | ok | ok | ok |
| đọc sản phẩm, danh mục, NCC, NVL | ok | ok | ok | – |
| ghi sản phẩm, danh mục, NCC, NVL | – | – | ok | – |
| danh sách nhân viên active (để giao việc) | – | ok | ok | – |
| quản lý tài khoản | – | – | ok | – |
| phiếu nhập kho, BOM, điều chỉnh kho, tạo production_request, duyệt phiếu xuất NVL / thành phẩm, xem plan MRP | – | ok | – | – |
| xem requirements của production_request | ok (đúng người được giao) | ok | – | – |
| tạo phiếu xuất NVL, production, phiếu xuất thành phẩm | ok (NVL / production: đúng người được giao) | – | – | – |
| xem tồn kho, sổ kho | ok | ok | ok | – |
| báo cáo kho | – | ok | ok | – |

Quy ước mã lỗi theo D11: chưa đăng nhập 401; sai role 403; chứng từ của người khác hoặc production_request không được giao 404 để không lộ tồn tại; sai trạng thái 409.

## 4. Các phase

Mỗi phase là một task giao cho agent. Phase xong khi: `./mvnw -q test` xanh, test E2E của phase xanh, `GET /v3/api-docs` có endpoint mới, `.gemini/MEMORY.md` được cập nhật.

Phân loại test: test không gắn nhãn là **bắt buộc**. Test gắn *(tùy chọn)* chỉ làm khi còn thời gian. Hai test đồng thời bắt buộc: MRP-09 và FG-06.

### P0 – Nền tảng (chặn mọi phase sau)

Việc cần làm:
- Thêm dependency (D9). `application.yaml`: datasource đọc biến môi trường, `ddl-auto: validate`, `open-in-view: false`.
- `docker-compose.yml`: PostgreSQL 17, mount `database/mrp.sql` vào `/docker-entrypoint-initdb.d/`. Sửa schema thì chạy `docker compose down -v` để nạp lại.
- Hạ tầng test: Testcontainers (container dùng chung) mount `mrp.sql` vào initdb (không dùng `withInitScript`), helper tạo user theo role + lấy token, helper dọn dữ liệu giữa các test bằng `TRUNCATE users, categories, suppliers, materials RESTART IDENTITY CASCADE` (đã thử: cascade dọn sạch mọi bảng; TRUNCATE không kích hoạt trigger mức dòng nên không vướng trigger append-only).
- `GlobalExceptionHandler` trả `ProblemDetail`; map SQLState: 23505, 23503, 23514, P0001 → 409. Message từ `RAISE EXCEPTION` là tiếng Việt không dấu, dùng lại cho client được nhưng không để lộ tên bảng nội bộ nếu có thể.
- Security: JWT HS256, claim `sub`, `uid`, `pid`, `role`; `@EnableMethodSecurity`; Swagger khai báo Bearer.
- CORS cho frontend React (dev `http://localhost:5173`, cấu hình qua biến môi trường).
- Chốt D11 (mã lỗi) và D12 (`@Lock(PESSIMISTIC_WRITE)`) thành code dùng chung (`GlobalExceptionHandler`, repository `findByIdForUpdate`).

Test:
- P0-01 Chạy `mrp_database_test_proper.sql` trên container, toàn bộ 31 test PASS (cổng trước khi code).
- P0-02 Context load với `ddl-auto: validate` (entity khớp schema).
- P0-03 Không token → 401; token sai / hết hạn → 401.
- P0-04 Validation sai → 400 dạng ProblemDetail, có chỉ ra field.
- P0-05 Vi phạm 23505 / 23514 / P0001 → 409; lỗi không lường trước → 500, không lộ stack trace.
- P0-06 `/swagger-ui` và `/v3/api-docs` truy cập được không cần token.

### P1 – Đăng nhập & Quản trị tài khoản (Admin, 20đ phần II)

Endpoint: `POST /api/auth/login`, `GET /api/auth/me`; Admin: `GET/POST /api/admin/users`, `GET /api/admin/users/{id}`, `POST /api/admin/users/{id}/role`, `/lock`, `/unlock`, `DELETE /api/admin/users/{id}`.

Business logic:
- Không có đăng ký công khai. Admin đầu tiên do bootstrap (D10).
- Admin tạo tài khoản cho role STAFF / WAREHOUSE_MANAGER / ADMIN (role khác → 400): tạo `users` và đúng bảng profile của role đó, atomic. Role do admin chọn, không nhận từ nơi nào khác.
- Username unique (409). Mật khẩu lưu BCrypt, tối thiểu 8 ký tự.
- Login sai username, sai mật khẩu hoặc tài khoản bị khóa đều trả cùng một thông báo chung.
- Lock / unlock đổi `is_active`, có hiệu lực ngay (D1).
- Danh sách user: lọc role / active, tìm theo username / họ tên / email, sắp xếp whitelist, phân trang.
- Xóa và đổi role theo D7.

Test:
- BOOT-01 DB trống: khởi động tạo admin từ biến môi trường, login được. DB đã có user: không tạo thêm / không đổi mật khẩu.
- AUTH-01 Login thành công → `/me` đúng role, `pid` khớp bảng profile.
- AUTH-02 Thiếu field / mật khẩu ngắn / email sai định dạng khi tạo user → 400. Username trùng → 409.
- AUTH-03 Login sai pass, sai user, user bị khóa: cùng một thông báo.
- ACC-01 Admin tạo STAFF / WM / ADMIN: có đúng một dòng profile tương ứng, login được. Tạo với role lạ → 400.
- ACC-02 STAFF / WM gọi `/api/admin/**` → 403.
- ACC-03 Khóa user: token đang dùng bị từ chối ngay (401 / 403), login mới thất bại; mở khóa dùng lại được.
- ACC-04 Admin tự khóa / xóa / hạ quyền chính mình → 409. Khóa admin cuối cùng đang active → 409.
- ACC-05 Xóa user chưa phát sinh dữ liệu → 204, mất cả profile. Xóa user đã có phiếu / production / sổ kho → 409 kèm gợi ý khóa.
- ACC-06 Đổi role STAFF → WM khi chưa có dữ liệu: profile cũ mất, profile mới có, token cũ không còn quyền cũ. Khi đã có phiếu → 409, dữ liệu không đổi.
- ACC-07 Tìm / lọc / sắp xếp danh sách user; sort theo field ngoài whitelist → 400.

### P2 – Danh mục, sản phẩm, nhà cung cấp, nguyên vật liệu (Admin xem / tìm / lọc / sắp xếp)

Endpoint: `/api/categories`, `/api/products`, `/api/suppliers`, `/api/materials` (GET list / detail, POST, PUT, DELETE). Chỉ `/api/products` và `/api/materials` có `POST /{id}/deactivate` và `/activate` (chỉ hai bảng này có cột `is_active`). Thêm `GET /api/staff` (WM, ADMIN): nhân viên active, trả id profile + họ tên, để WM chọn người được giao.

Business logic:
- Đọc: STAFF / WM / ADMIN. Ghi: ADMIN.
- Sản phẩm: danh sách mặc định chỉ sản phẩm active; ADMIN lọc thêm theo `active`. Lọc: từ khóa, danh mục, khoảng giá. Sắp xếp: whitelist (tên, giá, ngày tạo). Phân trang có giới hạn kích thước trang.
- Giá bán ≥ 0 [DB]. Tên danh mục unique [DB]. Mã NVL unique [DB].
- Xóa cứng chỉ khi chưa được tham chiếu, còn lại 409. Sản phẩm và NVL: thông báo 409 gợi ý vô hiệu hóa. Danh mục và NCC không có `is_active` nên không có vô hiệu hóa.
- NVL / sản phẩm bị vô hiệu: không thêm được vào BOM mới, phiếu nhập mới, phiếu xuất thành phẩm mới; dữ liệu cũ giữ nguyên.
- Đơn vị tính của NVL không đổi được sau khi đã có movement hoặc BOM [SVC].
- Tìm kiếm nâng cao nhà cung cấp: tên / SĐT / email, sắp xếp whitelist.

Test:
- CAT-01 ADMIN CRUD; STAFF / WM ghi → 403; chưa đăng nhập → 401.
- CAT-02 Lọc kết hợp (từ khóa + danh mục + khoảng giá) cho kết quả đúng; sort + phân trang đúng; sort field lạ → 400; page size quá lớn bị chặn.
- CAT-03 Danh sách mặc định không có sản phẩm inactive; ADMIN lọc `active` thấy đúng.
- CAT-04 Giá âm → 400. Tên danh mục / mã NVL trùng → 409.
- CAT-05 Xóa danh mục còn sản phẩm → 409. Xóa NCC / NVL / sản phẩm đã được dùng → 409; chưa dùng → 204.
- CAT-06 Đổi đơn vị NVL đã có movement → 409.
- CAT-07 Vô hiệu NVL rồi đưa vào BOM / phiếu nhập → 409.
- CAT-08 `GET /api/staff`: WM / ADMIN xem được, chỉ có nhân viên active; STAFF → 403.

### P3 – Tồn kho, sổ kho, điều chỉnh

Endpoint: `GET /api/inventories/materials`, `GET /api/inventories/products`, `GET /api/stock-movements`, `POST /api/stock-adjustments`.

Business logic:
- `inventories` chỉ đọc (`@Immutable`). Mọi thay đổi tồn đi qua INSERT `stock_movements`. `stock_movements` entity cũng `@Immutable`, không update / delete.
- Điều chỉnh (WM): đúng một trong `materialId` / `productId`; hướng IN / OUT; số lượng > 0; `note` bắt buộc [DB]; số lượng thành phẩm phải nguyên [DB]; OUT quá tồn → 409 [DB, 23514] và không để lại movement.
- Dòng tồn chưa tồn tại được tạo bởi trigger, API coi là 0.
- Sau khi insert movement, đọc tồn bằng query mới (persistence context không tự biết trigger đã đổi inventories).

Test:
- INV-01 ADJUST_IN có note → tồn tăng đúng, movement ghi `created_by` = `users.id` của WM.
- INV-02 Thiếu note / note trắng → 400 (hoặc 409 từ DB), tồn không đổi.
- INV-03 ADJUST_OUT vượt tồn → 409, tồn và ledger không đổi.
- INV-04 Thành phẩm số lẻ → 400. NVL số lẻ 3 chữ số thập phân → ok.
- INV-05 ADMIN / STAFF điều chỉnh → 403.
- INV-06 Không có endpoint nào sửa / xóa movement hay ghi trực tiếp inventories (thử PUT / DELETE → 404 / 405).
- INV-07 Lọc sổ kho theo loại, vật tư, khoảng ngày, chứng từ nguồn.

### P4 – Phiếu nhập kho NVL (5.1.1)

Endpoint: `POST /api/purchase-receipts`, `GET` list / detail, `PUT /{id}` (thay dòng khi PENDING), `POST /{id}/complete`, `POST /{id}/cancel`.

Business logic:
- Tạo PENDING: NCC tồn tại, ≥ 1 dòng, NVL active và không trùng trong cùng phiếu (PK kép), số lượng > 0, đơn giá ≥ 0.
- Server tính `amount = round(quantity × unit_price, 2)` [DB check] và `total_amount = Σ amount` [SVC]. Client không gửi amount, total, status.
- `complete` (WM bất kỳ): chuyển COMPLETED **trước**, rồi INSERT movement IN cho từng dòng (số lượng khớp dòng phiếu, `created_by` = WM). Tất cả trong một transaction; lỗi ở đâu thì phiếu vẫn PENDING và không có movement.
- `cancel`: chỉ từ PENDING, không ảnh hưởng tồn.
- Sau COMPLETED / CANCELLED: không sửa phiếu và dòng [DB]; sửa → 409.

Test:
- PUR-01 Tạo → complete: tồn từng NVL tăng đúng, mỗi dòng đúng 1 movement IN, total = tổng dòng.
- PUR-02 Làm tròn: số lượng 2,5 × giá 3 333,33 → amount đúng quy tắc round 2 số lẻ.
- PUR-03 Phiếu rỗng, NVL trùng, số lượng ≤ 0, NVL inactive, NCC không tồn tại → 400 / 404 / 409 đúng loại.
- PUR-04 Complete lần hai → 409, tồn không tăng thêm.
- PUR-05 *(tùy chọn)* Hai request complete đồng thời cùng phiếu → đúng một thành công, tồn tăng một lần.
- PUR-06 Cancel PENDING ok, tồn không đổi; cancel / sửa phiếu COMPLETED → 409.
- PUR-07 Sửa dòng khi PENDING ok, total cập nhật; sau complete → 409.
- PUR-08 STAFF / ADMIN tạo hoặc complete → 403.

### P5 – Công thức sản phẩm BOM (5.1.5)

Endpoint: `POST /api/boms`, `GET /api/boms?productId=&active=`, `GET /api/boms/{id}`, `POST /{id}/activate`, `POST /{id}/deactivate`.

Business logic:
- Ví dụ đề bài: 1 cái bàn = 1 mặt bàn + 4 chân bàn (hai NVL, số lượng 1 và 4).
- Tạo BOM: sản phẩm active; ≥ 1 dòng; NVL active, không trùng; số lượng > 0. `version = max + 1` theo sản phẩm.
- Chỉ một BOM active mỗi sản phẩm (partial unique index, không deferrable): service **vô hiệu BOM cũ trước rồi mới kích hoạt BOM mới** trong cùng transaction.
- Không có API sửa dòng BOM. BOM đã có production_request thì DB chặn sửa dòng [DB]; muốn đổi thì tạo version mới. Production_request cũ vẫn trỏ BOM cũ.
- Vô hiệu hóa BOM làm sản phẩm không còn BOM active → không tạo được production_request mới.

Test:
- BOM-01 Tạo BOM bàn (1 mặt + 4 chân) → v1 active, dòng đúng.
- BOM-02 Tạo BOM thứ hai cho cùng sản phẩm → v2 active, v1 inactive, chỉ còn một active.
- BOM-03 BOM rỗng, NVL trùng, NVL inactive, số lượng ≤ 0, sản phẩm không tồn tại → lỗi đúng loại.
- BOM-04 Production_request cũ vẫn trỏ v1 sau khi có v2.
- BOM-05 *(tùy chọn)* Hai request tạo BOM cho cùng sản phẩm đồng thời → không có hai version trùng, không có hai BOM active; request thua nhận 409 rõ ràng (hoặc retry).
- BOM-06 Activate BOM cũ → BOM đang active tự chuyển inactive.
- BOM-07 Chỉ WM tạo / đổi trạng thái; STAFF / ADMIN đọc được.

### P6 – Lõi MRP: kế hoạch, production_request, yêu cầu NVL, sản xuất (5.1.6, 5.2.1–5.2.3)

Endpoint:
- `GET /api/mrp/plan?productId=&quantity=` (WM): kế hoạch theo D2.
- `POST /api/production-requests`, `GET` list / detail (kèm tiến độ), `POST /{id}/cancel`, `GET /{id}/requirements`.
- `POST /api/material-issue-requests`, `GET`, `POST /{id}/approve`, `/reject`, `/cancel`.
- `POST /api/productions`, `GET`, `POST /{id}/complete`, `POST /{id}/cancel`.

Business logic – kế hoạch MRP (WM):
- Tính theo D2 từ dữ liệu có sẵn (tồn, phiếu xuất thành phẩm PENDING, production_request mở, phiếu xuất NVL đã duyệt). Chỉ đọc, không ghi gì. Sản phẩm không có BOM active → 409. Response trả từng bước (`fgOnHand`, `fgReserved`, `fgIncoming`, `available`, `toProduce`) và từng NVL (`gross`, `onHand`, `committed`, `usable`, `shortage`).
- Logic tính nằm trong một lớp thuần (không Spring, không DB) để unit test bằng số.

Business logic – production_request (WM tạo):
- Client gửi `productId`, `quantity` (nguyên > 0), `assignedStaffId`, `note`. **BOM do server lấy** (BOM active của sản phẩm); không có BOM active hoặc BOM rỗng → 409 [DB backstop].
- Nhân viên được giao phải tồn tại và đang active (WM lấy danh sách từ `GET /api/staff`). `assigned_to` bắt buộc có khi rời PENDING [DB]. Trigger kiểm tra người tạo phiếu xuất NVL / production phải đúng người được giao.
- STAFF chỉ thấy production_request của mình; WM thấy tất cả. Trạng thái theo D4; hủy theo D8.
- `requirements` (5.2.1): với mỗi NVL trả: nhu cầu (BOM × số lượng), đã xuất (APPROVED), đang chờ duyệt (PENDING), còn được phép yêu cầu, tồn kho, `committed` của các production_request khác, thiếu bao nhiêu = `max(0, còn phải xuất − max(0, tồn − committedKhác))`.

Business logic – phiếu xuất NVL (STAFF tạo, WM duyệt):
- Người tạo phải là người được giao [DB, P0001]; production_request phải PENDING / IN_PROGRESS [DB].
- Mỗi NVL phải thuộc BOM của production_request và **tổng PENDING + APPROVED không vượt BOM × số lượng** [DB]. Từ chối / hủy phiếu thì hạn mức được trả lại.
- Tạo phiếu: header trước (kích hoạt kiểm tra người tạo / trạng thái), sau đó dòng (kích hoạt kiểm tra hạn mức). Cho tạo khi kho thiếu, kèm cảnh báo (D3).
- Approve (WM): đổi APPROVED, ghi `reviewed_by` (profile WM), `reviewed_at`, **rồi** INSERT movement OUT từng dòng (số lượng khớp từng dòng, `created_by` = WM). Một dòng thiếu tồn → cả transaction rollback, phiếu vẫn PENDING, không có movement nào, trả 409 nêu NVL thiếu.
- Hai WM duyệt đồng thời cùng phiếu: khóa dòng (D12), chỉ một thành công, người còn lại 409.
- Reject (WM, D6): REJECTED có `reviewed_by` / `reviewed_at`. Cancel (người tạo, chỉ PENDING): CANCELLED, các trường review phải null [DB].
- Dòng phiếu đóng băng sau khi rời PENDING [DB].

Business logic – production (STAFF):
- Bắt đầu: người được giao; production_request PENDING / IN_PROGRESS; **tổng số lượng các production không-CANCELLED ≤ số lượng yêu cầu** [DB]. Production_request chuyển IN_PROGRESS (D4). Có thể sản xuất nhiều đợt.
- Complete (5.2.3): với từng NVL, tổng OUT đã duyệt ≥ BOM × (đã hoàn thành + đợt này) [DB P0001, service pre-check]. Cập nhật COMPLETED + `completed_at`, INSERT movement IN thành phẩm (`created_by` = STAFF user, số lượng = số lượng đợt). Nếu tổng COMPLETED = số lượng yêu cầu → production_request COMPLETED.
- NVL đã trừ kho từ lúc duyệt phiếu xuất, nên complete **không** trừ NVL thêm lần nữa.
- Cancel production: chỉ khi IN_PROGRESS, hạn mức được trả lại.

Test:
- MRP-01 Plan, ví dụ bàn (BOM 1 mặt + 4 chân): muốn có sẵn 10 bàn; tồn thành phẩm 2, phiếu xuất thành phẩm PENDING 1, production_request mở còn 3 → `available` = 2 − 1 + 3 = 4, `toProduce` = 6; NVL cần: mặt 6, chân 24. Tồn mặt 5, chân 30, không có cam kết → thiếu mặt 1, chân 0. Muốn có sẵn ≤ available → `toProduce` 0, mọi NVL thiếu 0.
- MRP-01b Plan có cam kết: một production_request khác đang mở còn phải xuất NVL → `committed` làm giảm `usable`, thiếu tăng đúng. Sản phẩm không BOM active → 409. STAFF gọi plan → 403.
- MRP-02 Tạo production_request: sản phẩm không BOM active → 409; BOM rỗng → 409; staff không tồn tại / bị khóa → 4xx; số lượng 0 hoặc lẻ → 400; STAFF tạo → 403.
- MRP-03 `requirements` trả đúng số liệu trước và sau khi có phiếu PENDING / APPROVED.
- MRP-04 STAFF A xem / thao tác production_request giao cho STAFF B → 404.
- MRP-05 Tạo phiếu xuất NVL hợp lệ → PENDING; NVL ngoài BOM → 409; vượt hạn mức → 409; đúng hạn mức rồi tạo thêm → 409; reject phiếu đầu rồi tạo lại → ok (hạn mức trả lại).
- MRP-06 Approve đủ tồn: trạng thái APPROVED, reviewer đúng, mỗi dòng đúng 1 movement OUT, tồn NVL giảm đúng.
- MRP-07 Approve thiếu tồn một dòng: 409, phiếu còn PENDING, **không** movement nào, tồn mọi NVL không đổi. Nhập thêm hàng rồi approve lại → ok.
- MRP-08 Approve / reject / cancel lần hai trên phiếu đã chốt → 409. Approve phiếu CANCELLED → 409.
- MRP-09 *(bắt buộc)* Hai WM approve đồng thời → đúng một thắng, tồn trừ một lần.
- MRP-10 *(tùy chọn)* Cùng một STAFF gửi hai request đồng thời (double-submit) làm tổng vượt hạn mức → đúng một vượt qua được, tổng không vượt BOM × số lượng. (Chỉ nhân viên được giao mới tạo được phiếu, nên không có tranh chấp giữa hai nhân viên.)
- MRP-11 Production: tạo vượt số lượng yêu cầu → 409; complete khi NVL xuất chưa đủ → 409; đủ thì tồn thành phẩm tăng đúng, tồn NVL không đổi.
- MRP-12 Sản xuất 2 đợt (4 rồi 6 cho yêu cầu 10): sau đợt cuối production_request COMPLETED; đợt 11 → 409; sau COMPLETED không tạo thêm phiếu / production → 409.
- MRP-13 Hủy production_request theo D8 (cả hai nhánh ok / 409).
- MRP-14 Người không được giao tạo phiếu xuất NVL / production → 404 (D11), không phải 500.
- MRP-15 Dòng phiếu xuất NVL sau khi APPROVED / REJECTED không sửa được; không có endpoint sửa.

### P7 – Phiếu xuất thành phẩm để bán (5.1.3, 5.2.4)

Endpoint: `POST /api/fg-issue-requests`, `GET` list / detail, `POST /{id}/approve`, `/reject`, `/cancel`.

Business logic:
- STAFF tạo phiếu (không cần đơn hàng, không cần là người được giao production nào): body gồm `items` (`productId`, `quantity` nguyên > 0, không trùng sản phẩm trong cùng phiếu [DB unique]) và `note` (người mua / lý do bán). Sản phẩm phải active. Tạo header trước, rồi dòng.
- Cảnh báo (D5): nếu số lượng xin + tổng các phiếu PENDING khác của sản phẩm vượt tồn thì vẫn tạo, response kèm cảnh báo. Chặn thật ở bước duyệt.
- Approve (WM): APPROVED + reviewer, **rồi** INSERT movement OUT từng sản phẩm (số lượng khớp dòng, `created_by` = WM). Thiếu tồn thành phẩm → rollback toàn bộ, phiếu vẫn PENDING, 409.
- Reject (D6): REJECTED. Cancel (người tạo, PENDING).
- STAFF chỉ thấy phiếu của mình; WM thấy tất cả. Hai approve đồng thời → một thắng (D12).

Test:
- FG-01 Tạo phiếu hợp lệ → PENDING, tồn thành phẩm không đổi, không có movement.
- FG-02 Phiếu rỗng, sản phẩm trùng trong phiếu, số lượng ≤ 0 hoặc lẻ, sản phẩm inactive hoặc không tồn tại → lỗi đúng loại.
- FG-03 Xin vượt tồn: tạo được, response có cảnh báo; approve → 409, phiếu PENDING, tồn không đổi; sản xuất bù xong approve lại ok.
- FG-04 Approve đủ tồn: mỗi sản phẩm 1 movement OUT, tồn thành phẩm giảm đúng, `reviewed_by` / `reviewed_at` đúng.
- FG-05 Approve / reject / cancel lần hai → 409. Dòng phiếu sau khi APPROVED / REJECTED không sửa được.
- FG-06 *(bắt buộc)* Hai WM approve đồng thời cùng phiếu → đúng một thắng, tồn trừ một lần.
- FG-07 *(tùy chọn)* Hai phiếu PENDING cùng xin hết số tồn cuối, duyệt đồng thời → đúng một thành công, phiếu kia 409, tồn không âm.
- FG-08 STAFF duyệt phiếu → 403; WM / ADMIN tạo phiếu → 403; STAFF A xem phiếu của STAFF B → 404.

### P8 – Báo cáo kho (5.1.4)

Endpoint: `GET /api/reports/stock?kind=MATERIAL|PRODUCT&granularity=MONTH|QUARTER|YEAR&year=&period=` (WM, ADMIN).

Business logic:
- Mỗi vật tư / sản phẩm một dòng: tồn đầu kỳ, nhập (IN), xuất (OUT), điều chỉnh tăng / giảm, tồn cuối kỳ. Tồn đầu kỳ = tổng có dấu các movement trước kỳ; tồn cuối kỳ = đầu kỳ + biến động trong kỳ. Biên kỳ: `>= đầu kỳ` và `< đầu kỳ sau`.
- Item không phát sinh trong kỳ nhưng có tồn đầu vẫn xuất hiện. Kỳ chưa có dữ liệu trả danh sách rỗng hoặc toàn 0, không lỗi.
- Tham số sai (tháng 13, quý 5, năm âm) → 400.
- Nếu kỳ chứa hiện tại thì tồn cuối kỳ phải bằng `inventories.quantity`.

Test:
- RPT-01 Dựng dữ liệu nhiều tháng (chèn movement có thời gian cố định trong môi trường test); báo cáo tháng / quý / năm khớp tay.
- RPT-02 Movement đúng 00:00 ngày đầu kỳ thuộc kỳ đó, không thuộc kỳ trước.
- RPT-03 Tồn cuối kỳ hiện tại = tồn thực.
- RPT-04 STAFF → 403. Tham số sai → 400.
- RPT-05 Báo cáo đủ cho cả NVL và thành phẩm.

### P9 – Vận hành, dữ liệu demo, tài liệu

- Dữ liệu demo (D10): tài khoản test mỗi role, vài NCC, NVL (mặt bàn, chân bàn…), sản phẩm, BOM, tồn đầu kỳ, vài production_request và phiếu ở các trạng thái để demo luồng. Mật khẩu demo lấy từ biến môi trường hoặc tài liệu rõ ràng, không commit secret thật.
- Sao lưu / phục hồi (đề yêu cầu có phương án, 5đ): script `pg_dump -Fc` và `pg_restore`, chạy thử phục hồi vào DB mới rồi chạy các invariant ở mục 6.
- README: yêu cầu môi trường, cách chạy docker-compose, biến môi trường, cách chạy test, danh sách tài khoản demo, link Swagger.
- File nộp theo đề: `readme.txt` thông tin nhóm, source, báo cáo, script SQL (`database/mrp.sql`).

Test:
- OPS-01 Khởi động với profile `demo` trên DB trống: dữ liệu đầy đủ, các invariant ở mục 6 đều đúng.
- OPS-02 Dump → restore vào DB mới → số lượng bản ghi và tồn kho khớp, backend chạy được trên DB đã restore.
- OPS-03 Làm theo README từ đầu trên máy sạch (docker-compose up, chạy app, login Swagger) thành công.

## 5. Kịch bản E2E xuyên module

Mỗi kịch bản chạy qua API thật với Testcontainers, kết thúc bằng việc kiểm tra toàn bộ invariant ở mục 6.

- **S1 – Luồng chính (bàn ăn).** Admin (bootstrap) tạo STAFF + WM, tạo NCC, NVL (mặt bàn, chân bàn), sản phẩm → WM nhập kho (số lượng dư) → WM tạo BOM (1 mặt + 4 chân) → WM xem plan (muốn có 3 bàn, toProduce = 3), tạo production_request 3 bàn giao cho STAFF → STAFF xem requirements, tạo phiếu xuất NVL (3 mặt, 12 chân) → WM approve, tồn NVL giảm → STAFF bắt đầu và hoàn thành production 3 bàn, tồn thành phẩm = 3, production_request COMPLETED → STAFF tạo phiếu xuất thành phẩm 3 bàn → WM approve, tồn thành phẩm = 0 → báo cáo tháng khớp.
- **S2 – Thiếu NVL.** Tồn kho không đủ khi duyệt phiếu xuất NVL: 409, không movement; nhập thêm; duyệt lại thành công.
- **S3 – Hạn mức BOM.** Yêu cầu vượt BOM × số lượng bị chặn; reject trả lại hạn mức; yêu cầu lại thành công.
- **S4 – Sản xuất nhiều đợt.** 10 chiếc = 4 + 6; đợt vượt bị chặn; complete khi chưa đủ NVL bị chặn.
- **S5 – Sai người / sai quyền.** Mỗi endpoint nghiệp vụ gọi bởi role sai hoặc STAFF không được giao: 401 / 403 / 404 như đã quy ước, không bao giờ 500.
- **S6 – Xin xuất khi chưa đủ hàng.** Tồn thành phẩm 0: tạo phiếu xuất được (có cảnh báo), approve 409; sau khi sản xuất xong approve thành công.
- **S7 – Hủy.** Hủy production_request theo D8; hủy phiếu nhập; hủy phiếu xuất NVL và thành phẩm; trả hạn mức.
- **S8 – Song song.** Bắt buộc: hai WM approve cùng phiếu xuất NVL; hai WM approve cùng phiếu xuất thành phẩm. Tùy chọn: hai complete cùng phiếu nhập; cùng một staff double-submit yêu cầu NVL chạm hạn mức; hai phiếu thành phẩm tranh nhau tồn cuối; hai lần tạo BOM cùng sản phẩm. Kết quả luôn: đúng một thắng, người còn lại 409, tồn không sai, không 500.
- **S9 – Vòng đời tài khoản.** Tạo → dùng → khóa (token cũ bị từ chối) → đổi role / xóa theo D7.
- **S10 – Sao lưu và phục hồi (tùy chọn; OPS-02 vẫn bắt buộc).** Chạy S1 → dump → restore sang DB mới → backend chạy → invariant đúng.

## 6. Invariant kiểm tra sau mỗi kịch bản

Viết thành một bộ truy vấn SQL dùng chung trong test; chạy cuối mọi kịch bản.

1. Với mỗi vật tư / sản phẩm: `inventories.quantity` = tổng có dấu các `stock_movements` (IN / ADJUST_IN dương, OUT / ADJUST_OUT âm). Không có tồn âm.
2. Mỗi phiếu nhập COMPLETED có đúng một movement IN cho mỗi dòng, số lượng khớp; phiếu PENDING / CANCELLED không có movement.
3. Mỗi phiếu xuất NVL APPROVED có đúng một movement OUT cho mỗi dòng; phiếu khác không có movement.
4. Mỗi phiếu xuất thành phẩm APPROVED có đúng một movement OUT cho mỗi dòng; phiếu khác không có movement.
5. Mỗi production COMPLETED có đúng một movement IN thành phẩm; production khác không có.
6. Với mỗi production_request: tổng production không-CANCELLED ≤ số lượng yêu cầu; tổng NVL (PENDING + APPROVED) ≤ BOM × số lượng; production_request COMPLETED ⇔ tổng production COMPLETED = số lượng yêu cầu.
7. Mỗi sản phẩm có tối đa một BOM active; mỗi production_request trỏ BOM đúng sản phẩm của nó.
8. `purchase_receipts.total_amount` = Σ dòng.
9. Mọi phiếu ở trạng thái APPROVED / REJECTED có `reviewed_by` và `reviewed_at`; PENDING / CANCELLED thì null.

## 7. Đối chiếu đề bài

| Yêu cầu đề | Phần plan |
|---|---|
| II.1 CSDL đầy đủ bảng, sao lưu / phục hồi | P0 (cổng test DB), P9 |
| II.2 Admin xem / tìm / lọc / sắp xếp sản phẩm, NCC | P2 |
| II.2 Admin thêm, xóa, phân quyền account | P1 |
| III.5.1.1 Phiếu nhập kho tăng NVL | P4 |
| III.5.1.2 Duyệt phiếu xuất NVL, giảm NVL | P6 |
| III.5.1.3 Duyệt phiếu xuất thành phẩm, giảm thành phẩm | P7 |
| III.5.1.4 Thống kê tồn / xuất / lưu kho theo tháng, quý, năm | P8 |
| III.5.1.5 Công thức tạo sản phẩm (BOM) | P5 |
| III.5.1.6 Đề nghị số lượng thành phẩm cần, gửi nhân viên | P6 (`mrp/plan`, production_request) |
| III.5.2.1 Nhận yêu cầu, kiểm tra thiếu NVL | P6 (`requirements`) |
| III.5.2.2 Lập đề nghị xuất NVL | P6 |
| III.5.2.3 Tạo thành phẩm từ NVL đã có | P6 (production) |
| III.5.2.4 Lập phiếu đề nghị xuất thành phẩm để bán | P7 |
| Dữ liệu demo, tài khoản test, giao dịch mẫu | P9 |

## 8. Thứ tự làm và mức ưu tiên

Theo thang điểm: Admin (20) + MRP (30) + DB (10) là trọng tâm, nên:

1. P0 → P1 → P2 → P3 (nền, Admin, tồn kho). P2 gồm cả `GET /api/staff`.
2. P4 → P5 (nhập kho, BOM).
3. P6 → P7 (lõi MRP và xuất thành phẩm).
4. P8, P9.

Khi giao cho agent: mỗi phase một task, kèm đoạn "Business logic" và "Test" của phase đó, và nhắc đọc phần liên quan của `database/mrp.sql` trước khi viết entity.

## 9. Lịch sử sửa

**2026-10-06 – chuyển sang MRP-only (20 bảng):**
1. Bỏ phía khách hàng: role CUSTOMER, đăng ký công khai, giỏ hàng, đơn hàng, khuyến mãi, voucher. Phase cũ P6 (bán hàng) và P10 (khuyến mãi) bị xóa; các phase sau đánh số lại (P7 lõi MRP cũ → P6, P8 → P7, P9 → P8, P11 → P9).
2. Phiếu xuất thành phẩm không còn gắn đơn: tạo từ danh sách sản phẩm do nhân viên chọn, cảnh báo khi xin vượt tồn (D5), chặn ở bước duyệt.
3. Công thức MRP (D2) viết lại theo `quantity` muốn có sẵn do WM nhập, thay cho "đơn CONFIRMED".
4. Thêm bootstrap admin đầu tiên (D10, BOOT-01) vì không còn đăng ký.
5. Đánh số lại quyết định D1–D12 và test (FG-xx, bỏ ORD-xx, PRM-xx); hai test đồng thời bắt buộc là MRP-09 và FG-06.
6. Cổng test DB (P0-01) là 31 test của `mrp_database_test_proper.sql` bản mới.

**2026-10-04 – review đối chiếu `mrp.sql` (29 bảng):** `suppliers` và `categories` không có `is_active` nên bỏ deactivate cho hai bảng này; thêm `GET /api/staff`; thêm D11 (mã lỗi) và D12 (`PESSIMISTIC_WRITE`); gắn nhãn test bắt buộc / tùy chọn.

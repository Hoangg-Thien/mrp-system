# Kết quả sửa lỗi E2E MRP

Ngày kiểm thử: 28/10/2026. Nhánh: `fix/e2e-findings`.

## Phạm vi và môi trường

Đã sửa F01, F02, F03, F05, F06, F07 và cảnh báo antd. Giữ nguyên F04 ở backend: hai phiếu nhập giống nhau vẫn có thể hợp lệ.

API E2E chạy ở `127.0.0.1:18080`, UI ở `localhost:15173`, datasource `mrp_e2e`, dùng thông tin trong `.env.e2e`. Không sửa schema, không thao tác DB `mrp` hay container `mrp-postgres`, không chạy lại `seed_stock.sql`, không DROP/TRUNCATE. Test PostgreSQL mới dùng container tạm riêng của Testcontainers, nạp schema hiện có. Không commit `.env.e2e`, mật khẩu hay token.

## Thay đổi và commit

| Mục | Commit | Thay đổi |
|---|---|---|
| F02 | `54555a3` | Khóa yêu cầu sản xuất bằng `findByIdForUpdate` trước khi tạo header/dòng của phiếu NVL và sản xuất. Map `ConcurrencyFailureException` (bao gồm lớp con `PessimisticLockingFailureException`) và SQLState `40P01`/`40001` sang 409, thông báo “Hệ thống đang bận, vui lòng thử lại”. |
| F03 | `27f7c6b` | Tắt Jackson `accept-float-as-int`; giữ kiểu thập phân của NVL. |
| F05 | `b413a98` | Handler tài nguyên không tồn tại trả 404; phương thức không hỗ trợ trả 405. |
| F01 | `cdbcac5` | Hook `useAllPages` dùng size=100 và lấy tiếp các trang; áp dụng sáu trang yêu cầu và hai trang kho/sổ kho có cùng lỗi. Có thông báo lỗi và thử lại cho query danh sách lựa chọn. |
| F07 | `c75306a` | `QueryError` dùng chung; các bảng danh sách hiển thị lỗi và nút Thử lại, không đánh đồng lỗi với dữ liệu rỗng. |
| F06 | `fa6027d` | Mutation dùng single-flight để chặn cả hai lần submit cùng tick; loading/disabled cho thao tác gửi và modal, hộp xác nhận chờ promise, đăng nhập có guard. Sau lỗi có thể thử lại. |
| antd | `5a9f899` | Đổi sang `destroyOnHidden`, `Alert.title`, `Statistic.styles.content`, `Space.orientation`, `mask.closable`; dùng context của `App` cho message/modal, mount form trước khi khởi tạo giá trị. |

## Kết quả chạy thực tế

| Lệnh/bộ kiểm tra | Kết quả |
|---|---|
| `backend: .\mvnw.cmd -q '-Ddebug=false' '-Dlogging.level.root=WARN' test` | 202 tests, 0 failures, 0 errors, 0 skipped. Đây là Maven wrapper trên Windows tương đương `./mvnw -q test`; hai tham số bổ sung giảm log. |
| `npm --prefix frontend run lint` | Thành công, không cảnh báo ở lần cuối. |
| `npm --prefix frontend run build` | Thành công. |
| `node --test frontend/src/api/allPages.test.js frontend/src/api/singleFlight.test.js` | 4/4 đạt: lấy 201 lựa chọn qua 3 trang, không trả danh sách thiếu khi trang sau lỗi, chặn submit trùng và cho thử lại sau lỗi. |
| API/UI/invariant từ script QA | 583 ID kiểm tra riêng biệt: 581 đạt tự động, 2 khác kỳ vọng cũ được giải thích bên dưới. Giữ lần chạy cuối cho mỗi ID, không cộng các lần chạy lại thành test mới. |
| Luồng UI qua form thật | 16/16 bước đạt, 3 role, 0 lỗi/cảnh báo console trong lần cuối. |
| Invariant sau cùng | 11/11 truy vấn đạt, mọi số vi phạm bằng 0. |

Test hồi quy mới ở `backend/src/test/java/mrp/E2eFindingsIntegrationTest.java`, cùng thư mục test tích hợp của dự án. Repo trước đó có test tích hợp H2; lớp mới chạy PostgreSQL thật bằng Testcontainers. Docker ban đầu chưa sẵn sàng nhưng đã chạy được ở lượt xác minh cuối; không có test Docker bị skip.

Năm test của lớp mới xác nhận: hai request đồng thời tạo phiếu NVL trả 200+409, hai request đồng thời tạo sản xuất trả 200+409, không nhân đôi dữ liệu; số lẻ ở phiếu thành phẩm/yêu cầu sản xuất/sản xuất trả 400; NVL 0.125 được lưu đúng; hai URL PUT được yêu cầu trả 404 và PUT trên endpoint chỉ hỗ trợ GET trả 405. Không gặp lại deadlock trong các lần chạy này.

### Hai khác biệt với oracle QA cũ

1. `C-double-submit-receipt`: hai POST trực tiếp trả 200+200. Oracle cũ đòi 200+409, trái với quyết định giữ F04 của yêu cầu hiện tại. Không thêm ràng buộc chống phiếu giống nhau vào backend.
2. `B-old-role-no-staff-rights`: token cũ sau đổi role trả **401**, oracle cũ chỉ chấp nhận **403**. Request bị chặn, không được cấp quyền STAFF và không trả 500. Giữ nguyên kết quả thô trong bằng chứng, không đổi thành một test 403 đạt.

Các lần thử ban đầu còn cảnh báo antd và lỗi selector script; đã sửa rồi chạy lại. Một ca mock lỗi sau mock dữ liệu rỗng bị ảnh hưởng bởi route của script; chạy trong context mới xác nhận lỗi hiển thị đúng. Những lần này không được trình bày là kết quả cuối đạt ngay từ lần đầu.

## Luồng UI thật và số liệu

Fixture cuối: `UIFIX1793193357652`. Tất cả thao tác ghi của luồng này đi qua form/nút UI; SQL chỉ đọc để đối chiếu.

| Bước | Đối chiếu |
|---|---|
| ADMIN tạo danh mục, NCC, NVL và sản phẩm | 4 form thành công. |
| WM tạo và hoàn tất phiếu nhập | Nhập 10 NVL, đơn giá 5, tổng 50; tồn NVL = 10. |
| WM tạo BOM | 2 NVL cho một thành phẩm. |
| WM tính MRP mục tiêu 3 | Cần sản xuất 3, nhu cầu NVL 6, kho đủ. |
| WM giao yêu cầu sản xuất cho STAFF A | Số lượng 3. |
| STAFF lập phiếu NVL, WM duyệt | Xuất 6; tồn NVL còn 4. |
| STAFF tạo và hoàn tất sản xuất | Thành phẩm tăng lên 3. |
| STAFF lập phiếu thành phẩm, WM duyệt | Xuất 3; tồn thành phẩm còn 0. |
| WM xem báo cáo | Dòng NVL đầu kỳ 0, nhập 10, xuất 6, cuối kỳ 4. |

API QA cũng đã chạy đối chiếu báo cáo tháng/quý/năm. UI kiểm tra báo cáo hiện tại; chưa thao tác từng bộ lọc tháng/quý/năm bằng trình duyệt.

Đã mở 23 trang của ba role và kiểm tra chuyển sai khu vực; lượt cuối đều đạt và console sạch. Kiểm tra network trên 11 trang WM/STAFF đạt. Đăng nhập bấm đúp chỉ phát một request. Trên form danh mục, trì hoãn POST và click hai lần cùng tick: chỉ một POST, nút loading và disabled, đóng modal sau khi hoàn tất. Giả lập 503 trên danh sách của cả ba role: hiện lỗi, không hiện “Không có dữ liệu”; bấm Thử lại phục hồi dữ liệu thành công.

## Toàn vẹn dữ liệu

Các truy vấn cuối xác nhận: tồn khớp sổ kho và không âm; phiếu nhập/xuất NVL/xuất thành phẩm/sản xuất có đúng movement; tổng sản xuất không vượt yêu cầu và trạng thái hoàn tất phù hợp; tổng phiếu NVL không vượt BOM; tối đa một BOM active và BOM thuộc đúng sản phẩm; tiền phiếu nhập khớp dòng; trường người duyệt/thời điểm duyệt phù hợp trạng thái.

## Bằng chứng và cách chạy lại

- [Kết quả từng ca, đã che thông tin bí mật](e2e-fix-evidence/results.json).
- [ID chứng từ, số liệu và 16 bước UI cuối](e2e-fix-evidence/ui-flow.json).
- [MRP](e2e-fix-evidence/flow-plan.png), [hoàn tất sản xuất](e2e-fix-evidence/flow-productionCompleted.png), [duyệt xuất thành phẩm](e2e-fix-evidence/flow-fgApproved.png), [báo cáo](e2e-fix-evidence/flow-report.png), [lỗi danh sách](e2e-fix-evidence/ADMIN-list-error.png).
- Log Maven/XML: `backend/target/surefire-reports/`. Script và kết quả thô: `backend/target/fix-*.mjs`, `fix-results.json`. Đây là thư mục build cục bộ, có thể mất khi chạy clean.
- Đã tái sử dụng QA cũ bằng các bản `fix-run.mjs`, `fix-more.mjs`, `fix-final-api.mjs`, `fix-ui.mjs`, `fix-ui-details.mjs`, `fix-ui-states.mjs`, `fix-ui-error.mjs`, `fix-invariants.mjs`; bổ sung `fix-ui-flow-final.mjs`, `fix-retry.mjs`, `fix-pending.mjs`. Bản final API loại phần oracle F04 đã lỗi thời và dùng script invariant đã sửa của QA trước.

## Giới hạn xác minh

- Không kiểm tra trên DB thật theo yêu cầu. Không chạy lại seed hay bộ SQL khởi tạo trên DB E2E đã có dữ liệu.
- Chưa bấm đúp riêng từng nút của mọi màn hình; đã áp dụng guard chung, kiểm tra unit, đo một form thật với request chậm và chạy chuỗi nghiệp vụ thực tế đầy đủ.
- Phân trang hơn 100 lựa chọn được xác minh bằng test 201 phần tử; DB demo không có hơn 100 bản ghi cho từng loại dropdown để kiểm chứng mọi dropdown với dữ liệu thật lớn.
- Không ép PostgreSQL tạo riêng từng SQLState 40P01/40001 bằng một kịch bản deadlock nhân tạo; đã triển khai mapping và kiểm thử đồng thời hai nghiệp vụ được yêu cầu.
- UI dùng Chrome desktop headless. Không tuyên bố đã kiểm thử Safari/Firefox/mobile hoặc tải đồng thời dài hạn.

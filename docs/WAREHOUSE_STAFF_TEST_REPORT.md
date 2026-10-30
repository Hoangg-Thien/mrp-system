# Kiểm thử Warehouse và Staff — 29/10/2026

## Cập nhật mới nhất: đã sửa và xác minh cả 3 lỗi UI

**UI-01, UI-02 và UI-03 đã được xử lý.** Lượt Playwright hồi quy mới đạt **8/8 kiểm tra**, không có `pageerror`, lưu 8 ảnh mới. Lint, production build và 4/4 test helper frontend cũng đạt. Số 41/44 ở phần dưới là kết quả lịch sử trước khi sửa, không phải trạng thái hiện tại.

| Lỗi | Thay đổi | Xác minh sau sửa |
|---|---|---|
| UI-01: nhãn biểu đồ | Dùng `XAxis dataKey="name"` để Recharts lấy tên đối tượng | Trục X hiển thị `UI QA Material` và `UI QA Product` ở hai loại báo cáo |
| UI-02: lỗi JavaScript sau HTTP 409 | Theo dõi kết quả hook modal qua `dialog.then(...)`, kích hoạt chế độ await của Ant Design; giữ rejection từ mutation để modal không tự đóng khi lỗi | Duyệt thiếu tồn vẫn 409 và hiện thông báo; không pageerror; bấm đúp chỉ một POST; thử lại được, hủy/mở lại được; khi đủ tồn, duyệt thành công và modal đóng |
| UI-03: ô tìm kiếm không xóa | Điều khiển giá trị ô nhập bằng state riêng, xóa state khi Đặt lại; vẫn chỉ tìm khi gửi ô Search | Cả tab NVL và thành phẩm: tìm không có dữ liệu → Đặt lại xóa ô nhập và phục hồi dữ liệu; Enter sau đó vẫn hiển thị dữ liệu |

Kiểm thử dùng lại database/container QA riêng. Lượt hồi quy tạo một phiếu xuất mới, điều chỉnh tăng 1 thành phẩm qua UI rồi duyệt xuất 1 để kiểm tra đường thành công; tồn thành phẩm cuối vẫn 0. Không thao tác dữ liệu ứng dụng hiện tại.

- [Kết quả hồi quy mới](warehouse-staff-ui-fix-evidence/results.json).
- [Nhãn biểu đồ đã sửa](warehouse-staff-ui-fix-evidence/01-report-material-name.png).
- [Ô tìm kiếm đã xóa sau Đặt lại](warehouse-staff-ui-fix-evidence/03-reset-material-search.png).
- [Thiếu tồn được xử lý, không lỗi JavaScript](warehouse-staff-ui-fix-evidence/04-shortage-no-unhandled-error.png).
- [Duyệt thành công sau khi đủ tồn](warehouse-staff-ui-fix-evidence/07-confirm-success-closes-modal.png).
- Runner hồi quy: `frontend/scripts/warehouse-staff-fixes.mjs`. Chạy từ gốc repo bằng `node frontend/scripts/warehouse-staff-fixes.mjs` khi frontend/backend QA ở cổng 15173/18080 đang chạy; truyền mật khẩu tài khoản QA qua `MRP_QA_PASSWORD`. Runner mặc định dùng Playwright đã cài trong `backend/target/ui-test-tools/node_modules/playwright`; có thể chỉ định đường dẫn package bằng `PLAYWRIGHT_PACKAGE`. Cần fixture `UI QA Material`, `UI QA Product`, tài khoản `ui_warehouse`/`ui_staff` và tồn thành phẩm ban đầu bằng 0. Runner này kiểm tra hồi quy 3 lỗi, không dựng môi trường/fixture hoặc thay thế bộ kiểm thử luồng chính.

## Cập nhật: đã chạy UI bằng Playwright

Đã thao tác trên Chromium headless qua Playwright ngày 29/10/2026. **44 điểm kiểm tra: 41 đạt, 3 phát hiện lỗi UI; lưu 43 ảnh PNG**. Đây là các checkpoint của một phiên kiểm thử khám phá (gồm mở trang, kiểm tra trạng thái và số liệu), không phải 44 test case tự động độc lập. Kết quả backend ở phần dưới là lượt chạy trước, không cộng vào số UI.

Môi trường riêng: frontend `127.0.0.1:15173`, backend `127.0.0.1:18080`, PostgreSQL `mrp_ui_qa` trong container `mrp-ui-qa-20261029` ở cổng 15433. Tài khoản và danh mục test được chuẩn bị bằng API; nghiệp vụ Warehouse/Staff bên dưới được thao tác bằng form và nút trên trình duyệt. Không dùng database ứng dụng ở cổng 5433.

### Những chức năng đã kiểm tra trên UI

| Nhóm | Kết quả thực tế |
|---|---|
| Đăng nhập, dashboard, hồ sơ | Cả Warehouse và Staff đăng nhập qua form; dashboard và trang hồ sơ tải được |
| Nhập kho | Validation khi thiếu NCC/NVL; tạo phiếu 10 NVL × 5 = 50; hoàn tất; tạo phiếu khác, sửa 2 lên 4 NVL, tổng tiền 20 rồi hủy |
| BOM | Tạo BOM 2 NVL/1 SP; vô hiệu hóa và kích hoạt lại |
| MRP | Mục tiêu 3 SP: cần sản xuất 3, nhu cầu NVL 6, tồn 10, không thiếu; chuyển sang form giao việc với số lượng 3 được điền sẵn |
| Yêu cầu sản xuất | Warehouse giao Staff; Staff thấy yêu cầu trong danh sách |
| Xuất NVL | Staff gửi phiếu 6 NVL; Warehouse duyệt; tồn từ 10 còn 4 |
| Thực hiện sản xuất | Staff bắt đầu và hoàn tất 3 SP, nhập kho 3 thành phẩm |
| Xuất thành phẩm | Staff đề nghị xuất 3, Warehouse duyệt; tồn thành phẩm còn 0 |
| Thiếu tồn, từ chối/hủy | Yêu cầu thêm 1 khi tồn 0 có cảnh báo; duyệt bị 409, phiếu vẫn chờ; Warehouse từ chối có lý do; Staff hủy một phiếu chờ khác |
| Kiểm kê, sổ kho | Điều chỉnh NVL +1 rồi -1, tồn lần lượt 5 rồi 4; sổ kho có đủ 6 biến động |
| Báo cáo | NVL tháng: đầu 0, nhập 10, xuất 6, cuối 4; SP nhập 3, xuất 3, cuối 0; quý 4/năm có điều chỉnh +1/-1 và cuối 4 |
| Xuất CSV | Tải được CSV và đối chiếu đúng dòng NVL: 0,10,6,1,1,4 |
| Phân quyền UI | Staff vào `/warehouse/inventory` bị chuyển về `/staff`; Warehouse vào `/staff/productions` bị chuyển về `/warehouse` |
| Lỗi tải dữ liệu | Giả lập HTTP 503 trên danh sách của mỗi role: hiện lỗi và nút Thử lại; bỏ giả lập, bấm Thử lại khôi phục dữ liệu |
| Tìm kiếm tồn kho | Từ khóa không tồn tại trả danh sách rỗng; Đặt lại khôi phục dữ liệu nhưng có lỗi ô nhập bên dưới |

### Ba lỗi phát hiện ban đầu (đã xử lý trong cập nhật mới nhất)

1. **UI-01 — Biểu đồ báo cáo hiện nhãn `0` thay vì tên vật tư.** Bảng số liệu đúng. `frontend/src/pages/warehouse/StockReportsPage.jsx:336` dùng `<XAxis dataIndex="name">`; cần kiểm tra chuyển sang prop `dataKey`. [Ảnh](warehouse-staff-ui-evidence/41-report-chart-label-defect.png).
2. **UI-02 — Lỗi JavaScript chưa được xử lý khi duyệt phiếu thiếu tồn.** Backend trả 409 đúng, thông báo nghiệp vụ hiện rõ và hộp xác nhận vẫn mở, nhưng Playwright nhận `pageerror` với nội dung “Tồn kho thành phẩm #1 không đủ để xuất kho…”. Cần kiểm tra luồng Promise trong hộp xác nhận, không bỏ cơ chế chặn thiếu tồn. [Ảnh](warehouse-staff-ui-evidence/26-approve-insufficient-stock-blocked.png).
3. **UI-03 — Đặt lại bộ lọc không xóa từ khóa trong ô tìm kiếm tồn kho.** Tìm `NO_SUCH_UI_QA`, Enter → không dữ liệu; bấm Đặt lại → hiện lại vật tư nhưng ô vẫn giữ từ khóa cũ. `frontend/src/pages/warehouse/InventoryPage.jsx:118` có Input.Search không được đồng bộ giá trị với filter. [Ảnh](warehouse-staff-ui-evidence/40-inventory-filter-reset.png).

### Bằng chứng mới

- [Kết quả, phản hồi mạng và ba phát hiện](warehouse-staff-ui-evidence/results.json).
- [MRP](warehouse-staff-ui-evidence/06-mrp-plan.png), [Staff hoàn tất sản xuất](warehouse-staff-ui-evidence/13-staff-production-completed.png), [Warehouse duyệt xuất thành phẩm](warehouse-staff-ui-evidence/15-warehouse-fg-approved.png).
- [Báo cáo NVL](warehouse-staff-ui-evidence/16-material-stock-report.png), [CSV](warehouse-staff-ui-evidence/stock-report.csv).

Giới hạn: một Chromium desktop viewport 1440×1000, fixture nhỏ; chưa kiểm tra mobile/cross-browser, phân trang nhiều trang, mọi nhánh hủy/từ chối NVL/sản xuất hoặc sửa hồ sơ/đổi mật khẩu qua UI. Các lỗi selector/timeout khi xây dựng thao tác đã được đối chiếu UI và thử lại; không tính là lỗi sản phẩm. Chưa lưu một bộ runner hoàn chỉnh có thể tái chạy từ đầu; kết quả này từ phiên Playwright tương tác.

## Kết quả chạy mới

- Backend: **137 test đạt, 0 failures, 0 errors, 0 skipped**; Maven ghi `BUILD SUCCESS` (kết thúc 19:19:38, UTC+7).
- Frontend: **4/4 test đạt**; production build và lint thành công.
- Chạy bộ test hiện có trên source hiện tại; không sửa mã nghiệp vụ.

## Phạm vi chức năng đã được test

| Nhóm test | Số ca đạt | Nội dung chính |
|---|---:|---|
| BOM | 13 | Tạo/version, kích hoạt/vô hiệu hóa, NVL trùng, sản phẩm/NVL không hoạt động, thiếu BOM |
| Nhập kho | 11 | Tạo phiếu, sửa chi tiết, hoàn tất và ghi nhập kho, hủy, chặn trạng thái không hợp lệ, phân trang |
| MRP calculator | 3 | Nhu cầu sản xuất, tồn khả dụng và lượng đã cam kết |
| Sản xuất và yêu cầu NVL | 17 | Giao việc, chặn nhân viên không hoạt động, giới hạn BOM, duyệt/từ chối, thiếu tồn, tạo/hoàn tất sản xuất, điều kiện hủy |
| Quyền đọc yêu cầu sản xuất | 3 | STAFF/WAREHOUSE_MANAGER được đọc, ADMIN bị chặn |
| Xuất thành phẩm | 17 | Tạo, duyệt/từ chối/hủy, thiếu tồn, quyền sở hữu phiếu, lọc theo nhân viên, validation |
| Báo cáo tồn | 27 | Tháng/quý/năm, số dư và ranh giới kỳ, validation; warehouse được đọc, staff bị chặn |
| Tích hợp PostgreSQL | 5 | Gửi đồng thời yêu cầu NVL/sản xuất, số lượng nguyên thành phẩm, độ chính xác NVL, HTTP 404/405 |
| Hồi quy dùng chung | 41 | Tài khoản, đăng nhập, hồ sơ/mật khẩu, danh mục, khởi động ứng dụng |
| Frontend helpers | 4 | Lấy đủ các trang, lỗi trang sau, chặn gửi trùng và cho phép thử lại |

Các test backend sử dụng mock, H2 profile test hoặc PostgreSQL Testcontainers riêng theo từng lớp test. Năm test PostgreSQL thực sự chạy, không bị bỏ qua.

## Lệnh kiểm tra

```text
cd backend
.\mvnw.cmd test
cd ../frontend
node --test src/api/allPages.test.js src/api/singleFlight.test.js
npm.cmd run build
npm.cmd run lint
```

Log backend: `docs/warehouse-staff-backend-test.log`; báo cáo chi tiết từng ca: `backend/target/surefire-reports/`.

## Giới hạn

Các ghi chú chưa kiểm tra UI/lỗi kết nối bên dưới là lịch sử trước lượt Playwright nêu trên.

- Lượt thử kiểm tra UI tiếp theo ngày 29/10/2026 bị chặn trước khi mở ứng dụng: công cụ trình duyệt trả `The browser access host is unavailable`; kết nối Windows trả `Computer Use native pipe is unavailable ... (os error 2)`. Không có trình duyệt/cửa sổ nào được công cụ trả về. Chưa thực hiện thao tác UI và chưa có kết quả pass/fail UI mới; cần khôi phục kết nối công cụ để tiếp tục.

- Đây là kiểm thử tự động hiện có ở mức service/API và kiểm tra build/lint frontend; chưa thao tác UI trên trình duyệt trong lượt này. Không khẳng định toàn bộ luồng UI đã đạt.
- Chưa có test riêng đầy đủ cho dashboard, điều chỉnh tồn kho và mọi tổ hợp phân quyền trên tất cả endpoint.
- Maven wrapper ban đầu lỗi trong sandbox khi truy cập cache; chạy ngoài sandbox đã hoàn tất 137 test. Tool vẫn trả exit code 1 dù log Maven ghi BUILD SUCCESS; kết quả trên được xác nhận từ phần tổng kết Surefire/Maven, không suy ra từ exit code wrapper.

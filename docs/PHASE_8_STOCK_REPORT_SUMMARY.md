# TỔNG KẾT PHASE 8: BÁO CÁO KHO THEO KỲ (STOCK REPORTS)

## 1. Mục tiêu đã hoàn thành
Triển khai toàn bộ chức năng báo cáo xuất nhập tồn kho theo kỳ (`GET /api/reports/stock`) theo đặc tả tại `docs/BACKEND_E2E_PLAN.md` (mục 5.1.4):
- Thống kê xuất nhập tồn cho cả **Nguyên vật liệu (`MATERIAL`)** và **Thành phẩm (`PRODUCT`)**.
- Hỗ trợ 3 cấp độ thời gian (**`granularity`**): Tháng (`MONTH`), Quý (`QUARTER`), và Năm (`YEAR`).
- Đáp ứng đầy đủ các yêu cầu nghiệp vụ:
  - Mỗi vật tư / sản phẩm một dòng gồm: Tồn đầu kỳ (`openingBalance`), Nhập trong kỳ (`inQuantity`), Xuất trong kỳ (`outQuantity`), Điều chỉnh tăng (`adjustInQuantity`), Điều chỉnh giảm (`adjustOutQuantity`), Tồn cuối kỳ (`closingBalance`).
  - Tồn đầu kỳ = tổng có dấu các biến động trước kỳ (`created_at < startInclusive`).
  - Biến động trong kỳ tính theo biên nửa đóng nửa mở: `created_at >= startInclusive` và `created_at < endExclusive`.
  - Biến động đúng 00:00:00 ngày đầu kỳ thuộc về kỳ đó, không thuộc về kỳ trước.
  - Vật tư/sản phẩm không phát sinh trong kỳ nhưng có tồn đầu vẫn hiển thị đầy đủ trong báo cáo.
  - Tồn cuối kỳ hiện tại khớp chính xác với tồn thực trong bảng `inventories`.
  - Kiểm tra và xử lý lỗi chặt chẽ: tham số sai (tháng 13, quý 5, năm âm, thiếu tham số) trả về `400 Bad Request`.
  - Phân quyền nghiêm ngặt: Chỉ `WAREHOUSE_MANAGER` và `ADMIN` được xem báo cáo; `STAFF` truy cập nhận `403 Forbidden`.

---

## 2. Các thành phần đã tạo (Package `mrp.report`)

| File | Vai trò |
|---|---|
| `StockReportKind.java` | Enum loại đối tượng báo cáo: `MATERIAL`, `PRODUCT` |
| `ReportGranularity.java` | Enum cấp độ kỳ: `MONTH`, `QUARTER`, `YEAR` |
| `StockReportItemView.java` | Record chứa dữ liệu một dòng báo cáo: `id`, `code`, `name`, `unit`, `openingBalance`, `inQuantity`, `outQuantity`, `adjustInQuantity`, `adjustOutQuantity`, `closingBalance` (kèm các alias `getItemId()`, `getItemCode()`, `getItemName()`) |
| `StockReportService.java` | Service validate tham số, tính toán mốc thời gian `startInclusive` & `endExclusive`, thực thi truy vấn CTE tối ưu qua `JdbcTemplate` |
| `StockReportController.java` | REST Controller với `@PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'ADMIN')")` tại `GET /api/reports/stock` |

---

## 3. Chi tiết API Endpoint

- **URL**: `GET /api/reports/stock`
- **Quyền hạn**: `hasAnyRole('WAREHOUSE_MANAGER', 'ADMIN')`
- **Query Parameters**:
  - `kind` (`MATERIAL` | `PRODUCT`, bắt buộc)
  - `granularity` (`MONTH` | `QUARTER` | `YEAR`, bắt buộc)
  - `year` (int, năm hợp lệ > 0, bắt buộc)
  - `period` (int, bắt buộc với `MONTH` từ 1..12, bắt buộc với `QUARTER` từ 1..4, tùy chọn với `YEAR`)
- **Response**: `List<StockReportItemView>` (JSON Array)

Ví dụ response:
```json
[
  {
    "id": 1,
    "code": "MAT01",
    "name": "Gỗ sồi",
    "unit": "m3",
    "openingBalance": 70.000,
    "inQuantity": 50.000,
    "outQuantity": 20.000,
    "adjustInQuantity": 5.000,
    "adjustOutQuantity": 2.000,
    "closingBalance": 103.000
  }
]
```

---

## 4. Kiểm thử (Unit & Integration Tests)

Tổng cộng **27 test cases** cho Phase 8 bao gồm 2 bộ test:

### A. `StockReportServiceTest` (18 unit tests):
- Kiểm tra toàn bộ các trường hợp tham số không hợp lệ (null kind, invalid kind, null granularity, invalid granularity, negative year, null period, month 13, month 0, quarter 5, quarter 0, year negative).
- Kiểm tra tính toán mốc thời gian chuyển sang SQL `Timestamp` (`MONTH`, `QUARTER`, `YEAR`).
- Kiểm tra row mapper và công thức tính `closingBalance`.

### B. `StockReportIntegrationTest` (9 integration tests với SpringBootTest & H2):
- **RPT-01**: Báo cáo tháng, quý, năm cho dữ liệu biến động nhiều tháng khớp chính xác tính toán tay.
- **RPT-01b**: Item có tồn đầu nhưng không phát sinh trong kỳ vẫn hiển thị đầy đủ.
- **RPT-02**: Biến động kho phát sinh đúng 00:00:00 ngày đầu kỳ thuộc về kỳ mới, không thuộc kỳ trước.
- **RPT-03**: Tồn cuối kỳ trong kỳ hiện tại khớp chính xác giá trị `inventories.quantity`.
- **RPT-04**: Quyền hạn `STAFF` -> `403 Forbidden`, `WAREHOUSE_MANAGER` và `ADMIN` -> `200 OK`. Tham số sai (tháng 13, quý 5, năm âm) -> `400 Bad Request`.
- **RPT-05**: Báo cáo hoạt động chính xác cho cả `MATERIAL` và `PRODUCT`.

### Kết quả chạy kiểm thử toàn dự án:
- `StockReportServiceTest`: **18/18 PASS**
- `StockReportIntegrationTest`: **9/9 PASS**
- **Toàn bộ backend**: **99/99 PASS** (0 failures, 0 errors, 0 skipped).

---

## 5. Bàn giao cho Phase 9
Phase 8 đã hoàn thành 100%. Tiếp theo là **Phase 9: Vận hành, dữ liệu demo, sao lưu/phục hồi, tài liệu**.

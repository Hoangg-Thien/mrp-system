# Phase 6 – Lõi MRP: Kế hoạch, Yêu cầu sản xuất, Phiếu xuất NVL & Sản xuất

Cập nhật: 2026-10-20. Phần 6 của `docs/BACKEND_E2E_PLAN.md`: triển khai lõi MRP bao gồm thuật toán tính toán kế hoạch MRP (Quyết định D2), quản lý yêu cầu sản xuất (Production Request), vòng đời phiếu xuất nguyên vật liệu (Material Issue Request) và thực hiện sản xuất (Production execution) theo các mục 5.1.6, 5.2.1–5.2.3.

## 1. Mục tiêu

- **Kế hoạch MRP** (`GET /api/mrp/plan`): Cho phép Warehouse Manager (WM) nhập `productId` và số lượng thành phẩm mong muốn (`quantity`). Thuật toán tính toán tồn khả dụng (`available = fgOnHand - fgReserved + fgIncoming`), số lượng cần sản xuất (`toProduce = max(0, quantity - available)`), và nhu cầu chi tiết từng NVL theo BOM active (`gross`, `committed`, `usable`, `shortage`).
- **Yêu cầu sản xuất** (`/api/production-requests`):
  - WM tạo yêu cầu sản xuất cho sản phẩm active, BOM do server tự lấy từ BOM active của sản phẩm, gán cho nhân viên active (`assigned_to`).
  - STAFF chỉ xem được yêu cầu sản xuất được giao cho mình (trả về 404 nếu xem yêu cầu của người khác theo quy ước D11 để không lộ sự tồn tại).
  - Hủy yêu cầu sản xuất (WM) tuân thủ quy tắc D8: chỉ khi chưa có phiếu xuất NVL `APPROVED` và chưa có đợt sản xuất không-`CANCELLED`.
  - API `GET /{id}/requirements`: tính toán nhu cầu định mức, số lượng đã xuất (APPROVED), đang chờ duyệt (PENDING), còn được phép yêu cầu, tồn kho, cam kết ở các PR mở khác và lượng thiếu hụt.
- **Phiếu xuất nguyên vật liệu** (`/api/material-issue-requests`):
  - Nhân viên được giao tạo phiếu xuất ở trạng thái `PENDING`. NVL phải thuộc BOM của yêu cầu sản xuất và tổng `PENDING + APPROVED` không được vượt quá định mức BOM (`bom_quantity * pr.quantity`).
  - Cho phép tạo phiếu khi kho đang thiếu kèm cờ và cảnh báo (Quyết định D3).
  - Duyệt phiếu (`approve` - WM): kiểm tra tồn kho tức thời của từng dòng NVL. Nếu thiếu dù chỉ 1 dòng, rollback toàn bộ transaction, giữ nguyên `PENDING`, không phát sinh movement nào và trả về 409 thông báo NVL thiếu. Nếu đủ, chuyển `APPROVED` và ghi sổ kho `OUT` cho từng dòng với `created_by = wm.userId`.
  - Từ chối phiếu (`reject` - WM): yêu cầu nhập lý do, chuyển `REJECTED`, ghi nối vào `note` dạng `[TỪ CHỐI] <lý do>` (Quyết định D6). Hạn mức BOM được hoàn trả lại.
  - Hủy phiếu (`cancel` - STAFF): chỉ khi phiếu đang `PENDING`.
- **Thực hiện sản xuất** (`/api/productions`):
  - Nhân viên bắt đầu đợt sản xuất (`POST /api/productions`) với trạng thái `IN_PROGRESS`. Chuyển yêu cầu sản xuất sang `IN_PROGRESS` ở đợt sản xuất đầu tiên (Quyết định D4). Tổng số lượng sản xuất không-`CANCELLED` không được vượt số lượng yêu cầu của PR.
  - Hoàn thành sản xuất (`complete` - STAFF): kiểm tra toàn bộ NVL trong BOM đã được xuất kho đủ cho tổng số lượng hoàn thành (`totalDoneQty * bom_item.quantity <= approvedIssued`). Nếu đủ, chuyển `COMPLETED`, ghi sổ kho `IN` thành phẩm với `created_by = staff.userId`.
  - Tự động hoàn thành yêu cầu sản xuất: khi tổng số lượng `COMPLETED` của các đợt sản xuất đạt đủ số lượng yêu cầu của PR, PR tự động chuyển sang `COMPLETED` (Quyết định D4).

## 2. Danh sách Endpoints REST API

| Phương thức | Đường dẫn | Vai trò | Mô tả |
|---|---|---|---|
| `GET` | `/api/mrp/plan` | WAREHOUSE_MANAGER | Lập kế hoạch MRP cho sản phẩm và số lượng cần |
| `POST` | `/api/production-requests` | WAREHOUSE_MANAGER | Tạo yêu cầu sản xuất (tự gắn BOM active) |
| `GET` | `/api/production-requests` | STAFF, WAREHOUSE_MANAGER, ADMIN | Danh sách yêu cầu sản xuất (STAFF chỉ thấy của mình) |
| `GET` | `/api/production-requests/{id}` | STAFF, WAREHOUSE_MANAGER, ADMIN | Chi tiết yêu cầu sản xuất (kèm tiến độ, các đợt SX và phiếu xuất) |
| `POST` | `/api/production-requests/{id}/cancel` | WAREHOUSE_MANAGER | Hủy yêu cầu sản xuất (chỉ khi chưa có phiếu duyệt/sản xuất) |
| `GET` | `/api/production-requests/{id}/requirements` | STAFF, WAREHOUSE_MANAGER | Kiểm tra NVL: định mức, đã xuất, đang xin, thiếu hụt |
| `POST` | `/api/material-issue-requests` | STAFF | Tạo phiếu xuất NVL (chỉ người được giao PR) |
| `GET` | `/api/material-issue-requests` | STAFF, WAREHOUSE_MANAGER, ADMIN | Danh sách phiếu xuất NVL (STAFF chỉ thấy của mình) |
| `GET` | `/api/material-issue-requests/{id}` | STAFF, WAREHOUSE_MANAGER, ADMIN | Chi tiết phiếu xuất NVL và các dòng chi tiết |
| `POST` | `/api/material-issue-requests/{id}/approve` | WAREHOUSE_MANAGER | Duyệt phiếu xuất NVL → ghi sổ kho OUT từng dòng |
| `POST` | `/api/material-issue-requests/{id}/reject` | WAREHOUSE_MANAGER | Từ chối phiếu xuất kèm lý do |
| `POST` | `/api/material-issue-requests/{id}/cancel` | STAFF | Hủy phiếu xuất NVL (chỉ khi PENDING) |
| `POST` | `/api/productions` | STAFF | Bắt đầu đợt sản xuất (chuyển PR sang IN_PROGRESS) |
| `GET` | `/api/productions` | STAFF, WAREHOUSE_MANAGER, ADMIN | Danh sách đợt sản xuất |
| `GET` | `/api/productions/{id}` | STAFF, WAREHOUSE_MANAGER, ADMIN | Chi tiết đợt sản xuất |
| `POST` | `/api/productions/{id}/complete` | STAFF | Hoàn thành sản xuất → kiểm tra NVL đủ và ghi sổ kho IN thành phẩm |
| `POST` | `/api/productions/{id}/cancel` | STAFF | Hủy đợt sản xuất (chỉ khi IN_PROGRESS) |

## 3. Kiến trúc

- `MrpCalculator`: Lớp tính toán thuần Java (Pure domain, không phụ thuộc Spring/DB) thực thi chính xác thuật toán MRP D2.
- `ProductionRequest` (`@Entity`, bảng `production_requests`): `id`, `product_id`, `bom_id`, `quantity`, `warehouse_manager_id`, `assigned_to`, `status`, `created_at`, `note`.
- `MaterialIssueRequest` (`@Entity`, bảng `material_issue_requests`): `id`, `production_request_id`, `requested_by`, `reviewed_by`, `status`, `requested_at`, `reviewed_at`, `note`.
- `MaterialIssueRequestItem` (`@Entity`, bảng `material_issue_request_items`): `id`, `request_id`, `material_id`, `quantity` (NUMERIC(15,3)).
- `Production` (`@Entity`, bảng `productions`): `id`, `production_request_id`, `quantity`, `produced_by`, `status`, `started_at`, `completed_at`, `note`.
- Repositories:
  - `ProductionRequestRepository`: `findByIdForUpdate` (Pessimistic Write Lock), `findByProductIdAndStatusIn`, `findByStatusIn`.
  - `MaterialIssueRequestRepository`: `findByIdForUpdate`, `findByProductionRequestId`, `existsByProductionRequestIdAndStatus`.
  - `MaterialIssueRequestItemRepository`: `findByRequestId`, `findByRequestIdIn`.
  - `ProductionRepository`: `findByIdForUpdate`, `findByProductionRequestId`, `findByProductionRequestIdAndStatus`, `findByProductionRequestIdAndStatusNot`.
- `ProductionService`: Quản lý toàn bộ transaction cho các nghiệp vụ cốt lõi, phối hợp với `StockLedger` để ghi nhận các biến động tồn kho `IN` / `OUT`.
- `ProductionQuery`: Phân trang an toàn và whitelist sắp xếp cho 3 nhóm chứng từ.
- Controllers:
  - `MrpPlanController` (`/api/mrp/plan`)
  - `ProductionRequestController` (`/api/production-requests`)
  - `MaterialIssueRequestController` (`/api/material-issue-requests`)
  - `ProductionController` (`/api/productions`)

## 4. Danh sách file thêm mới

### Gói `mrp.production` (`backend/src/main/java/mrp/production/`)
- `MrpCalculator.java` — Thuật toán thuần tính toán kế hoạch MRP
- `ProductionRequestStatus.java` — Enum trạng thái yêu cầu sản xuất (`PENDING`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`)
- `MaterialIssueRequestStatus.java` — Enum trạng thái phiếu xuất NVL (`PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`)
- `ProductionStatus.java` — Enum trạng thái đợt sản xuất (`IN_PROGRESS`, `COMPLETED`, `CANCELLED`)
- `ProductionRequest.java` — Thực thể `production_requests`
- `MaterialIssueRequest.java` — Thực thể `material_issue_requests`
- `MaterialIssueRequestItem.java` — Thực thể `material_issue_request_items`
- `Production.java` — Thực thể `productions`
- `ProductionRequestRepository.java` — Repository yêu cầu sản xuất
- `MaterialIssueRequestRepository.java` — Repository phiếu xuất NVL
- `MaterialIssueRequestItemRepository.java` — Repository dòng chi tiết xuất NVL
- `ProductionRepository.java` — Repository đợt sản xuất
- `ProductionRequests.java` — DTO request + validation
- `ProductionViews.java` — DTO read models
- `ProductionQuery.java` — Pagination & whitelist sort
- `ProductionService.java` — Service xử lý logic lõi MRP
- `MrpPlanController.java` — Controller `/api/mrp/plan`
- `ProductionRequestController.java` — Controller `/api/production-requests`
- `MaterialIssueRequestController.java` — Controller `/api/material-issue-requests`
- `ProductionController.java` — Controller `/api/productions`

### Tests (`backend/src/test/java/mrp/production/`)
- `MrpCalculatorTest.java` — 3 unit test cho `MrpCalculator` (ví dụ bàn với số liệu chính xác theo MRP-01 và MRP-01b).
- `ProductionServiceTest.java` — 17 unit test Mockito cho `ProductionService` bao phủ MRP-01 đến MRP-15.

## 5. Kết quả kiểm thử

- **20/20 unit tests Phase 6 PASS**:
  - `mrp01_tableExampleNumbers`: Tính toán số liệu bàn (1 mặt + 4 chân), available = 4, toProduce = 6, thiếu mặt = 1, thiếu chân = 0.
  - `mrp01_targetLessThanAvailableYieldsZeroToProduce`: Mục tiêu <= available -> toProduce = 0.
  - `mrp01b_committedReducesUsableAndIncreasesShortage`: Cam kết ở PR mở làm giảm usable, tăng shortage.
  - `mrp01_getPlanReturnsCalculation`: Tích hợp service tính toán plan đúng với dữ liệu kho và PR đang mở.
  - `mrp01b_getPlanWithoutActiveBomThrowsBusinessException`: Sản phẩm không có BOM active -> 409.
  - `mrp02_createProductionRequestSucceeds`: Tạo yêu cầu sản xuất -> PENDING, tự lấy BOM active.
  - `mrp02_createProductionRequestFailsWhenStaffInactive`: Giao việc cho nhân viên bị khóa -> 409.
  - `mrp03_getRequirementsReturnsBreakdown`: API kiểm tra NVL trả đúng định mức, đã xuất, đang xin, thiếu hụt.
  - `mrp04_staffCannotAccessOtherStaffProductionRequest`: Nhân viên A xem PR của nhân viên B -> 404 (D11).
  - `mrp05_createMaterialIssueRequestSucceedsWithinBom`: Tạo phiếu xuất NVL trong định mức BOM -> PENDING.
  - `mrp05b_createMaterialIssueRequestExceedingBomThrows409`: Tạo phiếu xuất vượt định mức BOM -> 409.
  - `mrp06_approveMaterialIssueRequestSufficientStock`: Duyệt phiếu khi đủ tồn -> APPROVED, ghi sổ kho OUT.
  - `mrp07_approveMaterialIssueRequestInsufficientStockThrows409`: Thiếu tồn khi duyệt -> 409, giữ nguyên PENDING, không ghi sổ kho.
  - `mrp08_rejectMaterialIssueRequestSetsRejected`: Từ chối phiếu -> REJECTED, ghi nối lý do [TỪ CHỐI].
  - `mrp11_createProductionSucceedsAndSetsPrInProgress`: Tạo đợt sản xuất -> IN_PROGRESS, PR chuyển IN_PROGRESS.
  - `mrp11b_createProductionExceedingQuantityThrows409`: Tổng sản xuất vượt số lượng yêu cầu -> 409.
  - `mrp12_completeProductionFailsWhenMaterialsNotSufficientlyIssued`: Hoàn thành khi NVL xuất chưa đủ theo BOM -> 409.
  - `mrp12b_completeProductionSucceedsAndSetsPrCompleted`: Hoàn thành khi NVL đủ -> COMPLETED, ghi sổ kho IN thành phẩm, PR tự chuyển COMPLETED khi đủ sản lượng.
  - `mrp13_cancelProductionRequestAllowedWhenNoApprovedMirAndNoProds`: Hủy PR hợp lệ theo D8.
  - `mrp13b_cancelProductionRequestFailsWhenApprovedMirExists`: Hủy PR khi đã có phiếu xuất duyệt -> 409.
- **Toàn bộ 55/55 tests** trong test suite của backend Spring Boot đều **BUILD SUCCESS** và **PASS 100%**.

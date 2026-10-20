# Phase 4 – Phiếu nhập kho nguyên vật liệu (Purchase Receipts)

Cập nhật: 2026-10-20. Phần 4 của `docs/BACKEND_E2E_PLAN.md`: phiếu nhập kho NVL với vòng đời `PENDING → COMPLETED/CANCELLED`, ghi tồn qua `StockLedger.post()` khi hoàn thành.

## 1. Mục tiêu

- Warehouse Manager (WM) tạo phiếu nhập ở trạng thái `PENDING` với nhiều dòng nguyên vật liệu; sửa toàn bộ dòng (replace) chỉ khi `PENDING`.
- `complete`: chuyển `COMPLETED` rồi phát sinh movement `IN` từng dòng qua `StockLedger.post()` (tồn tăng). `cancel`: hủy từ `PENDING`, không động đến tồn.
- `COMPLETED` / `CANCELLED` là trạng thái cuối — không sửa phiếu hay dòng nữa (service pre-check + trigger DB backstop).
- `amount = round(quantity × unit_price, 2)` tính server; `total_amount = Σ amount`.
- Material phải `active` khi tạo dòng và khi `complete` (gọi `CatalogService.requireActiveMaterial()` — kế thừa P3): không có → 404, inactive → 409.
- `created_by` của movement = `users.id` của WM thao tác (khác `warehouse_manager_id` = profile id ghi trên phiếu).

## 2. Endpoint

| Phương thức | Đường dẫn | Vai trò | Mô tả |
|---|---|---|---|
| POST | `/api/purchase-receipts` | WAREHOUSE_MANAGER | Tạo phiếu nhập `PENDING` |
| GET | `/api/purchase-receipts` | STAFF, WAREHOUSE_MANAGER, ADMIN | Danh sách, lọc + phân trang |
| GET | `/api/purchase-receipts/{id}` | STAFF, WAREHOUSE_MANAGER, ADMIN | Chi tiết phiếu + các dòng |
| PUT | `/api/purchase-receipts/{id}` | WAREHOUSE_MANAGER | Thay toàn bộ dòng (chỉ `PENDING`) |
| POST | `/api/purchase-receipts/{id}/complete` | WAREHOUSE_MANAGER | Hoàn thành → ghi tồn `IN` |
| POST | `/api/purchase-receipts/{id}/cancel` | WAREHOUSE_MANAGER | Hủy phiếu (chỉ `PENDING`) |

Bộ lọc `GET` list: `supplierId`, `status`, `from`, `to`, `warehouseManagerId` + `page`, `size` (mặc định 20), `sort` (mặc định `id`), `direction` (mặc định `desc`).

Không có `DELETE`: xóa phiếu/dòng bị chặn bởi trigger `trg_final_immutable` / `trg_freeze_items`. Sửa dòng = `PUT` thay **toàn bộ** (không PATCH từng dòng) để tránh xung đột khóa kép `(purchase_receipt_id, material_id)`.

## 3. Kiến trúc

- `PurchaseReceipt` (`@Entity`, bảng `purchase_receipts`): `supplier_id`, `warehouse_manager_id` (profile, `updatable=false`), `receipt_date` (`@PrePersist`, `updatable=false`), `status` (`@Enumerated(STRING)`, length 30), `total_amount` (precision 15, scale 2).
- `PurchaseReceiptItem` (`@Entity`, bảng `purchase_receipt_items`, `@IdClass(PurchaseReceiptItemId.class)`): khóa kép `(purchase_receipt_id, material_id)`; `quantity` (scale 3), `unit_price` (scale 2), `amount` (scale 2, server tính).
- `PurchaseReceiptItemId`: `Serializable` composite key với `equals`/`hashCode`.
- `PurchaseReceiptStatus` (enum): `PENDING`, `COMPLETED`, `CANCELLED`.
- `PurchaseReceiptRepository`: `JpaRepository` + `JpaSpecificationExecutor`; `findByIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)`) serialize complete/cancel/replace-items.
- `PurchaseReceiptItemRepository`: `findByPurchaseReceiptIdOrderByMaterialIdAsc`, `countByPurchaseReceiptId`, `deleteByPurchaseReceiptId` (`@Modifying` bulk DELETE — cho PUT replace khi `PENDING`).
- `PurchaseService` (`@Service`, class-level `@Transactional(readOnly=true)`; các method ghi đè `@Transactional`): 6 entry-point (`create`, `list`, `detail`, `replaceItems`, `complete`, `cancel`) + helper. Đọc/ghi tồn CHỈ qua `StockLedger` (kế thừa P3).
- `PurchaseQuery`: `page ≥ 0`, `size 1..100`, sort whitelist `{id, receiptDate, totalAmount, status}`, `direction asc/desc`, sort theo `id` luôn đi kèm để ổn định thứ tự.
- `PurchaseRequests` (DTO + Bean Validation): `PurchaseReceiptRequest` (`supplierId` `@Positive`, `items` `@NotEmpty`) và `PurchaseReceiptItemRequest` (`materialId` `@Positive`, `quantity` `@Positive @Digits(12,3)`, `unitPrice` `@DecimalMin("0") @Digits(13,2)`).
- `PurchaseViews` (read model): `PurchaseReceiptView` (list), `PurchaseReceiptDetailView` (header + items), `PurchaseReceiptItemView` (kèm mã/tên/đơn vị NVL). `status` trả dạng `String` (`.name()`) để client không phụ thuộc enum Java.
- `PurchaseReceiptController`: 6 endpoint `@PreAuthorize` theo vai trò; `create`/`complete` lấy `Authentication` → `CurrentUserResolver.resolve()` để có `actor`.
- Enrich không N+1: `enrichList` (list) và `detailView` (detail) batch `findAllById` cho supplier / warehouse manager / material.

## 4. Business logic / ràng buộc

- **Tạo** (`create`): `supplierId` phải tồn tại (404 `ResourceNotFoundException`); `items` ≥ 1 dòng (`@NotEmpty` ở controller, 400 nếu vi phạm); mỗi `materialId` không trùng trong phiếu (409 `BusinessException`); material phải active qua `requireActiveMaterial` (404 nếu không có, 409 nếu inactive); `amount = round(quantity × unit_price, 2)` (`setScale(2, HALF_UP)` khớp PostgreSQL `round(numeric,int)`); `total_amount = Σ amount`.
- **Replace** (`PUT`): chỉ khi `PENDING` (409 nếu final); `deleteByPurchaseReceiptId` (bulk DELETE) → `buildItems` → `saveAll`; DELETE chạy trước INSERT để tránh xung đột khóa kép; cập nhật `supplier_id` + `total_amount`.
- **Complete**: chỉ khi `PENDING` → đặt `COMPLETED` → `saveAndFlush` (flush để trigger `fn_sm_validate` thấy trạng thái) → với từng dòng (sắp `materialId`): `requireActiveMaterial` rồi `ledger.post(IN, materialId, null, quantity, receiptId, null, null, null, actor.userId(), note)` với `note = "Nhập NVL theo phiếu nhập #" + id`.
- **Cancel**: chỉ khi `PENDING` → `CANCELLED` → flush; không động tồn.
- `created_by` = `actor.userId()` (`users.id`), khác `warehouse_manager_id` (profile id) trên phiếu.
- `from`/`to`: `from ≤ to` (400 `BadRequestException`); lọc `receiptDate` inclusive (`from` = đầu ngày, `to` < đầu ngày kế tiếp).
- DB backstop: `trg_final_immutable` (chặn UPDATE phiếu final), `trg_freeze_items` (chặn INSERT/UPDATE/DELETE dòng khi final), `fn_sm_validate` (movement từ phiếu chỉ khi `COMPLETED` + số lượng khớp), `ck_pri_amount` (`amount = round(quantity×unit_price,2)`), `ck_pr_status` (`status` ∈ enum).

## 5. File thêm và sửa

### Thêm (Phase 4) — gói `mrp.purchase`

- `purchase/PurchaseReceiptStatus.java` — enum `PENDING`/`COMPLETED`/`CANCELLED`
- `purchase/PurchaseReceiptItemId.java` — composite PK `Serializable`
- `purchase/PurchaseReceipt.java` — thực thể `purchase_receipts`
- `purchase/PurchaseReceiptItem.java` — thực thể `purchase_receipt_items` (`@IdClass`)
- `purchase/PurchaseReceiptRepository.java` — `findByIdForUpdate` (pessimistic write)
- `purchase/PurchaseReceiptItemRepository.java` — `@Modifying` bulk delete + truy vấn dòng
- `purchase/PurchaseRequests.java` — DTO + Bean Validation
- `purchase/PurchaseViews.java` — read model (list/detail/item)
- `purchase/PurchaseQuery.java` — phân trang + sort whitelist
- `purchase/PurchaseService.java` — create/list/detail/replaceItems/complete/cancel
- `purchase/PurchaseReceiptController.java` — 6 REST endpoint + `@PreAuthorize`
- `purchase/PurchaseServiceTest.java` (test) — 11 Mockito unit test

### Sửa

Không sửa file ở gói khác — tái sử dụng nguyên vẹn `StockLedger`, `CatalogService`, `CurrentUserResolver`, `CurrentUser`, `GlobalExceptionHandler` từ P2/P3.

## 6. Ánh xạ test (PRC-01..11)

`PurchaseServiceTest` — Mockito unit test (`@ExtendWith(MockitoExtension.class)`), mock toàn bộ repo/dependency, kiểm tra logic nghiệp vụ (trạng thái, tính amount, post movement, freeze `PENDING`); DB trigger/constraint nằm ngoài phạm vi unit test.

- **PRC-01** `pur01` — Tạo `PENDING`: `total_amount = 6000.00` (`Σ amount`), `warehouse_manager_id` = profile id, `items.save` ×2, `requireActiveMaterial(10L)` + `(11L)`.
- **PRC-02** `pur02` — Supplier không tồn tại → `ResourceNotFoundException` (404).
- **PRC-03** `pur03` — Material inactive (`requireActiveMaterial` ném) → `BusinessException` (409) lan truyền.
- **PRC-04** `pur04` — `materialId` trùng trong phiếu → `BusinessException` (409).
- **PRC-05** `pur05` — `complete`: `COMPLETED` + `ledger.post(IN, materialId, null, qty, receiptId, null×3, actor.userId(), note)` cho từng dòng; `note` chứa `"phiếu nhập"`; `created_by` = `users.id` (7).
- **PRC-06** `pur06` — `complete` phiếu đã `COMPLETED` → `BusinessException` (409).
- **PRC-07** `pur07` — `cancel`: `PENDING → CANCELLED`, `saveAndFlush` được gọi.
- **PRC-08** `pur08` — `cancel` phiếu đã `COMPLETED` → `BusinessException` (409).
- **PRC-09** `pur09` — `replaceItems`: `deleteByPurchaseReceiptId` → `saveAll`; `supplier_id` = 3, `total_amount = 5000.00`.
- **PRC-10** `pur10` — `replaceItems` phiếu `COMPLETED` → `BusinessException` (409).
- **PRC-11** `pur11` — `list` với `size=101` → `BadRequestException` (400).

## 7. Ghi chú cho phase sau

- P5 / P8 (xuất kho, sản xuất) tạo movement `OUT` cũng phải dùng `StockLedger.post()` và gọi `requireActiveMaterial()`/`requireActiveProduct()` trước khi post — như `complete` ở đây.
- `created_by` của mọi movement luôn là `users.id` (không phải id bảng profile) — `CurrentUserResolver` đã đảm bảo từ P3.
- Phiếu nhập `COMPLETED` là nguồn chứng từ (`ref_id = purchase_receipts.id`) cho movement `IN`; các phase sau có thể join `stock_movements` với `purchase_receipts` để truy vết nhập kho.

## 8. Xác minh

`mvnw.cmd -B -DskipTests clean compile` → **BUILD SUCCESS** (javac release 21). 11 file gói `mrp.purchase` được sinh tại `target/classes/mrp/purchase/`, `PurchaseServiceTest` tại `target/test-classes/mrp/purchase/`. Phase 4 hoàn thành ở tầng compile; `PurchaseServiceTest` (11 kịch bản Mockito) sẵn sàng chạy qua `mvnw.cmd test -Dtest=PurchaseServiceTest`; kiểm thử E2E (DB + seed) chạy ở giai đoạn tích hợp.


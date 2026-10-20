# Phase 3 – Tồn kho, sổ kho, điều chỉnh (Inventory)

Cập nhật: 2026-10-18. Phần 3 của `docs/BACKEND_E2E_PLAN.md`: tồn kho chỉ đọc, sổ kho append-only và điều chỉnh tồn (chỉ WM).

## 1. Mục tiêu

- Tồn kho chỉ đọc: mọi thay đổi tồn đi qua `INSERT stock_movements`, không bao giờ `UPDATE`/`DELETE` trực tiếp `inventories` hay `stock_movements`.
- Sổ kho có thể lọc theo loại movement, vật tư, khoảng ngày (inclusive) và chứng từ nguồn.
- WM điều chỉnh tồn (`ADJUST_IN`/`ADJUST_OUT`) với `note` bắt buộc; `ADJUST_OUT` vượt tồn → 409 và không để lại movement nào.

## 2. Endpoint

| Phương thức | Đường dẫn | Vai trò | Mô tả |
|---|---|---|---|
| GET | `/api/inventories/materials` | STAFF, WAREHOUSE_MANAGER, ADMIN | Tồn NVL (LEFT JOIN, `quantity=0` khi chưa phát sinh) |
| GET | `/api/inventories/products` | STAFF, WAREHOUSE_MANAGER, ADMIN | Tồn thành phẩm |
| GET | `/api/stock-movements` | STAFF, WAREHOUSE_MANAGER, ADMIN | Sổ kho, lọc + phân trang |
| POST | `/api/stock-adjustments` | WAREHOUSE_MANAGER | Điều chỉnh tồn (IN/OUT) |

Không có endpoint `PUT`/`DELETE` cho movement hay inventories (INV-06): trigger `trg_sm_ro` / `trg_inv_guard` chặn ở DB, không map route → 404/405.

## 3. Kiến trúc

- `Inventory` (`@Immutable`): tồn hiện tại, do trigger tính, app không ghi trực tiếp.
- `StockMovement` (`@Immutable`): dòng sổ kho, ghi qua `StockLedger.post()` dùng `INSERT ... RETURNING`. `created_by` trỏ `users.id`.
- `StockMovementRepository`: chỉ đọc — không khai báo method save/update/delete nào.
- `StockLedger`: cổng ghi / đọc tồn duy nhất. Đọc tồn bằng query mới mỗi lần vì persistence context không biết trigger đã đổi `inventories`.
- `InventoryQuery`: read model `LEFT JOIN inventories`; sort theo whitelist theo entity; `size` 1..200; keyword escape `\ % _`.
- `InventoryService`: truy vấn + lọc `Specification` (no N+1 qua batch `findAllById` cho materials/products/users) + `adjust()`.

## 4. Business logic / ràng buộc

- Điều chỉnh: đúng một trong `materialId`/`productId`; số lượng > 0; thành phẩm phải nguyên [DB ck]; `note` bắt buộc [DB `ck_sm_adjust_note`]; `ADJUST_OUT` vượt tồn → 409 (pre-check ở service + DB CHECK `23514` là safety net cuối).
- Dòng tồn chưa tồn tại do trigger tạo ra ở movement đầu tiên; API coi `quantity = 0`.
- Sau khi insert movement, đọc lại tồn bằng query mới (không dùng giá trị trong persistence context).
- `MethodArgumentTypeMismatchException` (enum `MovementType` hoặc `LocalDate` sai định dạng) → 400, xử lý trong `GlobalExceptionHandler` (đã thêm từ P2).

## 5. File thêm và sửa

### Thêm (Phase 3)

- `inventory/MovementType.java` — enum `IN`/`OUT`/`ADJUST_IN`/`ADJUST_OUT`
- `inventory/Inventory.java` — `@Immutable`
- `inventory/StockMovement.java` — `@Immutable`
- `inventory/StockMovementRepository.java` — chỉ đọc
- `inventory/StockLedger.java` — `INSERT ... RETURNING` + balance
- `inventory/MovementFilter.java` — record bộ lọc
- `inventory/InventoryViews.java` — 4 view record (`MaterialStockView`, `ProductStockView`, `StockMovementView`, `StockAdjustmentView`)
- `inventory/InventoryRequests.java` — adjustment request + validation Bean Validation
- `inventory/InventoryQuery.java` — read model, sort whitelist, pagination, keyword escape
- `inventory/InventoryService.java` — truy vấn + `adjust()`
- `inventory/InventoryController.java` — `GET /materials`, `GET /products`
- `inventory/StockMovementController.java` — `GET` list
- `inventory/StockAdjustmentController.java` — `POST` (WM only)
- `security/CurrentUserResolver.java` — lấy `uid`/`pid` từ JWT
- `security/CurrentUser.java` — record

### Sửa

- `security/JwtAuthenticationConverter.java` — parse claim số (`Integer` vs `Long` theo độ lớn giá trị)

## 6. Ánh xạ test (INV-01..07)

- **INV-01** `ADJUST_IN` có note → tồn tăng đúng, movement ghi `created_by` = `users.id` của WM.
- **INV-02** Thiếu note / note trắng → 400 (hoặc 409 từ DB), tồn không đổi.
- **INV-03** `ADJUST_OUT` vượt tồn → 409, tồn và ledger không đổi.
- **INV-04** Thành phẩm số lẻ → 400; NVL 3 chữ số thập phân → ok.
- **INV-05** ADMIN / STAFF điều chỉnh → 403.
- **INV-06** Không có endpoint sửa / xóa movement hay ghi trực tiếp inventories (PUT/DELETE → 404/405, DB trigger chặn).
- **INV-07** Lọc sổ kho theo loại, vật tư, khoảng ngày, chứng từ nguồn.

## 7. Ghi chú cho phase sau

- P4 / P5 / P8 tạo movement **phải** dùng `StockLedger.post()` (không INSERT trực tiếp) và gọi `CatalogService.requireActiveMaterial()` / `requireActiveProduct()` trước khi tạo movement.
- `created_by` luôn là `users.id` (không phải id bảng profile) — `CurrentUserResolver` đã đảm bảo.

## 8. Xác minh

`mvnw.cmd -B -DskipTests clean compile` → **BUILD SUCCESS** (javac release 21). Phase 3 hoàn thành ở tầng compile; kiểm thử E2E (INV-01..07) chạy khi có database + seed.

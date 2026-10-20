# Phase 5 – Công thức sản phẩm BOM (Bill of Materials)

Cập nhật: 2026-10-20. Phần 5 của `docs/BACKEND_E2E_PLAN.md`: công thức sản phẩm BOM định nghĩa danh sách nguyên vật liệu cần thiết để sản xuất 1 đơn vị thành phẩm (mục 5.1.5).

## 1. Mục tiêu

- Warehouse Manager (WM) tạo công thức BOM cho sản phẩm active với ít nhất 1 dòng nguyên vật liệu (NVL active, không trùng trong cùng BOM, số lượng > 0).
- `version` tự động tăng (`max + 1`) theo từng sản phẩm.
- Quy tắc một BOM active: mỗi sản phẩm chỉ có tối đa 1 BOM active tại một thời điểm (được bảo vệ bởi partial unique index `uq_boms_one_active` trong PostgreSQL). Service tự động vô hiệu hóa BOM active cũ trước khi kích hoạt BOM mới.
- Vòng đời: `activate` (chuyển active = true, tự tắt BOM active khác của sản phẩm đó), `deactivate` (chuyển active = false).
- Không có API sửa / xóa dòng: BOM đã được tham chiếu bởi `production_requests` sẽ bị DB trigger `trg_lock_bom_items` chặn sửa dòng; muốn thay đổi công thức thì tạo version mới.

## 2. Endpoint

| Phương thức | Đường dẫn | Vai trò | Mô tả |
|---|---|---|---|
| POST | `/api/boms` | WAREHOUSE_MANAGER | Tạo BOM mới (mặc định active = true, vô hiệu BOM cũ) |
| GET | `/api/boms` | STAFF, WAREHOUSE_MANAGER, ADMIN | Danh sách BOM, lọc theo `productId`, `active` + phân trang |
| GET | `/api/boms/{id}` | STAFF, WAREHOUSE_MANAGER, ADMIN | Chi tiết BOM + các dòng nguyên vật liệu |
| POST | `/api/boms/{id}/activate` | WAREHOUSE_MANAGER | Kích hoạt BOM (tự tắt BOM active cũ) |
| POST | `/api/boms/{id}/deactivate` | WAREHOUSE_MANAGER | Vô hiệu hóa BOM |

Bộ lọc `GET /api/boms`:
- `productId` (Long, tùy chọn): lọc theo sản phẩm.
- `active` (Boolean, tùy chọn): lọc trạng thái active/inactive.
- `page` (int, mặc định 0), `size` (int, mặc định 20, tối đa 100).
- `sort` (String, whitelist: `id`, `version`, `createdAt`, `productId`, mặc định `id`), `direction` (`asc` / `desc`, mặc định `desc`).

## 3. Kiến trúc

- `Bom` (`@Entity`, bảng `boms`): `id`, `product_id`, `version`, `is_active` (`@Column(name = "is_active")`), `created_at` (`@PrePersist`, `updatable = false`).
- `BomItem` (`@Entity`, bảng `bom_items`): `id` (surrogate PK), `bom_id`, `material_id`, `quantity` (precision 15, scale 3, CHECK > 0).
- `BomRepository`: `JpaRepository` + `JpaSpecificationExecutor`; `findByIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)`) đảm bảo tính toàn vẹn khi activate/deactivate đồng thời; `findMaxVersionByProductId`, `findActiveByProductId`, `deactivateAllByProductId`.
- `BomItemRepository`: `findByBomIdOrderByMaterialIdAsc`, `findByBomIdIn`, `existsByBomId`, `countByBomId`.
- `BomRequests`: DTO record `CreateBomRequest` (`productId` `@NotNull @Positive`, `items` `@NotEmpty @Valid`) và `BomItemRequest` (`materialId` `@NotNull @Positive`, `quantity` `@NotNull @Positive @Digits(12, 3)`).
- `BomViews`: Read models `BomView` (danh sách có kèm tên sản phẩm, đơn vị tính, số lượng dòng NVL), `BomDetailView` (chi tiết BOM và danh sách `BomItemView` kèm mã NVL, tên NVL, đơn vị tính).
- `BomQuery`: Hỗ trợ phân trang và kiểm tra sort whitelist an toàn (`BadRequestException` khi sort trường lạ hoặc size > 100).
- `BomService` (`@Service`, `@Transactional(readOnly = true)`): Xử lý toàn bộ logic nghiệp vụ cho BOM, cung cấp thêm 2 helper quan trọng cho Phase 6:
  - `requireActiveBom(Long productId)`: Lấy BOM active và xác thực BOM có NVL.
  - `getBomItems(Long bomId)`: Lấy danh sách dòng chi tiết.
- `BomController`: REST Controller với `@PreAuthorize` phân quyền rõ ràng theo ma trận quyền hạn.

## 4. Business Logic & Ràng buộc

- **Tạo BOM** (`create`):
  - Sản phẩm phải tồn tại và đang active (`catalogService.requireActiveProduct(productId)`). Nếu không tồn tại -> 404, nếu inactive -> 409.
  - Danh sách NVL không được rỗng (`@NotEmpty` -> 400).
  - Không được có NVL trùng lặp trong cùng 1 BOM (`BusinessException` -> 409).
  - Từng NVL phải tồn tại và đang active (`catalogService.requireActiveMaterial(materialId)`). Nếu không tồn tại -> 404, nếu inactive -> 409.
  - Version mới = `(maxVersion ?: 0) + 1`.
  - Tự động tắt active các BOM cũ của sản phẩm (`deactivateAllByProductId`) rồi mới lưu BOM mới với `active = true` (đảm bảo không vi phạm partial unique index `uq_boms_one_active`).
- **Kích hoạt** (`activate`):
  - Khóa dòng bằng `findByIdForUpdate` (PESSIMISTIC_WRITE).
  - Sản phẩm phải đang active.
  - Tắt active các BOM cũ của cùng sản phẩm rồi đặt `active = true`.
- **Vô hiệu hóa** (`deactivate`):
  - Khóa dòng và đặt `active = false`.
- **Không có N+1**: Endpoint list và detail đều dùng batch fetching (`findAllById`) cho Product và Material.

## 5. Danh sách file thêm mới

### Gói `mrp.bom` (`backend/src/main/java/mrp/bom/`)
- `Bom.java` — Thực thể `boms`
- `BomItem.java` — Thực thể `bom_items`
- `BomRepository.java` — Repository với `findByIdForUpdate` và query theo product
- `BomItemRepository.java` — Repository chi tiết BOM
- `BomRequests.java` — DTO request + validation
- `BomViews.java` — DTO response read models
- `BomQuery.java` — Hỗ trợ pagination & sort whitelist
- `BomService.java` — Service xử lý logic BOM
- `BomController.java` — REST Controller 5 endpoint

### Test (`backend/src/test/java/mrp/bom/`)
- `BomServiceTest.java` — 13 unit test Mockito kiểm thử đầy đủ các kịch bản BOM-01 đến BOM-07 và các helper methods.

## 6. Kết quả kiểm thử

- **13/13 unit tests** trong `BomServiceTest` chạy PASS:
  - `bom01_createTableBomV1Active`: Tạo BOM bàn (1 mặt + 4 chân) -> v1 active, 2 dòng.
  - `bom02_createSecondBomDeactivatesFirstAndIncrementsVersion`: Tạo BOM thứ 2 -> v2 active, v1 inactive.
  - `bom03_duplicateMaterialInBomThrowsBusinessException`: Trùng NVL -> 409.
  - `bom04_productNotFoundThrowsResourceNotFoundException`: Sản phẩm không tồn tại -> 404.
  - `bom05_inactiveProductThrowsBusinessException`: Sản phẩm inactive -> 409.
  - `bom06_inactiveMaterialThrowsBusinessException`: NVL inactive -> 409.
  - `bom07_activateBomDeactivatesCurrentlyActiveBom`: Activate BOM cũ -> BOM đang active tự chuyển inactive.
  - `bom08_activateNonExistentBomThrows404`: Activate BOM lạ -> 404.
  - `bom09_deactivateBomSetsActiveFalse`: Deactivate -> active = false.
  - `bom10_detailNonExistentBomThrows404`: Detail BOM lạ -> 404.
  - `bom11_listWithInvalidSortThrowsBadRequestException`: Sort field không hợp lệ -> 400.
  - `bom12_requireActiveBomThrowsIfNoActiveBom`: Helper ném 409 khi sản phẩm chưa có BOM active.
  - `bom13_requireActiveBomThrowsIfBomHasNoItems`: Helper ném 409 khi BOM rỗng.
- **Toàn bộ 35/35 tests** của hệ thống backend đều PASS.

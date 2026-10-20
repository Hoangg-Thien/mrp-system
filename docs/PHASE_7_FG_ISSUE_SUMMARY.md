# TỔNG KẾT PHASE 7: PHIẾU XUẤT THÀNH PHẨM ĐỂ BÁN (FINISHED GOODS ISSUE REQUESTS)

## 1. Mục tiêu đã hoàn thành
Triển khai toàn bộ quy trình quản lý phiếu xuất thành phẩm (`finished_goods_issue_requests`) theo thiết kế tại `docs/BACKEND_E2E_PLAN.md` (mục 5.1.3 & 5.2.4).
- Cho phép nhân viên kinh doanh / nhân viên (`STAFF`) lập phiếu xuất bán hàng mà không phụ thuộc vào bảng đơn hàng (theo schema MRP rút gọn).
- Thủ kho (`WAREHOUSE_MANAGER`) kiểm duyệt và xuất kho thành phẩm thực tế.
- Đáp ứng đầy đủ các quyết định thiết kế: **D5** (cảnh báo thiếu tồn khi tạo, chặn thật khi duyệt), **D6** (định dạng lý do từ chối `[TỪ CHỐI] <reason>`), **D11** (quyền sở hữu phiếu: STAFF chỉ xem và thao tác trên phiếu của mình, xem phiếu người khác trả 404 để không lộ tồn tại), và **D12** (khóa bi quan `PESSIMISTIC_WRITE` khi thay đổi trạng thái).

---

## 2. Các thành phần đã tạo

Tất cả nằm trong package `mrp.fgissue`:

| File | Vai trò |
|---|---|
| `FgIssueRequestStatus.java` | Enum trạng thái: `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED` |
| `FinishedGoodsIssueRequest.java` | Entity JPA ánh xạ bảng `finished_goods_issue_requests` |
| `FinishedGoodsIssueRequestItem.java` | Entity JPA ánh xạ bảng `finished_goods_issue_request_items` |
| `FinishedGoodsIssueRequestRepository.java` | JpaRepository hỗ trợ `findByIdForUpdate` (PESSIMISTIC_WRITE) và Specification filter |
| `FinishedGoodsIssueRequestItemRepository.java` | JpaRepository hỗ trợ query items theo requestId và requestIdIn |
| `FgIssueRequests.java` | DTOs dạng record: `CreateFgIssueRequest`, `FgIssueItemRequest`, `RejectRequest` kèm Bean Validation |
| `FgIssueViews.java` | DTOs dạng record: `FgIssueRequestView`, `FgIssueRequestDetailView`, `FgIssueItemView` |
| `FgIssueQuery.java` | Helper validate phân trang và whitelist sort (`id`, `requestedAt`, `status`, `requestedBy`) |
| `FgIssueService.java` | Xử lý toàn bộ logic nghiệp vụ (tạo phiếu, duyệt trừ kho, từ chối, hủy, tra cứu danh sách/chi tiết) |
| `FgIssueController.java` | REST API Controller với phân quyền qua `@PreAuthorize` |

---

## 3. Danh sách REST Endpoints

Prefix: `/api/fg-issue-requests`

| Method | Endpoint | Quyền hạn | Mô tả |
|---|---|---|---|
| `POST` | `/api/fg-issue-requests` | `hasRole('STAFF')` | Tạo phiếu xuất thành phẩm mới (kèm cảnh báo nếu thiếu tồn) |
| `GET` | `/api/fg-issue-requests` | `hasAnyRole('STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')` | Danh sách phiếu xuất (STAFF tự động filter theo phiếu của mình) |
| `GET` | `/api/fg-issue-requests/{id}` | `hasAnyRole('STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')` | Chi tiết phiếu xuất (STAFF xem phiếu người khác -> 404) |
| `POST` | `/api/fg-issue-requests/{id}/approve` | `hasRole('WAREHOUSE_MANAGER')` | Duyệt phiếu xuất: kiểm tra tồn kho, trừ tồn thành phẩm qua `StockLedger.post(OUT)` |
| `POST` | `/api/fg-issue-requests/{id}/reject` | `hasRole('WAREHOUSE_MANAGER')` | Từ chối phiếu xuất kèm lý do (`[TỪ CHỐI] <reason>`) |
| `POST` | `/api/fg-issue-requests/{id}/cancel` | `hasRole('STAFF')` | Hủy phiếu xuất (chỉ người tạo được hủy khi phiếu còn `PENDING`) |

---

## 4. Kiểm thử (Unit Tests)

Đã viết bộ test suite toàn diện tại `backend/src/test/java/mrp/fgissue/FgIssueServiceTest.java` với **17 test cases**:

1. **FG-01**: `fg01_createValidRequest_success` – Tạo phiếu hợp lệ -> trạng thái `PENDING`, không phát sinh `stock_movements`.
2. **FG-02**: `fg02_createRequest_duplicateProductInRequest_throwsBusinessException` – Trùng lặp sản phẩm trong phiếu -> ném `BusinessException`.
3. **FG-02b**: `fg02b_createRequest_inactiveProduct_throwsBusinessException` – Sản phẩm không active -> ném `BusinessException`.
4. **FG-03**: `fg03_createRequest_stockShortage_warningReturned` – Xin vượt tồn khả dụng -> tạo thành công với cờ `hasStockShortage = true` và `warning != null`.
5. **FG-04**: `fg04_approve_sufficientStock_statusApprovedAndPostsStockOut` – Duyệt đủ tồn -> `APPROVED`, gọi `StockLedger.post(OUT)` cho từng sản phẩm với user của WM.
6. **FG-04b**: `fg04b_approve_insufficientStock_throwsBusinessException_noStockPost` – Duyệt thiếu tồn ở bất kỳ dòng nào -> ném `BusinessException` (409), phiếu giữ nguyên `PENDING`, không ghi sổ kho.
7. **FG-05**: `fg05_approve_nonPendingRequest_throwsBusinessException` – Duyệt phiếu không ở trạng thái `PENDING` -> 409.
8. **FG-05b**: `fg05b_reject_nonPendingRequest_throwsBusinessException` – Từ chối phiếu không ở trạng thái `PENDING` -> 409.
9. **FG-06**: `fg06_reject_pendingRequest_statusRejectedAndAppendsReason` – Từ chối phiếu `PENDING` -> `REJECTED`, nối `[TỪ CHỐI] <reason>` vào ghi chú.
10. **FG-07**: `fg07_cancel_byRequester_statusCancelled` – Người tạo hủy phiếu khi `PENDING` -> `CANCELLED`.
11. **FG-07b**: `fg07b_cancel_byOtherStaff_throwsResourceNotFound` – Nhân viên khác cố hủy phiếu -> ném `ResourceNotFoundException` (404, theo D11).
12. **FG-07c**: `fg07c_cancel_nonPendingRequest_throwsBusinessException` – Hủy phiếu đã `APPROVED` -> 409.
13. **FG-08**: `fg08_detail_staffViewsOtherStaffSlip_throwsResourceNotFound` – STAFF A xem phiếu của STAFF B -> ném 404 (theo D11).
14. **FG-08b**: `fg08b_detail_wmViewsStaffSlip_succeeds` – WM xem phiếu của STAFF -> thành công.
15. **FG-09**: `fg09_list_staffForcesRequestedByFilter` – Danh sách phiếu của STAFF tự động ép điều kiện `requestedBy = currentStaffId`.
16. **FG-10**: `fg10_query_invalidSortField_throwsBadRequestException` – Sắp xếp theo trường ngoài whitelist -> 400.
17. **FG-10b**: `fg10b_query_invalidDirection_throwsBadRequestException` – Hướng sắp xếp khác `asc`/`desc` -> 400.

### Kết quả chạy kiểm thử:
- `FgIssueServiceTest`: **17/17 PASS** (0 failures, 0 errors).
- Toàn bộ backend test suite: **72/72 PASS** (0 failures, 0 errors, 0 skipped).

---

## 5. Sẵn sàng bàn giao cho Phase 8
Phase 7 đã hoàn tất 100%. Tiếp theo là **Phase 8: Báo cáo kho (5.1.4)** (`GET /api/reports/stock?kind=MATERIAL|PRODUCT&granularity=MONTH|QUARTER|YEAR&year=&period=`).

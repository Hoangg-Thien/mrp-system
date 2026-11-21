package mrp.purchase.entity;

/**
 * Trạng thái của {@code purchase_receipts.status} [DB {@code ck_pr_status}].
 * <ul>
 *   <li>{@link #PENDING} — mới tạo, còn sửa dòng được (trigger {@code trg_freeze_items} cho phép).</li>
 *   <li>{@link #COMPLETED} — đã nhập kho; trigger {@code fn_sm_validate} yêu cầu trạng thái này khi
 *       ghi {@code stock_movements} từ phiếu nhập.</li>
 *   <li>{@link #CANCELLED} — hủy từ PENDING, không ảnh hưởng tồn.</li>
 * </ul>
 * COMPLETED / CANCELLED là trạng thái cuối: trigger {@code trg_final_immutable} chặn UPDATE phiếu,
 * {@code trg_freeze_items} chặn INSERT/UPDATE/DELETE dòng → sửa sau khi final trả 409.
 */
public enum PurchaseReceiptStatus { PENDING, COMPLETED, CANCELLED }
package mrp.inventory;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Cổng ghi / đọc tồn kho duy nhất.
 * <ul>
 *   <li>Ghi: INSERT {@code stock_movements} (append-only), trigger {@code trg_sm_apply} tự tạo dòng
 *       {@code inventories} nếu chưa có rồi cộng / trừ tồn, {@code trg_sm_validate} kiểm tra chứng từ
 *       nguồn và role của {@code created_by}.</li>
 *   <li>Đọc tồn: query mới mỗi lần vì persistence context không biết trigger đã đổi {@code inventories}.</li>
 * </ul>
 * Tên cột trong các câu SQL dưới đây là hằng số của ứng dụng, không nhận từ request.
 */
@Component
public class StockLedger {
    private static final String INSERT_MOVEMENT = """
            insert into stock_movements (material_id, product_id, movement_type, quantity,
                purchase_receipt_id, material_issue_request_id, production_id, fg_issue_request_id, created_by, note)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            returning id, created_at
            """;
    private final JdbcTemplate jdbc;
    public StockLedger(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public BigDecimal materialBalance(Long materialId) { return balance("material_id", materialId); }
    public BigDecimal productBalance(Long productId) { return balance("product_id", productId); }
    /** Đúng một trong hai id khác null; dòng tồn chưa có được coi là 0. */
    public BigDecimal balanceOf(Long materialId, Long productId) {
        return materialId != null ? materialBalance(materialId) : productBalance(productId);
    }
    private BigDecimal balance(String column, Long id) {
        BigDecimal value = jdbc.queryForObject(
                "select coalesce(sum(quantity), 0) from inventories where " + column + " = ?", BigDecimal.class, id);
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Điều chỉnh tồn (WM): không gắn chứng từ nguồn theo {@code ck_sm_source}, note bắt buộc. */
    public PostedMovement postAdjustment(MovementType type, Long materialId, Long productId, BigDecimal quantity,
                                         Long createdBy, String note) {
        return post(type, materialId, productId, quantity, null, null, null, null, createdBy, note);
    }

    public PostedMovement post(MovementType type, Long materialId, Long productId, BigDecimal quantity,
                               Long purchaseReceiptId, Long materialIssueRequestId, Long productionId,
                               Long fgIssueRequestId, Long createdBy, String note) {
        return jdbc.query(INSERT_MOVEMENT, rs -> {
            if (!rs.next()) throw new IllegalStateException("Không ghi được sổ kho");
            Timestamp createdAt = rs.getTimestamp("created_at");
            return new PostedMovement(rs.getLong("id"), createdAt == null ? null : createdAt.toLocalDateTime());
        }, materialId, productId, type.name(), quantity, purchaseReceiptId, materialIssueRequestId,
                productionId, fgIssueRequestId, createdBy, note);
    }

    public record PostedMovement(long id, LocalDateTime createdAt) { }
}

package mrp.catalog.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Table/column names below are fixed application constants, never request input. */
@Component
public class CatalogReferenceChecker {
    private final JdbcTemplate jdbc;
    public CatalogReferenceChecker(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private boolean exists(String table, String column, Long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from " + table + " where " + column + " = ?)", Boolean.class, id));
    }
    public boolean category(Long id) { return exists("products", "category_id", id); }
    public boolean supplier(Long id) { return exists("purchase_receipts", "supplier_id", id); }
    public boolean materialUnitLocked(Long id) {
        return exists("stock_movements", "material_id", id) || exists("bom_items", "material_id", id);
    }
    public boolean material(Long id) {
        return materialUnitLocked(id) || exists("purchase_receipt_items", "material_id", id)
                || exists("inventories", "material_id", id) || exists("material_issue_request_items", "material_id", id);
    }
    public boolean product(Long id) {
        return exists("boms", "product_id", id) || exists("production_requests", "product_id", id)
                || exists("inventories", "product_id", id) || exists("stock_movements", "product_id", id)
                || exists("finished_goods_issue_request_items", "product_id", id);
    }
}
package mrp.inventory.dto;



import static mrp.inventory.dto.InventoryViews.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import mrp.common.exception.BadRequestException;

/**
 * Read model tồn kho (INV-02, INV-03): LEFT JOIN {@code inventories} để mọi vật tư / sản phẩm đều
 * xuất hiện, dòng tồn chưa phát sinh thì {@code quantity} = 0 (trigger tạo dòng khi có movement đầu tiên).
 * Sort theo whitelist, phân trang chuẩn P2; giá trị đều bind qua named parameter.
 */
@Component
public class InventoryQuery {
    private static final Map<String, String> MATERIAL_SORT = Map.of(
            "id", "m.id", "code", "m.code", "name", "m.name",
            "quantity", "coalesce(i.quantity, 0)", "updatedAt", "i.updated_at");
    private static final Map<String, String> PRODUCT_SORT = Map.of(
            "id", "p.id", "name", "p.name", "quantity", "coalesce(i.quantity, 0)", "updatedAt", "i.updated_at");

    private final NamedParameterJdbcTemplate jdbc;
    public InventoryQuery(JdbcTemplate jdbcTemplate) { this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate); }

    public Page<MaterialStockView> materials(String keyword, boolean positiveOnly, Boolean active,
                                      int page, int size, String sort, String direction) {
        PageRequest pageable = page(page, size, sort, direction, MATERIAL_SORT.keySet());
        String dir = direction(pageable, sort);
        String orderBy = "order by " + MATERIAL_SORT.get(sort) + " " + dir + ("id".equals(sort) ? "" : ", m.id " + dir);
        StringBuilder where = new StringBuilder();
        Map<String, Object> args = new HashMap<>();
        String pattern = like(keyword);
        if (pattern != null) {
            args.put("keyword", pattern);
            where.append(" and (lower(m.code) like :keyword escape '\\' or lower(m.name) like :keyword escape '\\')");
        }
        if (positiveOnly) where.append(" and coalesce(i.quantity, 0) > 0");
        if (active != null) { args.put("active", active); where.append(" and m.is_active = :active"); }
        String from = "from materials m left join inventories i on i.material_id = m.id where 1 = 1" + where;
        args.put("limit", size);
        args.put("offset", (long) page * size);
        List<MaterialStockView> rows = jdbc.query("select m.id as material_id, m.code, m.name, m.unit, m.is_active,"
                        + " coalesce(i.quantity, 0) as quantity, i.id as inventory_id, i.updated_at "
                        + from + " " + orderBy + " limit :limit offset :offset",
                args, InventoryQuery::materialRow);
        return new PageImpl<>(rows, pageable, count(from, args));
    }

    public Page<ProductStockView> products(String keyword, Long categoryId, boolean positiveOnly, Boolean active,
                                    int page, int size, String sort, String direction) {
        PageRequest pageable = page(page, size, sort, direction, PRODUCT_SORT.keySet());
        String dir = direction(pageable, sort);
        String orderBy = "order by " + PRODUCT_SORT.get(sort) + " " + dir + ("id".equals(sort) ? "" : ", p.id " + dir);
        StringBuilder where = new StringBuilder();
        Map<String, Object> args = new HashMap<>();
        String pattern = like(keyword);
        if (pattern != null) { args.put("keyword", pattern); where.append(" and lower(p.name) like :keyword escape '\\'"); }
        if (categoryId != null) { args.put("categoryId", categoryId); where.append(" and p.category_id = :categoryId"); }
        if (positiveOnly) where.append(" and coalesce(i.quantity, 0) > 0");
        if (active != null) { args.put("active", active); where.append(" and p.is_active = :active"); }
        String from = "from products p left join categories c on c.id = p.category_id"
                + " left join inventories i on i.product_id = p.id where 1 = 1" + where;
        args.put("limit", size);
        args.put("offset", (long) page * size);
        List<ProductStockView> rows = jdbc.query("select p.id as product_id, p.name, p.unit, p.is_active,"
                        + " p.category_id, c.name as category_name, coalesce(i.quantity, 0) as quantity,"
                        + " i.id as inventory_id, i.updated_at " + from + " " + orderBy + " limit :limit offset :offset",
                args, InventoryQuery::productRow);
        return new PageImpl<>(rows, pageable, count(from, args));
    }

    private long count(String from, Map<String, Object> args) {
        Long total = jdbc.queryForObject("select count(*) " + from, args, Long.class);
        return total == null ? 0 : total;
    }

    private static String direction(PageRequest pageable, String sort) {
        Sort.Order order = pageable.getSort().getOrderFor(sort);
        return order != null && order.isDescending() ? "desc" : "asc";
    }

    private static MaterialStockView materialRow(ResultSet rs, int rowNum) throws SQLException {
        return new MaterialStockView(rs.getLong("material_id"), rs.getString("code"), rs.getString("name"),
                rs.getString("unit"), rs.getBoolean("is_active"), rs.getBigDecimal("quantity"),
                nullableLong(rs, "inventory_id"), nullableTime(rs, "updated_at"));
    }

    private static ProductStockView productRow(ResultSet rs, int rowNum) throws SQLException {
        return new ProductStockView(rs.getLong("product_id"), rs.getString("name"), rs.getString("unit"),
                rs.getBoolean("is_active"), nullableLong(rs, "category_id"), rs.getString("category_name"),
                rs.getBigDecimal("quantity"), nullableLong(rs, "inventory_id"), nullableTime(rs, "updated_at"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime nullableTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    static String like(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        return "%" + keyword.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    public static PageRequest page(int page, int size, String sort, String direction, Set<String> allowedSort) {
        if (page < 0) throw new BadRequestException("Page phải >= 0");
        if (size < 1 || size > 200) throw new BadRequestException("Size phải trong khoảng 1..200");
        if (sort == null || !allowedSort.contains(sort)) {
            throw new BadRequestException("sort không hợp lệ: " + sort + ". Cho phép: " + new ArrayList<>(allowedSort));
        }
        if (!"asc".equalsIgnoreCase(direction) && !"desc".equalsIgnoreCase(direction)) {
            throw new BadRequestException("direction phải là asc hoặc desc");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(direction), sort));
    }
}
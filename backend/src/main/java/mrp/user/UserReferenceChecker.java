package mrp.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Checks references before a profile is deleted or its role is changed. */
@Component
public class UserReferenceChecker {
    private final JdbcTemplate jdbc;

    public UserReferenceChecker(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean isReferenced(User user, Long profileId) {
        if (user.getRole() == UserRole.ADMIN) {
            return count("select count(*) from stock_movements where created_by = ?", user.getId()) > 0;
        }
        if (user.getRole() == UserRole.STAFF) {
            return count("select count(*) from production_requests where assigned_to = ?", profileId) > 0
                    || count("select count(*) from material_issue_requests where requested_by = ?", profileId) > 0
                    || count("select count(*) from productions where produced_by = ?", profileId) > 0
                    || count("select count(*) from finished_goods_issue_requests where requested_by = ?", profileId) > 0;
        }
        return count("select count(*) from purchase_receipts where warehouse_manager_id = ?", profileId) > 0
                || count("select count(*) from production_requests where warehouse_manager_id = ?", profileId) > 0
                || count("select count(*) from material_issue_requests where reviewed_by = ?", profileId) > 0
                || count("select count(*) from finished_goods_issue_requests where reviewed_by = ?", profileId) > 0;
    }

    private long count(String sql, Long id) { return jdbc.queryForObject(sql, Long.class, id); }
}
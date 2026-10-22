package mrp.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import javax.sql.DataSource;
import mrp.production.dto.ProductionViews;
import mrp.production.service.MrpCalculator;
import mrp.production.service.ProductionService;
import mrp.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(ProductionPlanQueryPerformanceTest.QueryCounterConfig.class)
class ProductionPlanQueryPerformanceTest extends AbstractPostgresIntegrationTest {

    @TestConfiguration
    static class QueryCounterConfig {
        @Bean
        public static BeanPostProcessor dataSourceCounterPostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                    if (bean instanceof DataSource ds && !(Proxy.isProxyClass(bean.getClass()))) {
                        return QueryCountHelper.wrap(ds);
                    }
                    return bean;
                }
            };
        }
    }

    @Autowired
    private ProductionService productionService;

    @Autowired
    private JdbcTemplate jdbc;

    private static final CurrentUser WM_ACTOR = new CurrentUser(3L, 1L, "wm1");
    private static final Long PRODUCT_ID = 100L;
    private static final Long BOM_ID = 100L;
    private static final int BOM_ITEM_COUNT = 20;
    private static final int OPEN_PR_COUNT = 50;

    @BeforeEach
    void seedBenchmarkDataset() {
        // Truncate existing data
        jdbc.execute("""
            TRUNCATE TABLE
                stock_movements,
                inventories,
                finished_goods_issue_request_items,
                finished_goods_issue_requests,
                material_issue_request_items,
                material_issue_requests,
                productions,
                production_requests,
                bom_items,
                boms,
                products,
                materials,
                suppliers,
                categories,
                staff,
                warehouse_managers,
                admins,
                users
            RESTART IDENTITY CASCADE
        """);

        // Seed users
        jdbc.update("INSERT INTO users (id, username, password_hash, role, is_active) VALUES (1, 'admin1', 'hash', 'ADMIN', true)");
        jdbc.update("INSERT INTO users (id, username, password_hash, role, is_active) VALUES (2, 'staff1', 'hash', 'STAFF', true)");
        jdbc.update("INSERT INTO users (id, username, password_hash, role, is_active) VALUES (3, 'wm1', 'hash', 'WAREHOUSE_MANAGER', true)");

        jdbc.update("INSERT INTO staff (id, user_id, full_name, email) VALUES (1, 2, 'Staff One', 'staff@mrp.local')");
        jdbc.update("INSERT INTO warehouse_managers (id, user_id, full_name, email) VALUES (1, 3, 'WM One', 'wm@mrp.local')");

        // Seed Category & Product
        jdbc.update("INSERT INTO categories (id, name, description) VALUES (1, 'Nội thất', 'Mô tả')");
        jdbc.update("INSERT INTO products (id, category_id, name, unit, selling_price, is_active) VALUES (?, 1, 'Bàn giám đốc', 'cái', 5000000, true)", PRODUCT_ID);

        // Seed 1 BOM with 20 BOM items
        jdbc.update("INSERT INTO boms (id, product_id, version, is_active) VALUES (?, ?, 1, true)", BOM_ID, PRODUCT_ID);

        // Seed 20 Materials and 20 BOM Items
        for (int i = 1; i <= BOM_ITEM_COUNT; i++) {
            long matId = 100L + i;
            jdbc.update("INSERT INTO materials (id, code, name, unit, standard_cost, is_active) VALUES (?, ?, ?, 'cái', 50000, true)",
                    matId, "MAT_" + i, "Vật tư #" + i);
            jdbc.update("INSERT INTO bom_items (bom_id, material_id, quantity) VALUES (?, ?, ?)",
                    BOM_ID, matId, new BigDecimal("1.500"));

            // Seed initial on-hand stock: 50.000 for each material via stock_movements
            jdbc.update("""
                INSERT INTO stock_movements (material_id, movement_type, quantity, created_by, note)
                VALUES (?, 'ADJUST_IN', 50.000, 3, 'Seed material stock')
            """, matId);
        }

        // Seed on-hand for finished product: 10.000 via stock_movements
        jdbc.update("""
            INSERT INTO stock_movements (product_id, movement_type, quantity, created_by, note)
            VALUES (?, 'ADJUST_IN', 10.000, 3, 'Seed product stock')
        """, PRODUCT_ID);

        // Seed 50 open Production Requests for this product
        for (int i = 1; i <= OPEN_PR_COUNT; i++) {
            long prId = 200L + i;
            String status = (i % 2 == 0) ? "IN_PROGRESS" : "PENDING";
            jdbc.update("INSERT INTO production_requests (id, product_id, bom_id, quantity, warehouse_manager_id, assigned_to, status) VALUES (?, ?, ?, 10, 1, 1, ?)",
                    prId, PRODUCT_ID, BOM_ID, status);
        }

        // Seed some approved issue requests to test approvedIssued subtraction
        // PR #201: Material #101 issued 5 units (out of 1.5 * 10 = 15 required)
        jdbc.update("INSERT INTO material_issue_requests (id, production_request_id, requested_by, status) VALUES (501, 201, 1, 'PENDING')");
        jdbc.update("INSERT INTO material_issue_request_items (id, request_id, material_id, quantity) VALUES (601, 501, 101, 5.000)");
        jdbc.update("UPDATE material_issue_requests SET status = 'APPROVED', reviewed_by = 1, reviewed_at = CURRENT_TIMESTAMP WHERE id = 501");

        // PR #202: Material #102 issued 10 units (out of 1.5 * 10 = 15 required)
        jdbc.update("INSERT INTO material_issue_requests (id, production_request_id, requested_by, status) VALUES (502, 202, 1, 'PENDING')");
        jdbc.update("INSERT INTO material_issue_request_items (id, request_id, material_id, quantity) VALUES (602, 502, 102, 10.000)");
        jdbc.update("UPDATE material_issue_requests SET status = 'APPROVED', reviewed_by = 1, reviewed_at = CURRENT_TIMESTAMP WHERE id = 502");
    }

    @Test
    @DisplayName("Characterization test: lock in current outputs of getPlan")
    void testGetPlanOutputs() {
        MrpCalculator.PlanResult plan = productionService.getPlan(PRODUCT_ID, 20);

        assertNotNull(plan);
        assertEquals(PRODUCT_ID, plan.productId());
        assertEquals(20, plan.targetQuantity());
        assertEquals(0, plan.fgOnHand().compareTo(new BigDecimal("10")));
        assertEquals(0, plan.fgReserved().compareTo(BigDecimal.ZERO));
        assertEquals(0, plan.fgIncoming().compareTo(new BigDecimal("500")));
        assertEquals(0, plan.toProduce());
        assertEquals(BOM_ITEM_COUNT, plan.materials().size());

        // Check Material #101 committed:
        // Total open PRs = 50, each requires 1.5 * 10 = 15.000.
        // For PR #201, 5.000 already approved issued => remaining = 10.000.
        // For other 49 PRs, remaining = 15.000 each.
        // Total committed for Material #101 = 10.000 + 49 * 15.000 = 745.000!
        MrpCalculator.MaterialPlanResult mat101 = plan.materials().stream()
                .filter(m -> m.materialId().equals(101L)).findFirst().orElseThrow();
        assertEquals(new BigDecimal("745.000"), mat101.committed(), "Material 101 committed must be exactly 745.000");

        // Check Material #102 committed:
        // For PR #202, 10.000 approved issued => remaining = 5.000.
        // Total committed for Material #102 = 5.000 + 49 * 15.000 = 740.000!
        MrpCalculator.MaterialPlanResult mat102 = plan.materials().stream()
                .filter(m -> m.materialId().equals(102L)).findFirst().orElseThrow();
        assertEquals(new BigDecimal("740.000"), mat102.committed(), "Material 102 committed must be exactly 740.000");

        // Check Material #103 committed:
        // 50 * 15.000 = 750.000
        MrpCalculator.MaterialPlanResult mat103 = plan.materials().stream()
                .filter(m -> m.materialId().equals(103L)).findFirst().orElseThrow();
        assertEquals(new BigDecimal("750.000"), mat103.committed(), "Material 103 committed must be exactly 750.000");
    }

    @Test
    @DisplayName("Characterization test: lock in current outputs of getRequirements")
    void testGetRequirementsOutputs() {
        ProductionViews.RequirementView req = productionService.getRequirements(201L, WM_ACTOR);

        assertNotNull(req);
        assertEquals(201L, req.productionRequestId());
        assertEquals(PRODUCT_ID, req.productId());
        assertEquals(10, req.quantity());
        assertEquals(BOM_ITEM_COUNT, req.materials().size());

        // For item 101 on PR #201:
        // totalRequired = 1.5 * 10 = 15.000
        // approvedIssued = 5.000
        // pendingIssued = 0.000
        // remainingAllowed = 15.000 - 5.000 = 10.000
        // committedOther (for other 49 PRs) = 49 * 15.000 = 735.000
        ProductionViews.RequirementItemView item101 = req.materials().stream()
                .filter(i -> i.materialId().equals(101L)).findFirst().orElseThrow();
        assertEquals(0, item101.totalRequired().compareTo(new BigDecimal("15")));
        assertEquals(0, item101.approvedQuantity().compareTo(new BigDecimal("5")));
        assertEquals(0, item101.pendingQuantity().compareTo(BigDecimal.ZERO));
        assertEquals(0, item101.remainingAllowed().compareTo(new BigDecimal("10")));
        assertEquals(0, item101.committedOther().compareTo(new BigDecimal("735")));
    }

    @Test
    @DisplayName("Statement Counter Benchmark for getPlan")
    void measureGetPlanStatements() {
        QueryCountHelper.reset();
        MrpCalculator.PlanResult plan = productionService.getPlan(PRODUCT_ID, 20);
        assertNotNull(plan);
        int statements = QueryCountHelper.getCount();
        System.out.println("=== STATEMENT COUNT FOR getPlan: " + statements + " ===");
        assertTrue(statements > 0);
    }
}

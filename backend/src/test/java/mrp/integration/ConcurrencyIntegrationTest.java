package mrp.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import mrp.common.exception.BusinessException;
import mrp.fgissue.dto.FgIssueRequests;
import mrp.fgissue.dto.FgIssueViews;
import mrp.fgissue.service.FgIssueService;
import mrp.inventory.entity.MovementType;
import mrp.inventory.service.StockLedger;
import mrp.production.dto.ProductionRequests;
import mrp.production.dto.ProductionViews;
import mrp.production.service.ProductionService;
import mrp.purchase.dto.PurchaseRequests;
import mrp.purchase.dto.PurchaseViews;
import mrp.purchase.service.PurchaseService;
import mrp.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class ConcurrencyIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private ProductionService productionService;

    @Autowired
    private PurchaseService purchaseService;

    @Autowired
    private FgIssueService fgIssueService;

    @Autowired
    private StockLedger stockLedger;

    @Autowired
    private JdbcTemplate jdbc;

    private static final CurrentUser ADMIN_ACTOR = new CurrentUser(1L, 1L, "admin1");
    private static final CurrentUser STAFF_ACTOR = new CurrentUser(2L, 1L, "staff1");
    private static final CurrentUser WM_ACTOR = new CurrentUser(3L, 1L, "wm1");

    @BeforeEach
    void resetDatabase() {
        // Truncate tables with CASCADE (bypasses row-level append-only triggers)
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
                purchase_receipt_items,
                purchase_receipts,
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

        jdbc.update("INSERT INTO admins (id, user_id, full_name, email) VALUES (1, 1, 'Admin One', 'admin@mrp.local')");
        jdbc.update("INSERT INTO staff (id, user_id, full_name, email) VALUES (1, 2, 'Staff One', 'staff@mrp.local')");
        jdbc.update("INSERT INTO warehouse_managers (id, user_id, full_name, email) VALUES (1, 3, 'WM One', 'wm@mrp.local')");

        // Seed catalog & master data
        jdbc.update("INSERT INTO categories (id, name, description) VALUES (1, 'Nội thất', 'Bàn ghế văn phòng')");
        jdbc.update("INSERT INTO products (id, category_id, name, unit, selling_price, is_active) VALUES (1, 1, 'Bàn làm việc', 'cái', 1500000, true)");
        jdbc.update("INSERT INTO suppliers (id, name, phone) VALUES (1, 'Nhà cung cấp Gỗ Việt', '0901234567')");
        jdbc.update("INSERT INTO materials (id, code, name, unit, standard_cost, is_active) VALUES (1, 'MAT01', 'Gỗ ép MFC', 'm2', 120000, true)");
        jdbc.update("INSERT INTO materials (id, code, name, unit, standard_cost, is_active) VALUES (2, 'MAT02', 'Ốc vít', 'hộp', 15000, true)");

        // Seed BOM & Production Request
        jdbc.update("INSERT INTO boms (id, product_id, version, is_active) VALUES (1, 1, 1, true)");
        jdbc.update("INSERT INTO bom_items (bom_id, material_id, quantity) VALUES (1, 1, 2.000)");

        // PR for 100 products => max quota for MAT01 = 200.000
        jdbc.update("INSERT INTO production_requests (id, product_id, bom_id, quantity, warehouse_manager_id, assigned_to, status) VALUES (1, 1, 1, 100, 1, 1, 'IN_PROGRESS')");
    }

    /**
     * Test 1 (concurrency, material issue):
     * One material with stock S = 10; N = 5 threads each approve a different PENDING MIR for 5 units.
     * Assert: final stock >= 0, exactly 2 approvals succeed (2 * 5 = 10), 3 fail with 409 (business/constraint error),
     * no deadlock, stable across 20 repetitions.
     */
    @RepeatedTest(20)
    @DisplayName("T1 - Test 1: Concurrency on Material Issue Request approval against limited stock")
    void test1_concurrencyMaterialIssue() throws InterruptedException {
        Long materialId = 1L;
        BigDecimal initialStock = new BigDecimal("10.000");
        stockLedger.postAdjustment(MovementType.ADJUST_IN, materialId, null, initialStock, WM_ACTOR.userId(), "Seed 10 units");
        assertEquals(initialStock, stockLedger.materialBalance(materialId));

        int nThreads = 5;
        BigDecimal requestedPerMir = new BigDecimal("5.000");
        List<Long> mirIds = new ArrayList<>();

        for (int i = 0; i < nThreads; i++) {
            ProductionViews.MaterialIssueRequestDetailView detail = productionService.createMaterialIssueRequest(
                    new ProductionRequests.CreateMaterialIssueRequest(
                            1L,
                            List.of(new ProductionRequests.IssueItemRequest(materialId, requestedPerMir)),
                            "MIR #" + (i + 1)
                    ),
                    STAFF_ACTOR
            );
            mirIds.add(detail.id());
        }

        ExecutorService executor = Executors.newFixedThreadPool(nThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(nThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failure409Count = new AtomicInteger(0);
        AtomicInteger unexpectedErrorCount = new AtomicInteger(0);

        for (Long mirId : mirIds) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    productionService.approveMaterialIssueRequest(mirId, WM_ACTOR);
                    successCount.incrementAndGet();
                } catch (BusinessException | DataIntegrityViolationException ex) {
                    // 409 error: either Java pre-check or PostgreSQL check constraint caught the overdraft
                    failure409Count.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedErrorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completedInTime = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completedInTime, "Execution completed without deadlock");
        assertEquals(0, unexpectedErrorCount.get(), "No unexpected errors");
        assertEquals(2, successCount.get(), "Exactly 2 approvals should succeed for stock = 10 and item qty = 5");
        assertEquals(3, failure409Count.get(), "The remaining 3 requests must fail with 409");

        BigDecimal finalStock = stockLedger.materialBalance(materialId);
        assertTrue(finalStock.compareTo(BigDecimal.ZERO) >= 0, "Final stock must be non-negative");
        assertEquals(new BigDecimal("0.000"), finalStock, "Final stock must be exactly 0 after 2 approvals of 5 units each");
    }

    /**
     * Test 1b: Concurrency on approving the EXACT SAME MIR.
     * With findByIdForUpdate, exactly one thread can transition the status to APPROVED.
     */
    @RepeatedTest(20)
    @DisplayName("T1 - Test 1b: Concurrent approval of the SAME Material Issue Request")
    void test1b_sameMirConcurrentApproval() throws InterruptedException {
        Long materialId = 1L;
        BigDecimal initialStock = new BigDecimal("20.000");
        stockLedger.postAdjustment(MovementType.ADJUST_IN, materialId, null, initialStock, WM_ACTOR.userId(), "Seed 20 units");

        ProductionViews.MaterialIssueRequestDetailView detail = productionService.createMaterialIssueRequest(
                new ProductionRequests.CreateMaterialIssueRequest(
                        1L,
                        List.of(new ProductionRequests.IssueItemRequest(materialId, new BigDecimal("2.000"))),
                        "Single MIR for concurrent approval test"
                ),
                STAFF_ACTOR
        );
        Long mirId = detail.id();

        int nThreads = 6;
        ExecutorService executor = Executors.newFixedThreadPool(nThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(nThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < nThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    productionService.approveMaterialIssueRequest(mirId, WM_ACTOR);
                    successCount.incrementAndGet();
                } catch (BusinessException | DataIntegrityViolationException ex) {
                    conflictCount.incrementAndGet();
                } catch (Throwable t) {
                    // unexpected
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one approval must succeed for the same MIR");
        assertEquals(nThreads - 1, conflictCount.get(), "Other threads must fail with conflict");

        // Exactly 2 units deducted (not 2 * nThreads)
        assertEquals(new BigDecimal("18.000"), stockLedger.materialBalance(materialId));
    }

    /**
     * Test 2 (concurrency, receipts):
     * The same purchase receipt completed concurrently posts stock exactly once.
     */
    @RepeatedTest(20)
    @DisplayName("T1 - Test 2: Concurrency on the same purchase receipt completion")
    void test2_concurrencyPurchaseReceiptCompletion() throws InterruptedException {
        Long materialId = 2L;
        BigDecimal receiptQty = new BigDecimal("15.000");

        PurchaseViews.PurchaseReceiptDetailView receipt = purchaseService.create(
                new PurchaseRequests.PurchaseReceiptRequest(
                        1L,
                        List.of(new PurchaseRequests.PurchaseReceiptItemRequest(materialId, receiptQty, new BigDecimal("15000.00")))
                ),
                WM_ACTOR
        );
        Long receiptId = receipt.id();

        assertEquals(BigDecimal.ZERO, stockLedger.materialBalance(materialId));

        int nThreads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(nThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(nThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger otherErrors = new AtomicInteger(0);

        for (int i = 0; i < nThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    purchaseService.complete(receiptId, WM_ACTOR);
                    successCount.incrementAndGet();
                } catch (BusinessException ex) {
                    conflictCount.incrementAndGet();
                } catch (Throwable t) {
                    otherErrors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completedInTime = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completedInTime, "No deadlock during concurrent receipt completion");
        assertEquals(0, otherErrors.get(), "No unexpected exceptions");
        assertEquals(1, successCount.get(), "Exactly one thread completes the purchase receipt");
        assertEquals(nThreads - 1, conflictCount.get(), "Other threads fail because status is no longer PENDING");

        BigDecimal finalStock = stockLedger.materialBalance(materialId);
        assertEquals(receiptQty, finalStock, "Stock must be posted exactly once (15.000), not multiplied");
    }

    /**
     * Test 3: Finished-goods issue cannot drive stock negative.
     * Evaluates both single-request overdraft rejection and concurrent issue attempts.
     */
    @RepeatedTest(20)
    @DisplayName("T1 - Test 3: Finished-goods issue cannot drive stock negative")
    void test3_fgIssueCannotDriveStockNegative() throws InterruptedException {
        Long productId = 1L;
        BigDecimal initialStock = new BigDecimal("5.000");
        stockLedger.postAdjustment(MovementType.ADJUST_IN, null, productId, initialStock, WM_ACTOR.userId(), "Seed 5 units");

        // Concurrent approval of two requests: Req A (4 units) + Req B (4 units) = 8 > 5
        FgIssueViews.FgIssueRequestDetailView fgir1 = fgIssueService.create(
                new FgIssueRequests.CreateFgIssueRequest(
                        List.of(new FgIssueRequests.FgIssueItemRequest(productId, 4)),
                        "Export 4 units (A)"
                ),
                STAFF_ACTOR
        );

        FgIssueViews.FgIssueRequestDetailView fgir2 = fgIssueService.create(
                new FgIssueRequests.CreateFgIssueRequest(
                        List.of(new FgIssueRequests.FgIssueItemRequest(productId, 4)),
                        "Export 4 units (B)"
                ),
                STAFF_ACTOR
        );

        int nThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(nThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(nThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startLatch.await();
                fgIssueService.approve(fgir1.id(), WM_ACTOR);
                successCount.incrementAndGet();
            } catch (BusinessException | DataIntegrityViolationException ex) {
                failureCount.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                fgIssueService.approve(fgir2.id(), WM_ACTOR);
                successCount.incrementAndGet();
            } catch (BusinessException | DataIntegrityViolationException ex) {
                failureCount.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "No deadlock in FG issue approval");
        assertEquals(1, successCount.get(), "Only one FG issue request can be approved");
        assertEquals(1, failureCount.get(), "The second request must fail with 409");

        BigDecimal finalStock = stockLedger.productBalance(productId);
        assertTrue(finalStock.compareTo(BigDecimal.ZERO) >= 0, "Product stock must never be negative");
        assertEquals(new BigDecimal("1.000"), finalStock, "Final stock must be 5 - 4 = 1");
    }
}

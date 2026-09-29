-- =====================================================================
-- Maison Interior MRP - PostgreSQL Database Integrity Test Suite
-- Target: PostgreSQL 17 (also compatible with PostgreSQL >= 11)
--
-- PURPOSE
--   Runtime integrity test for the already-installed MRP schema.
--   The suite intentionally tries valid and invalid operations.
--
-- IMPORTANT
--   - Run this whole file in the SAME database where mrp_final(1).sql was run.
--   - Run it once, from top to bottom.
--   - Test data is rolled back at the end.
--   - Sequences may advance; that is harmless.
--   - The suite NEVER raises a final exception just to report failures.
--     It prints the complete result table first, then ROLLBACKs.
-- =====================================================================

BEGIN;

CREATE TEMP TABLE _mrp_test_ids (
    k TEXT PRIMARY KEY,
    v BIGINT NOT NULL
) ON COMMIT DROP;

CREATE TEMP TABLE _mrp_test_results (
    test_no INTEGER PRIMARY KEY,
    test_name TEXT NOT NULL,
    expected TEXT NOT NULL,
    result TEXT NOT NULL,
    detail TEXT
) ON COMMIT DROP;

-- =====================================================================
-- Test helpers
-- =====================================================================

-- Execute SQL that MUST fail. Any PostgreSQL error = PASS.
-- If the statement succeeds, the test is recorded as FAIL.
CREATE OR REPLACE FUNCTION pg_temp.assert_error(
    p_no INTEGER,
    p_name TEXT,
    p_sql TEXT
) RETURNS VOID
LANGUAGE plpgsql AS $$
DECLARE
    v_state TEXT;
    v_msg   TEXT;
BEGIN
    BEGIN
        EXECUTE p_sql;
    EXCEPTION WHEN OTHERS THEN
        v_state := SQLSTATE;
        v_msg := SQLERRM;
        INSERT INTO _mrp_test_results(test_no,test_name,expected,result,detail)
        VALUES (p_no,p_name,'ERROR','PASS',format('[%s] %s',v_state,v_msg));
        RETURN;
    END;

    INSERT INTO _mrp_test_results(test_no,test_name,expected,result,detail)
    VALUES (p_no,p_name,'ERROR','FAIL','Statement succeeded but an error was expected');
END;
$$;

-- Execute SQL that MUST succeed. Any PostgreSQL error = FAIL.
-- The exception is caught inside this function, so one failing test
-- does not abort the outer transaction or hide later test results.
CREATE OR REPLACE FUNCTION pg_temp.assert_success(
    p_no INTEGER,
    p_name TEXT,
    p_sql TEXT
) RETURNS VOID
LANGUAGE plpgsql AS $$
DECLARE
    v_state TEXT;
    v_msg   TEXT;
BEGIN
    BEGIN
        EXECUTE p_sql;
    EXCEPTION WHEN OTHERS THEN
        v_state := SQLSTATE;
        v_msg := SQLERRM;
        INSERT INTO _mrp_test_results(test_no,test_name,expected,result,detail)
        VALUES (p_no,p_name,'SUCCESS','FAIL',format('[%s] %s',v_state,v_msg));
        RETURN;
    END;

    INSERT INTO _mrp_test_results(test_no,test_name,expected,result,detail)
    VALUES (p_no,p_name,'SUCCESS','PASS','');
END;
$$;

-- =====================================================================
-- TEST 0: Installed schema sanity
-- =====================================================================
DO $$
DECLARE
    v_tables INTEGER;
    v_triggers INTEGER;
BEGIN
    SELECT count(*) INTO v_tables
    FROM information_schema.tables
    WHERE table_schema='public' AND table_type='BASE TABLE';

    SELECT count(*) INTO v_triggers
    FROM information_schema.triggers
    WHERE trigger_schema='public';

    IF v_tables = 29 AND v_triggers >= 10 THEN
        INSERT INTO _mrp_test_results
        VALUES (0,'Installed schema object count','29 tables + trigger set','PASS',
                format('%s tables, %s triggers',v_tables,v_triggers));
    ELSE
        INSERT INTO _mrp_test_results
        VALUES (0,'Installed schema object count','29 tables + trigger set','FAIL',
                format('%s tables, %s triggers',v_tables,v_triggers));
    END IF;
END $$;

-- =====================================================================
-- TEST DATA
-- =====================================================================
DO $$
DECLARE
    v_user_customer BIGINT;
    v_user_staff BIGINT;
    v_user_wm BIGINT;
    v_customer BIGINT;
    v_staff BIGINT;
    v_wm BIGINT;
    v_cat BIGINT;
    v_product BIGINT;
    v_material BIGINT;
    v_material2 BIGINT;
    v_supplier BIGINT;
    v_bom BIGINT;
BEGIN
    INSERT INTO users(username,password_hash,role)
    VALUES ('__MRP_TEST_C_' || txid_current(),'x','CUSTOMER')
    RETURNING id INTO v_user_customer;

    INSERT INTO users(username,password_hash,role)
    VALUES ('__MRP_TEST_S_' || txid_current(),'x','STAFF')
    RETURNING id INTO v_user_staff;

    INSERT INTO users(username,password_hash,role)
    VALUES ('__MRP_TEST_WM_' || txid_current(),'x','WAREHOUSE_MANAGER')
    RETURNING id INTO v_user_wm;

    INSERT INTO customers(user_id,full_name)
    VALUES (v_user_customer,'MRP TEST Customer')
    RETURNING id INTO v_customer;

    INSERT INTO staff(user_id,full_name)
    VALUES (v_user_staff,'MRP TEST Staff')
    RETURNING id INTO v_staff;

    INSERT INTO warehouse_managers(user_id,full_name)
    VALUES (v_user_wm,'MRP TEST Warehouse Manager')
    RETURNING id INTO v_wm;

    INSERT INTO categories(name)
    VALUES ('__MRP_TEST_CATEGORY_' || txid_current())
    RETURNING id INTO v_cat;

    INSERT INTO products(category_id,name,unit,selling_price)
    VALUES (v_cat,'__MRP_TEST_PRODUCT','piece',1000)
    RETURNING id INTO v_product;

    INSERT INTO materials(code,name,unit,standard_cost)
    VALUES ('__MRP_TEST_M1_' || txid_current(),'__MRP_TEST_WOOD','kg',10)
    RETURNING id INTO v_material;

    INSERT INTO materials(code,name,unit,standard_cost)
    VALUES ('__MRP_TEST_M2_' || txid_current(),'__MRP_TEST_METAL','kg',20)
    RETURNING id INTO v_material2;

    INSERT INTO suppliers(name)
    VALUES ('__MRP_TEST_SUPPLIER_' || txid_current())
    RETURNING id INTO v_supplier;

    INSERT INTO boms(product_id,version,is_active)
    VALUES (v_product,1,true)
    RETURNING id INTO v_bom;

    -- 2 kg of material 1 per finished product.
    INSERT INTO bom_items(bom_id,material_id,quantity)
    VALUES (v_bom,v_material,2);

    INSERT INTO _mrp_test_ids(k,v) VALUES
      ('user_customer',v_user_customer),
      ('user_staff',v_user_staff),
      ('user_wm',v_user_wm),
      ('customer',v_customer),
      ('staff',v_staff),
      ('wm',v_wm),
      ('cat',v_cat),
      ('product',v_product),
      ('material',v_material),
      ('material2',v_material2),
      ('supplier',v_supplier),
      ('bom',v_bom);
END $$;

-- =====================================================================
-- TEST 1-2: Role/profile integrity
-- =====================================================================
SELECT pg_temp.assert_error(
    1,
    'Staff profile cannot point to CUSTOMER user',
    format($sql$
        INSERT INTO staff(user_id,full_name)
        VALUES (%s,'INVALID ROLE PROFILE')
    $sql$,(SELECT v FROM _mrp_test_ids WHERE k='user_customer'))
);

SELECT pg_temp.assert_success(
    2,
    'Valid CUSTOMER/STAFF/WAREHOUSE_MANAGER profiles exist',
    format($sql$
        SELECT 1
        FROM customers c
        JOIN users u ON u.id=c.user_id
        WHERE c.id=%s AND u.role='CUSTOMER';
    $sql$,(SELECT v FROM _mrp_test_ids WHERE k='customer'))
);

-- =====================================================================
-- TEST 3-7: Purchase receipt and inventory protection
-- =====================================================================
DO $$
DECLARE
    v_receipt BIGINT;
    v_material BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='material');
    v_supplier BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='supplier');
    v_wm BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='wm');
BEGIN
    INSERT INTO purchase_receipts(supplier_id,warehouse_manager_id,status,total_amount)
    VALUES (v_supplier,v_wm,'PENDING',40)
    RETURNING id INTO v_receipt;

    INSERT INTO purchase_receipt_items(
        purchase_receipt_id,material_id,quantity,unit_price,amount)
    VALUES (v_receipt,v_material,4,10,40);

    INSERT INTO _mrp_test_ids(k,v) VALUES ('receipt',v_receipt);
END $$;

SELECT pg_temp.assert_error(
    3,
    'Stock movement from PENDING purchase receipt is rejected',
    format($sql$
        INSERT INTO stock_movements(
            material_id,movement_type,quantity,purchase_receipt_id,created_by)
        VALUES (%s,'IN',4,%s,%s)
    $sql$,
    (SELECT v FROM _mrp_test_ids WHERE k='material'),
    (SELECT v FROM _mrp_test_ids WHERE k='receipt'),
    (SELECT v FROM _mrp_test_ids WHERE k='user_wm'))
);

SELECT pg_temp.assert_success(
    4,
    'COMPLETED purchase receipt posts material inventory',
    format($sql$
        UPDATE purchase_receipts
        SET status='COMPLETED'
        WHERE id=%s;

        INSERT INTO stock_movements(
            material_id,movement_type,quantity,purchase_receipt_id,created_by)
        VALUES (%s,'IN',4,%s,%s);

        DO $check$
        BEGIN
            IF (SELECT quantity FROM inventories WHERE material_id=%s) <> 4 THEN
                RAISE EXCEPTION 'Expected material inventory = 4';
            END IF;
        END
        $check$;
    $sql$,
    (SELECT v FROM _mrp_test_ids WHERE k='receipt'),
    (SELECT v FROM _mrp_test_ids WHERE k='material'),
    (SELECT v FROM _mrp_test_ids WHERE k='receipt'),
    (SELECT v FROM _mrp_test_ids WHERE k='user_wm'),
    (SELECT v FROM _mrp_test_ids WHERE k='material'))
);

SELECT pg_temp.assert_error(
    5,
    'Direct inventory UPDATE is rejected',
    format($sql$
        UPDATE inventories SET quantity=999
        WHERE material_id=%s
    $sql$,(SELECT v FROM _mrp_test_ids WHERE k='material'))
);

SELECT pg_temp.assert_error(
    6,
    'Direct inventory DELETE is rejected',
    format($sql$
        DELETE FROM inventories WHERE material_id=%s
    $sql$,(SELECT v FROM _mrp_test_ids WHERE k='material'))
);

SELECT pg_temp.assert_error(
    7,
    'Direct inventory INSERT with non-zero quantity is rejected',
    format($sql$
        INSERT INTO inventories(material_id,quantity)
        VALUES (%s,99)
    $sql$,(SELECT v FROM _mrp_test_ids WHERE k='material2'))
);

-- =====================================================================
-- TEST 8-12: BOM -> Production Request -> Material Issue
-- =====================================================================
DO $$
DECLARE
    v_product BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='product');
    v_wm BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='wm');
    v_staff BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='staff');
    v_bom BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='bom');
    v_inactive_bom BIGINT;
BEGIN
    INSERT INTO boms(product_id,version,is_active)
    VALUES (v_product,2,false)
    RETURNING id INTO v_inactive_bom;

    INSERT INTO _mrp_test_ids(k,v) VALUES ('inactive_bom',v_inactive_bom);
END $$;

SELECT pg_temp.assert_error(
    8,
    'Production Request with inactive BOM is rejected',
    format($sql$
        INSERT INTO production_requests(
            product_id,bom_id,quantity,warehouse_manager_id,assigned_to,status)
        VALUES (%s,%s,1,%s,%s,'PENDING')
    $sql$,
    (SELECT v FROM _mrp_test_ids WHERE k='product'),
    (SELECT v FROM _mrp_test_ids WHERE k='inactive_bom'),
    (SELECT v FROM _mrp_test_ids WHERE k='wm'),
    (SELECT v FROM _mrp_test_ids WHERE k='staff'))
);

SELECT pg_temp.assert_success(
    9,
    'Valid Production Request can be created from active BOM',
    format($sql$
        INSERT INTO production_requests(
            product_id,bom_id,quantity,warehouse_manager_id,assigned_to,status)
        VALUES (%s,%s,2,%s,%s,'IN_PROGRESS')
        RETURNING id
    $sql$,
    (SELECT v FROM _mrp_test_ids WHERE k='product'),
    (SELECT v FROM _mrp_test_ids WHERE k='bom'),
    (SELECT v FROM _mrp_test_ids WHERE k='wm'),
    (SELECT v FROM _mrp_test_ids WHERE k='staff'))
);

-- The previous test creates the request, so fetch the newest one.
INSERT INTO _mrp_test_ids(k,v)
SELECT 'pr',id
FROM production_requests
WHERE product_id=(SELECT v FROM _mrp_test_ids WHERE k='product')
ORDER BY id DESC
LIMIT 1;

DO $$
DECLARE
    v_pr BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='pr');
    v_staff BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='staff');
    v_material2 BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='material2');
    v_mir BIGINT;
BEGIN
    INSERT INTO material_issue_requests(production_request_id,requested_by)
    VALUES (v_pr,v_staff)
    RETURNING id INTO v_mir;

    INSERT INTO _mrp_test_ids(k,v) VALUES ('mir',v_mir);
END $$;

SELECT pg_temp.assert_error(
    10,
    'Material Issue item not present in BOM is rejected',
    format($sql$
        INSERT INTO material_issue_request_items(request_id,material_id,quantity)
        VALUES (%s,%s,1)
    $sql$,
    (SELECT v FROM _mrp_test_ids WHERE k='mir'),
    (SELECT v FROM _mrp_test_ids WHERE k='material2'))
);

SELECT pg_temp.assert_success(
    11,
    'Material Issue item within BOM requirement is accepted',
    format($sql$
        INSERT INTO material_issue_request_items(request_id,material_id,quantity)
        VALUES (%s,%s,4)
    $sql$,
    (SELECT v FROM _mrp_test_ids WHERE k='mir'),
    (SELECT v FROM _mrp_test_ids WHERE k='material'))
);

-- Second MIR: cumulative request must still be <= 2 kg x 2 products = 4 kg.
DO $$
DECLARE
    v_mir2 BIGINT;
    v_pr BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='pr');
    v_staff BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='staff');
BEGIN
    INSERT INTO material_issue_requests(production_request_id,requested_by)
    VALUES (v_pr,v_staff)
    RETURNING id INTO v_mir2;
    INSERT INTO _mrp_test_ids(k,v) VALUES ('mir2',v_mir2);
END $$;

SELECT pg_temp.assert_error(
    12,
    'Cumulative Material Issue above BOM requirement is rejected',
    format($sql$
        INSERT INTO material_issue_request_items(request_id,material_id,quantity)
        VALUES (%s,%s,0.001)
    $sql$,
    (SELECT v FROM _mrp_test_ids WHERE k='mir2'),
    (SELECT v FROM _mrp_test_ids WHERE k='material'))
);

-- Approve the valid MIR and post its stock movement.
SELECT pg_temp.assert_success(
    13,
    'Approved Material Issue decreases material inventory',
    format($sql$
        UPDATE material_issue_requests
        SET status='APPROVED', reviewed_by=%s, reviewed_at=now()
        WHERE id=%s;

        INSERT INTO stock_movements(
            material_id,movement_type,quantity,material_issue_request_id,created_by)
        VALUES (%s,'OUT',4,%s,%s);

        DO $check$
        BEGIN
            IF (SELECT quantity FROM inventories WHERE material_id=%s) <> 0 THEN
                RAISE EXCEPTION 'Expected material inventory = 0';
            END IF;
        END
        $check$;
    $sql$,
    (SELECT v FROM _mrp_test_ids WHERE k='wm'),
    (SELECT v FROM _mrp_test_ids WHERE k='mir'),
    (SELECT v FROM _mrp_test_ids WHERE k='material'),
    (SELECT v FROM _mrp_test_ids WHERE k='mir'),
    (SELECT v FROM _mrp_test_ids WHERE k='user_wm'),
    (SELECT v FROM _mrp_test_ids WHERE k='material'))
);

-- =====================================================================
-- TEST 14-16: Production quantity / completion / finished inventory
-- =====================================================================
DO $$
DECLARE
    v_pr BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='pr');
    v_staff BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='staff');
    v_prod BIGINT;
BEGIN
    INSERT INTO productions(production_request_id,quantity,produced_by,status)
    VALUES (v_pr,1,v_staff,'IN_PROGRESS')
    RETURNING id INTO v_prod;
    INSERT INTO _mrp_test_ids(k,v) VALUES ('production',v_prod);
END $$;

SELECT pg_temp.assert_error(
    14,
    'Cumulative Production above Production Request is rejected',
    format($sql$
        INSERT INTO productions(production_request_id,quantity,produced_by,status)
        VALUES (%s,2,%s,'IN_PROGRESS')
    $sql$,
    (SELECT v FROM _mrp_test_ids WHERE k='pr'),
    (SELECT v FROM _mrp_test_ids WHERE k='staff'))
);

SELECT pg_temp.assert_success(
    15,
    'Production can be completed when enough BOM material was issued',
    format($sql$
        UPDATE productions
        SET status='COMPLETED', completed_at=now()
        WHERE id=%s;
    $sql$,(SELECT v FROM _mrp_test_ids WHERE k='production'))
);

SELECT pg_temp.assert_success(
    16,
    'Completed Production posts finished-goods inventory through stock movement',
    format($sql$
        INSERT INTO stock_movements(
            product_id,movement_type,quantity,production_id,created_by)
        VALUES (%s,'IN',1,%s,%s);

        DO $check$
        BEGIN
            IF (SELECT quantity FROM inventories WHERE product_id=%s) <> 1 THEN
                RAISE EXCEPTION 'Expected finished-goods inventory = 1';
            END IF;
        END
        $check$;
    $sql$,
    (SELECT v FROM _mrp_test_ids WHERE k='product'),
    (SELECT v FROM _mrp_test_ids WHERE k='production'),
    (SELECT v FROM _mrp_test_ids WHERE k='user_staff'),
    (SELECT v FROM _mrp_test_ids WHERE k='product'))
);

-- Separate Production Request with zero issued material: completion must fail.
DO $$
DECLARE
    v_pr2 BIGINT;
    v_product BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='product');
    v_bom BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='bom');
    v_wm BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='wm');
    v_staff BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='staff');
    v_prod2 BIGINT;
BEGIN
    INSERT INTO production_requests(
        product_id,bom_id,quantity,warehouse_manager_id,assigned_to,status)
    VALUES (v_product,v_bom,1,v_wm,v_staff,'IN_PROGRESS')
    RETURNING id INTO v_pr2;

    INSERT INTO productions(
        production_request_id,quantity,produced_by,status)
    VALUES (v_pr2,1,v_staff,'IN_PROGRESS')
    RETURNING id INTO v_prod2;

    INSERT INTO _mrp_test_ids(k,v) VALUES ('pr2',v_pr2),('production2',v_prod2);
END $$;

SELECT pg_temp.assert_error(
    17,
    'Production completion without enough issued material is rejected',
    format($sql$
        UPDATE productions
        SET status='COMPLETED', completed_at=now()
        WHERE id=%s
    $sql$,(SELECT v FROM _mrp_test_ids WHERE k='production2'))
);

-- =====================================================================
-- TEST 18-19: Stock movement immutability and quantity/source integrity
-- =====================================================================
SELECT pg_temp.assert_error(
    18,
    'Stock movement UPDATE is rejected',
    format($sql$
        UPDATE stock_movements
        SET note='TAMPER'
        WHERE material_issue_request_id=%s
    $sql$,(SELECT v FROM _mrp_test_ids WHERE k='mir'))
);

SELECT pg_temp.assert_error(
    19,
    'Stock movement DELETE is rejected',
    format($sql$
        DELETE FROM stock_movements
        WHERE material_issue_request_id=%s
    $sql$,(SELECT v FROM _mrp_test_ids WHERE k='mir'))
);

SELECT pg_temp.assert_error(
    20,
    'Stock movement quantity must match Material Issue detail',
    format($sql$
        INSERT INTO stock_movements(
            material_id,movement_type,quantity,material_issue_request_id,created_by)
        VALUES (%s,'OUT',3,%s,%s)
    $sql$,
    (SELECT v FROM _mrp_test_ids WHERE k='material'),
    (SELECT v FROM _mrp_test_ids WHERE k='mir'),
    (SELECT v FROM _mrp_test_ids WHERE k='user_wm'))
);

SELECT pg_temp.assert_error(
    21,
    'Duplicate stock movement for the same source is rejected',
    format($sql$
        INSERT INTO stock_movements(
            material_id,movement_type,quantity,material_issue_request_id,created_by)
        VALUES (%s,'OUT',4,%s,%s)
    $sql$,
    (SELECT v FROM _mrp_test_ids WHERE k='material'),
    (SELECT v FROM _mrp_test_ids WHERE k='mir'),
    (SELECT v FROM _mrp_test_ids WHERE k='user_wm'))
);

SELECT pg_temp.assert_error(
    22,
    'Adjustment movement without a note is rejected',
    format($sql$
        INSERT INTO stock_movements(
            material_id,movement_type,quantity,created_by)
        VALUES (%s,'ADJUST_IN',1,%s)
    $sql$,
    (SELECT v FROM _mrp_test_ids WHERE k='material2'),
    (SELECT v FROM _mrp_test_ids WHERE k='user_wm'))
);

-- =====================================================================
-- TEST 23-26: Finished Goods Issue and Order rules
-- =====================================================================
DO $$
DECLARE
    v_order BIGINT;
    v_customer BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='customer');
    v_staff BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='staff');
    v_product BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='product');
BEGIN
    -- IMPORTANT: order_items must be inserted while order is PENDING because
    -- the schema intentionally freezes items after confirmation.
    INSERT INTO orders(customer_id,status,total_amount)
    VALUES (v_customer,'PENDING',1000)
    RETURNING id INTO v_order;

    INSERT INTO order_items(order_id,product_id,quantity,unit_price,amount)
    VALUES (v_order,v_product,1,1000,1000);

    UPDATE orders
    SET status='CONFIRMED', staff_id=v_staff
    WHERE id=v_order;

    INSERT INTO _mrp_test_ids(k,v) VALUES ('order',v_order);
END $$;

DO $$
DECLARE
    v_order_pending BIGINT;
    v_customer BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='customer');
    v_product BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='product');
BEGIN
    INSERT INTO orders(customer_id,status,total_amount)
    VALUES (v_customer,'PENDING',1000)
    RETURNING id INTO v_order_pending;

    INSERT INTO order_items(order_id,product_id,quantity,unit_price,amount)
    VALUES (v_order_pending,v_product,1,1000,1000);

    INSERT INTO _mrp_test_ids(k,v) VALUES ('pending_order',v_order_pending);
END $$;

SELECT pg_temp.assert_error(
    23,
    'FG Issue Request for non-CONFIRMED order is rejected',
    format($sql$
        INSERT INTO finished_goods_issue_requests(order_id,requested_by)
        VALUES (%s,%s)
    $sql$,
    (SELECT v FROM _mrp_test_ids WHERE k='pending_order'),
    (SELECT v FROM _mrp_test_ids WHERE k='staff'))
);

DO $$
DECLARE
    v_fgir BIGINT;
    v_order BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='order');
    v_staff BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='staff');
    v_product BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='product');
BEGIN
    INSERT INTO finished_goods_issue_requests(order_id,requested_by)
    VALUES (v_order,v_staff)
    RETURNING id INTO v_fgir;

    INSERT INTO _mrp_test_ids(k,v) VALUES ('fgir',v_fgir);
END $$;

SELECT pg_temp.assert_error(
    24,
    'FG Issue quantity above Order quantity is rejected',
    format($sql$
        INSERT INTO finished_goods_issue_request_items(
            request_id,order_id,product_id,quantity)
        VALUES (%s,%s,%s,2)
    $sql$,
    (SELECT v FROM _mrp_test_ids WHERE k='fgir'),
    (SELECT v FROM _mrp_test_ids WHERE k='order'),
    (SELECT v FROM _mrp_test_ids WHERE k='product'))
);

SELECT pg_temp.assert_success(
    25,
    'Valid FG Issue item can be created for confirmed Order',
    format($sql$
        INSERT INTO finished_goods_issue_request_items(
            request_id,order_id,product_id,quantity)
        VALUES (%s,%s,%s,1)
    $sql$,
    (SELECT v FROM _mrp_test_ids WHERE k='fgir'),
    (SELECT v FROM _mrp_test_ids WHERE k='order'),
    (SELECT v FROM _mrp_test_ids WHERE k='product'))
);

SELECT pg_temp.assert_success(
    26,
    'Approved FG Issue decreases finished-goods inventory',
    format($sql$
        UPDATE finished_goods_issue_requests
        SET status='APPROVED', reviewed_by=%s, reviewed_at=now()
        WHERE id=%s;

        INSERT INTO stock_movements(
            product_id,movement_type,quantity,fg_issue_request_id,created_by)
        VALUES (%s,'OUT',1,%s,%s);

        DO $check$
        BEGIN
            IF (SELECT quantity FROM inventories WHERE product_id=%s) <> 0 THEN
                RAISE EXCEPTION 'Expected finished-goods inventory = 0';
            END IF;
        END
        $check$;
    $sql$,
    (SELECT v FROM _mrp_test_ids WHERE k='wm'),
    (SELECT v FROM _mrp_test_ids WHERE k='fgir'),
    (SELECT v FROM _mrp_test_ids WHERE k='product'),
    (SELECT v FROM _mrp_test_ids WHERE k='fgir'),
    (SELECT v FROM _mrp_test_ids WHERE k='user_wm'),
    (SELECT v FROM _mrp_test_ids WHERE k='product'))
);

-- =====================================================================
-- TEST 27-29: Order/receipt detail integrity and final immutability
-- =====================================================================
DO $$
DECLARE
    v_order BIGINT;
    v_customer BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='customer');
    v_product BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='product');
BEGIN
    INSERT INTO orders(customer_id,status,total_amount)
    VALUES (v_customer,'PENDING',999)
    RETURNING id INTO v_order;

    INSERT INTO _mrp_test_ids(k,v) VALUES ('amount_test_order',v_order);
END $$;

SELECT pg_temp.assert_error(
    27,
    'Order item amount mismatch is rejected',
    format($sql$
        INSERT INTO order_items(order_id,product_id,quantity,unit_price,amount)
        VALUES (%s,%s,1,1000,999)
    $sql$,
    (SELECT v FROM _mrp_test_ids WHERE k='amount_test_order'),
    (SELECT v FROM _mrp_test_ids WHERE k='product'))
);

DO $$
DECLARE
    v_receipt BIGINT;
    v_material2 BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='material2');
    v_supplier BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='supplier');
    v_wm BIGINT := (SELECT v FROM _mrp_test_ids WHERE k='wm');
BEGIN
    -- Keep this receipt PENDING so the item-level amount check is what rejects it,
    -- rather than the immutable/freeze trigger.
    INSERT INTO purchase_receipts(supplier_id,warehouse_manager_id,status,total_amount)
    VALUES (v_supplier,v_wm,'PENDING',10)
    RETURNING id INTO v_receipt;
    INSERT INTO _mrp_test_ids(k,v) VALUES ('amount_test_receipt',v_receipt);
END $$;

SELECT pg_temp.assert_error(
    28,
    'Purchase receipt item amount mismatch is rejected',
    format($sql$
        INSERT INTO purchase_receipt_items(
            purchase_receipt_id,material_id,quantity,unit_price,amount)
        VALUES (%s,%s,1,10,999)
    $sql$,
    (SELECT v FROM _mrp_test_ids WHERE k='amount_test_receipt'),
    (SELECT v FROM _mrp_test_ids WHERE k='material2'))
);

SELECT pg_temp.assert_error(
    29,
    'Confirmed Order items cannot be modified',
    format($sql$
        UPDATE order_items
        SET quantity=2
        WHERE order_id=%s
          AND product_id=%s
    $sql$,
    (SELECT v FROM _mrp_test_ids WHERE k='order'),
    (SELECT v FROM _mrp_test_ids WHERE k='product'))
);

-- =====================================================================
-- FINAL REPORT
-- =====================================================================
SELECT test_no, test_name, expected, result, detail
FROM _mrp_test_results
ORDER BY test_no;

DO $$
DECLARE
    v_total INTEGER;
    v_pass INTEGER;
    v_fail INTEGER;
BEGIN
    SELECT count(*), count(*) FILTER (WHERE result='PASS'), count(*) FILTER (WHERE result='FAIL')
      INTO v_total, v_pass, v_fail
    FROM _mrp_test_results;

    RAISE NOTICE '============================================================';
    RAISE NOTICE 'MRP DATABASE TEST SUMMARY: % total | % PASS | % FAIL', v_total, v_pass, v_fail;

    IF v_fail = 0 THEN
        RAISE NOTICE 'ALL MRP DATABASE TESTS PASSED';
    ELSE
        RAISE NOTICE 'MRP DATABASE TESTS HAVE FAILURES - inspect the result table above';
    END IF;

    RAISE NOTICE 'All test data will now be rolled back.';
    RAISE NOTICE '============================================================';
END $$;

ROLLBACK;

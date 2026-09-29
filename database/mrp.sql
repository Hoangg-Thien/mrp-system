CREATE TABLE "users" (
  "id" BIGSERIAL PRIMARY KEY,
  "username" VARCHAR(50) UNIQUE NOT NULL,
  "password_hash" VARCHAR(255) NOT NULL,
  "role" VARCHAR(30) NOT NULL CHECK (role IN ('CUSTOMER', 'STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')) DEFAULT 'CUSTOMER',
  "is_active" BOOLEAN NOT NULL DEFAULT true,
  "created_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE "customers" (
  "id" BIGSERIAL PRIMARY KEY,
  "user_id" BIGINT UNIQUE NOT NULL,
  "full_name" VARCHAR(150) NOT NULL,
  "email" VARCHAR(150) UNIQUE,
  "phone" VARCHAR(20),
  "address" TEXT,
  "created_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE "staff" (
  "id" BIGSERIAL PRIMARY KEY,
  "user_id" BIGINT UNIQUE NOT NULL,
  "full_name" VARCHAR(150) NOT NULL,
  "email" VARCHAR(150) UNIQUE,
  "phone" VARCHAR(20),
  "address" TEXT,
  "created_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE "warehouse_managers" (
  "id" BIGSERIAL PRIMARY KEY,
  "user_id" BIGINT UNIQUE NOT NULL,
  "full_name" VARCHAR(150) NOT NULL,
  "email" VARCHAR(150) UNIQUE,
  "phone" VARCHAR(20),
  "address" TEXT,
  "created_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE "admins" (
  "id" BIGSERIAL PRIMARY KEY,
  "user_id" BIGINT UNIQUE NOT NULL,
  "full_name" VARCHAR(150) NOT NULL,
  "email" VARCHAR(150) UNIQUE,
  "phone" VARCHAR(20),
  "address" TEXT,
  "created_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE "categories" (
  "id" BIGSERIAL PRIMARY KEY,
  "name" VARCHAR(150) UNIQUE NOT NULL,
  "description" TEXT
);

CREATE TABLE "products" (
  "id" BIGSERIAL PRIMARY KEY,
  "category_id" BIGINT NOT NULL,
  "name" VARCHAR(200) NOT NULL,
  "description" TEXT,
  "unit" VARCHAR(50) NOT NULL,
  "selling_price" NUMERIC(15,2) NOT NULL CHECK (selling_price >= 0),
  "is_active" BOOLEAN NOT NULL DEFAULT true,
  "created_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE "carts" (
  "id" BIGSERIAL PRIMARY KEY,
  "customer_id" BIGINT UNIQUE NOT NULL,
  "created_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  "updated_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE "cart_items" (
  "cart_id" BIGINT NOT NULL,
  "product_id" BIGINT NOT NULL,
  "quantity" INTEGER NOT NULL CHECK (quantity > 0),
  PRIMARY KEY ("cart_id", "product_id")
);

CREATE TABLE "orders" (
  "id" BIGSERIAL PRIMARY KEY,
  "customer_id" BIGINT NOT NULL,
  "staff_id" BIGINT,
  "order_date" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  "status" VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  "total_amount" NUMERIC(15,2) NOT NULL CHECK (total_amount >= 0) DEFAULT 0,
  "voucher_id" BIGINT,
  "discount_amount" NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0)
);

CREATE TABLE "order_items" (
  "order_id" BIGINT NOT NULL,
  "product_id" BIGINT NOT NULL,
  "quantity" INTEGER NOT NULL CHECK (quantity > 0),
  "unit_price" NUMERIC(15,2) NOT NULL CHECK (unit_price >= 0),
  "amount" NUMERIC(15,2) NOT NULL CHECK (amount >= 0),
  PRIMARY KEY ("order_id", "product_id")
);

CREATE TABLE "suppliers" (
  "id" BIGSERIAL PRIMARY KEY,
  "name" VARCHAR(200) NOT NULL,
  "phone" VARCHAR(20),
  "email" VARCHAR(150),
  "address" TEXT
);

CREATE TABLE "materials" (
  "id" BIGSERIAL PRIMARY KEY,
  "code" VARCHAR(50) UNIQUE NOT NULL,
  "name" VARCHAR(200) NOT NULL,
  "unit" VARCHAR(50) NOT NULL,
  "standard_cost" NUMERIC(15,2) NOT NULL CHECK (standard_cost >= 0) DEFAULT 0,
  "is_active" BOOLEAN NOT NULL DEFAULT true,
  "created_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE "purchase_receipts" (
  "id" BIGSERIAL PRIMARY KEY,
  "supplier_id" BIGINT NOT NULL,
  "warehouse_manager_id" BIGINT NOT NULL,
  "receipt_date" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  "status" VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  "total_amount" NUMERIC(15,2) NOT NULL CHECK (total_amount >= 0) DEFAULT 0
);

CREATE TABLE "purchase_receipt_items" (
  "purchase_receipt_id" BIGINT NOT NULL,
  "material_id" BIGINT NOT NULL,
  "quantity" NUMERIC(15,3) NOT NULL CHECK (quantity > 0),
  "unit_price" NUMERIC(15,2) NOT NULL CHECK (unit_price >= 0),
  "amount" NUMERIC(15,2) NOT NULL CHECK (amount >= 0),
  PRIMARY KEY ("purchase_receipt_id", "material_id")
);

CREATE TABLE "inventories" (
  "id" BIGSERIAL PRIMARY KEY,
  "material_id" BIGINT,
  "product_id" BIGINT,
  "quantity" NUMERIC(15,3) NOT NULL CHECK (quantity >= 0) DEFAULT 0,
  "updated_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  CHECK ((material_id IS NOT NULL AND product_id IS NULL) OR (material_id IS NULL AND product_id IS NOT NULL))
);

CREATE TABLE "boms" (
  "id" BIGSERIAL PRIMARY KEY,
  "product_id" BIGINT NOT NULL,
  "version" INTEGER NOT NULL CHECK (version > 0) DEFAULT 1,
  "is_active" BOOLEAN NOT NULL DEFAULT true,
  "created_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE "bom_items" (
  "id" BIGSERIAL PRIMARY KEY,
  "bom_id" BIGINT NOT NULL,
  "material_id" BIGINT NOT NULL,
  "quantity" NUMERIC(15,3) NOT NULL CHECK (quantity > 0)
);

CREATE TABLE "production_requests" (
  "id" BIGSERIAL PRIMARY KEY,
  "product_id" BIGINT NOT NULL,
  "bom_id" BIGINT NOT NULL,
  "quantity" INTEGER NOT NULL CHECK (quantity > 0),
  "warehouse_manager_id" BIGINT NOT NULL,
  "assigned_to" BIGINT,
  "status" VARCHAR(30) NOT NULL CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')) DEFAULT 'PENDING',
  "created_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  "note" TEXT
);

CREATE TABLE "material_issue_requests" (
  "id" BIGSERIAL PRIMARY KEY,
  "production_request_id" BIGINT NOT NULL,
  "requested_by" BIGINT NOT NULL,
  "reviewed_by" BIGINT,
  "status" VARCHAR(30) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')) DEFAULT 'PENDING',
  "requested_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  "reviewed_at" TIMESTAMP,
  "note" TEXT
);

CREATE TABLE "material_issue_request_items" (
  "id" BIGSERIAL PRIMARY KEY,
  "request_id" BIGINT NOT NULL,
  "material_id" BIGINT NOT NULL,
  "quantity" NUMERIC(15,3) NOT NULL CHECK (quantity > 0)
);

CREATE TABLE "productions" (
  "id" BIGSERIAL PRIMARY KEY,
  "production_request_id" BIGINT NOT NULL,
  "quantity" INTEGER NOT NULL CHECK (quantity > 0),
  "produced_by" BIGINT NOT NULL,
  "status" VARCHAR(30) NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'CANCELLED')) DEFAULT 'IN_PROGRESS',
  "started_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  "completed_at" TIMESTAMP,
  "note" TEXT
);

CREATE TABLE "finished_goods_issue_requests" (
  "id" BIGSERIAL PRIMARY KEY,
  "order_id" BIGINT NOT NULL,
  "requested_by" BIGINT NOT NULL,
  "reviewed_by" BIGINT,
  "status" VARCHAR(30) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')) DEFAULT 'PENDING',
  "requested_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  "reviewed_at" TIMESTAMP,
  "note" TEXT
);

CREATE TABLE "finished_goods_issue_request_items" (
  "id" BIGSERIAL PRIMARY KEY,
  "request_id" BIGINT NOT NULL,
  "product_id" BIGINT NOT NULL,
  "quantity" INTEGER NOT NULL CHECK (quantity > 0)
);

CREATE TABLE "stock_movements" (
  "id" BIGSERIAL PRIMARY KEY,
  "material_id" BIGINT,
  "product_id" BIGINT,
  "movement_type" VARCHAR(20) NOT NULL CHECK (movement_type IN ('IN', 'OUT', 'ADJUST_IN', 'ADJUST_OUT')),
  "quantity" NUMERIC(15,3) NOT NULL CHECK (quantity > 0),
  "purchase_receipt_id" BIGINT,
  "material_issue_request_id" BIGINT,
  "production_id" BIGINT,
  "fg_issue_request_id" BIGINT,
  "created_by" BIGINT NOT NULL,
  "created_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  "note" TEXT,
  CHECK ((material_id IS NOT NULL AND product_id IS NULL) OR (material_id IS NULL AND product_id IS NOT NULL)),
  -- product quantities must be whole units (a chair isn't sold as 1.5)
  CHECK (product_id IS NULL OR quantity = trunc(quantity))
);

CREATE TABLE "promotion_programs" (
  "id" BIGSERIAL PRIMARY KEY,
  "name" VARCHAR(200) NOT NULL,
  "description" TEXT,
  "start_date" TIMESTAMP NOT NULL,
  "end_date" TIMESTAMP NOT NULL,
  "status" VARCHAR(30) NOT NULL CHECK (status IN ('DRAFT','ACTIVE','INACTIVE')) DEFAULT 'DRAFT',
  CHECK (end_date >= start_date)
);

-- Fix (normalization): removed start_date/end_date (duplicated with parent
-- promotion_programs, could contradict it)
CREATE TABLE "product_discounts" (
  "id" BIGSERIAL PRIMARY KEY,
  "promotion_program_id" BIGINT NOT NULL,
  "product_id" BIGINT NOT NULL,
  "discount_percent" NUMERIC(5,2) NOT NULL CHECK (discount_percent > 0 AND discount_percent <= 100)
);

CREATE TABLE "order_discounts" (
  "id" BIGSERIAL PRIMARY KEY,
  "promotion_program_id" BIGINT NOT NULL,
  "minimum_amount" NUMERIC(15,2) NOT NULL CHECK (minimum_amount >= 0),
  "discount_percent" NUMERIC(5,2) NOT NULL CHECK (discount_percent > 0 AND discount_percent <= 100)
);

CREATE TABLE "vouchers" (
  "id" BIGSERIAL PRIMARY KEY,
  "promotion_program_id" BIGINT NOT NULL,
  "code" VARCHAR(50) UNIQUE NOT NULL,
  "discount_percent" NUMERIC(5,2) NOT NULL CHECK (discount_percent > 0 AND discount_percent <= 100),
  "minimum_amount" NUMERIC(15,2) NOT NULL CHECK (minimum_amount >= 0) DEFAULT 0,
  "maximum_discount" NUMERIC(15,2),
  "quantity" INTEGER NOT NULL CHECK (quantity >= 0) DEFAULT 0,
  "used_quantity" INTEGER NOT NULL CHECK (used_quantity >= 0 AND used_quantity <= quantity) DEFAULT 0,
  "start_date" TIMESTAMP NOT NULL,
  "end_date" TIMESTAMP NOT NULL,
  "status" VARCHAR(30) NOT NULL CHECK (status IN ('ACTIVE','INACTIVE')) DEFAULT 'ACTIVE',
  CHECK (end_date >= start_date),
  CHECK (maximum_discount IS NULL OR maximum_discount >= 0)
);


CREATE INDEX "idx_products_category" ON "products" ("category_id");
CREATE INDEX "idx_cart_items_product" ON "cart_items" ("product_id");
CREATE INDEX "idx_orders_customer" ON "orders" ("customer_id");
CREATE INDEX "idx_orders_staff" ON "orders" ("staff_id");
CREATE INDEX "idx_order_items_product" ON "order_items" ("product_id");
CREATE INDEX "idx_purchase_receipts_supplier" ON "purchase_receipts" ("supplier_id");
CREATE INDEX "idx_purchase_receipts_manager" ON "purchase_receipts" ("warehouse_manager_id");
CREATE INDEX "idx_purchase_receipt_items_material" ON "purchase_receipt_items" ("material_id");

CREATE UNIQUE INDEX "uq_inventories_material" ON "inventories" ("material_id");
CREATE UNIQUE INDEX "uq_inventories_product" ON "inventories" ("product_id");

CREATE UNIQUE INDEX ON "boms" ("product_id", "version");
CREATE UNIQUE INDEX "uq_boms_one_active" ON "boms" ("product_id") WHERE is_active;
ALTER TABLE "boms" ADD CONSTRAINT uq_boms_id_product UNIQUE ("id", "product_id");

CREATE UNIQUE INDEX ON "bom_items" ("bom_id", "material_id");
CREATE INDEX "idx_bom_items_material" ON "bom_items" ("material_id");

CREATE INDEX "idx_production_requests_manager" ON "production_requests" ("warehouse_manager_id");
CREATE INDEX "idx_production_requests_employee" ON "production_requests" ("assigned_to");
CREATE INDEX "idx_production_requests_bom" ON "production_requests" ("bom_id");

CREATE INDEX "idx_material_issue_requests_production" ON "material_issue_requests" ("production_request_id");
CREATE INDEX "idx_material_issue_requests_employee" ON "material_issue_requests" ("requested_by");
CREATE INDEX "idx_material_issue_requests_manager" ON "material_issue_requests" ("reviewed_by");

CREATE UNIQUE INDEX ON "material_issue_request_items" ("request_id", "material_id");
CREATE INDEX "idx_material_issue_items_material" ON "material_issue_request_items" ("material_id");

CREATE INDEX "idx_productions_request" ON "productions" ("production_request_id");
CREATE INDEX "idx_productions_employee" ON "productions" ("produced_by");

CREATE INDEX "idx_finished_goods_issue_order" ON "finished_goods_issue_requests" ("order_id");
CREATE INDEX "idx_finished_goods_issue_employee" ON "finished_goods_issue_requests" ("requested_by");
CREATE INDEX "idx_finished_goods_issue_manager" ON "finished_goods_issue_requests" ("reviewed_by");
CREATE UNIQUE INDEX "uq_fgir_active_per_order" ON "finished_goods_issue_requests" ("order_id")
  WHERE status IN ('PENDING','APPROVED');

CREATE UNIQUE INDEX ON "finished_goods_issue_request_items" ("request_id", "product_id");
CREATE INDEX "idx_finished_goods_issue_items_product" ON "finished_goods_issue_request_items" ("product_id");

CREATE INDEX "idx_stock_movements_material" ON "stock_movements" ("material_id");
CREATE INDEX "idx_stock_movements_product" ON "stock_movements" ("product_id");
CREATE INDEX "idx_stock_movements_created_by" ON "stock_movements" ("created_by");
CREATE INDEX "idx_stock_movements_created_at" ON "stock_movements" ("created_at");
CREATE UNIQUE INDEX "uq_sm_purchase" ON "stock_movements" ("purchase_receipt_id", "material_id") WHERE purchase_receipt_id IS NOT NULL;
CREATE UNIQUE INDEX "uq_sm_mat_issue" ON "stock_movements" ("material_issue_request_id", "material_id") WHERE material_issue_request_id IS NOT NULL;
CREATE UNIQUE INDEX "uq_sm_production" ON "stock_movements" ("production_id") WHERE production_id IS NOT NULL;
CREATE UNIQUE INDEX "uq_sm_fg_issue" ON "stock_movements" ("fg_issue_request_id", "product_id") WHERE fg_issue_request_id IS NOT NULL;

CREATE INDEX "idx_product_discounts_product" ON "product_discounts" ("product_id");
CREATE UNIQUE INDEX "uq_pd_program_product" ON "product_discounts" ("promotion_program_id", "product_id");
CREATE INDEX "idx_order_discounts_program" ON "order_discounts" ("promotion_program_id");
CREATE UNIQUE INDEX "uq_od_program_min" ON "order_discounts" ("promotion_program_id", "minimum_amount");
CREATE INDEX "idx_vouchers_code" ON "vouchers" ("code");

ALTER TABLE "customers" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "staff" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "warehouse_managers" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "admins" ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "products" ADD FOREIGN KEY ("category_id") REFERENCES "categories" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "carts" ADD FOREIGN KEY ("customer_id") REFERENCES "customers" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "cart_items" ADD FOREIGN KEY ("cart_id") REFERENCES "carts" ("id") ON DELETE CASCADE DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "cart_items" ADD FOREIGN KEY ("product_id") REFERENCES "products" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "orders" ADD FOREIGN KEY ("customer_id") REFERENCES "customers" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "orders" ADD FOREIGN KEY ("staff_id") REFERENCES "staff" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "orders" ADD FOREIGN KEY ("voucher_id") REFERENCES "vouchers" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "order_items" ADD FOREIGN KEY ("order_id") REFERENCES "orders" ("id") ON DELETE CASCADE DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "order_items" ADD FOREIGN KEY ("product_id") REFERENCES "products" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "purchase_receipts" ADD FOREIGN KEY ("supplier_id") REFERENCES "suppliers" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "purchase_receipts" ADD FOREIGN KEY ("warehouse_manager_id") REFERENCES "warehouse_managers" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "purchase_receipt_items" ADD FOREIGN KEY ("purchase_receipt_id") REFERENCES "purchase_receipts" ("id") ON DELETE CASCADE DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "purchase_receipt_items" ADD FOREIGN KEY ("material_id") REFERENCES "materials" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "inventories" ADD FOREIGN KEY ("material_id") REFERENCES "materials" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "inventories" ADD FOREIGN KEY ("product_id") REFERENCES "products" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "boms" ADD FOREIGN KEY ("product_id") REFERENCES "products" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "bom_items" ADD FOREIGN KEY ("bom_id") REFERENCES "boms" ("id") ON DELETE CASCADE DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "bom_items" ADD FOREIGN KEY ("material_id") REFERENCES "materials" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "production_requests" ADD FOREIGN KEY ("product_id") REFERENCES "products" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "production_requests" ADD FOREIGN KEY ("warehouse_manager_id") REFERENCES "warehouse_managers" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "production_requests" ADD FOREIGN KEY ("assigned_to") REFERENCES "staff" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "production_requests" ADD CONSTRAINT fk_pr_bom FOREIGN KEY ("bom_id", "product_id") REFERENCES "boms" ("id", "product_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "material_issue_requests" ADD FOREIGN KEY ("production_request_id") REFERENCES "production_requests" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "material_issue_requests" ADD FOREIGN KEY ("requested_by") REFERENCES "staff" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "material_issue_requests" ADD FOREIGN KEY ("reviewed_by") REFERENCES "warehouse_managers" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "material_issue_request_items" ADD FOREIGN KEY ("request_id") REFERENCES "material_issue_requests" ("id") ON DELETE CASCADE DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "material_issue_request_items" ADD FOREIGN KEY ("material_id") REFERENCES "materials" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "productions" ADD FOREIGN KEY ("production_request_id") REFERENCES "production_requests" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "productions" ADD FOREIGN KEY ("produced_by") REFERENCES "staff" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "finished_goods_issue_requests" ADD FOREIGN KEY ("order_id") REFERENCES "orders" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "finished_goods_issue_requests" ADD FOREIGN KEY ("requested_by") REFERENCES "staff" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "finished_goods_issue_requests" ADD FOREIGN KEY ("reviewed_by") REFERENCES "warehouse_managers" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "finished_goods_issue_request_items" ADD FOREIGN KEY ("request_id") REFERENCES "finished_goods_issue_requests" ("id") ON DELETE CASCADE DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "finished_goods_issue_request_items" ADD FOREIGN KEY ("product_id") REFERENCES "products" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "stock_movements" ADD FOREIGN KEY ("material_id") REFERENCES "materials" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "stock_movements" ADD FOREIGN KEY ("product_id") REFERENCES "products" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "stock_movements" ADD FOREIGN KEY ("created_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "stock_movements" ADD FOREIGN KEY ("purchase_receipt_id") REFERENCES "purchase_receipts" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "stock_movements" ADD FOREIGN KEY ("material_issue_request_id") REFERENCES "material_issue_requests" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "stock_movements" ADD FOREIGN KEY ("production_id") REFERENCES "productions" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "stock_movements" ADD FOREIGN KEY ("fg_issue_request_id") REFERENCES "finished_goods_issue_requests" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "stock_movements" ADD CONSTRAINT ck_sm_source CHECK (
  (material_id IS NOT NULL AND (
       (movement_type = 'IN'  AND purchase_receipt_id IS NOT NULL AND num_nonnulls(material_issue_request_id, production_id, fg_issue_request_id) = 0)
    OR (movement_type = 'OUT' AND material_issue_request_id IS NOT NULL AND num_nonnulls(purchase_receipt_id, production_id, fg_issue_request_id) = 0)
    OR (movement_type LIKE 'ADJUST_%' AND num_nonnulls(purchase_receipt_id, material_issue_request_id, production_id, fg_issue_request_id) = 0)))
  OR
  (product_id IS NOT NULL AND (
       (movement_type = 'IN'  AND production_id IS NOT NULL AND num_nonnulls(purchase_receipt_id, material_issue_request_id, fg_issue_request_id) = 0)
    OR (movement_type = 'OUT' AND fg_issue_request_id IS NOT NULL AND num_nonnulls(purchase_receipt_id, material_issue_request_id, production_id) = 0)
    OR (movement_type LIKE 'ADJUST_%' AND num_nonnulls(purchase_receipt_id, material_issue_request_id, production_id, fg_issue_request_id) = 0)))
);

ALTER TABLE "product_discounts" ADD FOREIGN KEY ("promotion_program_id") REFERENCES "promotion_programs" ("id") ON DELETE CASCADE DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "product_discounts" ADD FOREIGN KEY ("product_id") REFERENCES "products" ("id") DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "order_discounts" ADD FOREIGN KEY ("promotion_program_id") REFERENCES "promotion_programs" ("id") ON DELETE CASCADE DEFERRABLE INITIALLY IMMEDIATE;
ALTER TABLE "vouchers" ADD FOREIGN KEY ("promotion_program_id") REFERENCES "promotion_programs" ("id") ON DELETE CASCADE DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "orders" ADD CONSTRAINT ck_orders_status CHECK (status IN ('PENDING','CONFIRMED','COMPLETED','CANCELLED'));
ALTER TABLE "orders" ADD CONSTRAINT ck_orders_staff CHECK (status IN ('PENDING','CANCELLED') OR staff_id IS NOT NULL);
ALTER TABLE "order_items" ADD CONSTRAINT ck_oi_amount CHECK (amount = quantity * unit_price);

ALTER TABLE "purchase_receipts" ADD CONSTRAINT ck_pr_status CHECK (status IN ('PENDING','COMPLETED','CANCELLED'));
ALTER TABLE "purchase_receipt_items" ADD CONSTRAINT ck_pri_amount CHECK (amount = round(quantity * unit_price, 2));

ALTER TABLE "material_issue_requests" ADD CONSTRAINT ck_mir_review CHECK (
  (status IN ('APPROVED','REJECTED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL) OR
  (status IN ('PENDING','CANCELLED') AND reviewed_by IS NULL AND reviewed_at IS NULL));

ALTER TABLE "finished_goods_issue_requests" ADD CONSTRAINT ck_fgir_review CHECK (
  (status IN ('APPROVED','REJECTED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL) OR
  (status IN ('PENDING','CANCELLED') AND reviewed_by IS NULL AND reviewed_at IS NULL));

ALTER TABLE "productions" ADD CONSTRAINT ck_prod_done CHECK (
  (status = 'COMPLETED') = (completed_at IS NOT NULL)
  AND (completed_at IS NULL OR completed_at >= started_at)
);

ALTER TABLE "production_requests" ADD CONSTRAINT ck_pr_assigned CHECK (
  status IN ('PENDING','CANCELLED') OR assigned_to IS NOT NULL
);

ALTER TABLE "promotion_programs" ADD CONSTRAINT ck_pp_status CHECK (status IN ('DRAFT','ACTIVE','INACTIVE'));
ALTER TABLE "vouchers" ADD CONSTRAINT ck_v_status CHECK (status IN ('ACTIVE','INACTIVE'));

CREATE FUNCTION fn_apply_stock_movement() RETURNS trigger AS $$
DECLARE d NUMERIC(15,3);
BEGIN
  d := CASE WHEN NEW.movement_type IN ('IN','ADJUST_IN') THEN NEW.quantity ELSE -NEW.quantity END;
  IF NEW.material_id IS NOT NULL THEN
    INSERT INTO inventories(material_id, quantity) VALUES (NEW.material_id, 0)
      ON CONFLICT (material_id) DO NOTHING;
    UPDATE inventories SET quantity = quantity + d, updated_at = now() WHERE material_id = NEW.material_id;
  ELSE
    INSERT INTO inventories(product_id, quantity) VALUES (NEW.product_id, 0)
      ON CONFLICT (product_id) DO NOTHING;
    UPDATE inventories SET quantity = quantity + d, updated_at = now() WHERE product_id = NEW.product_id;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sm_apply AFTER INSERT ON stock_movements
  FOR EACH ROW EXECUTE FUNCTION fn_apply_stock_movement();

CREATE FUNCTION fn_sm_append_only() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'stock_movements is append-only: corrections must be posted as a new ADJUST_IN/ADJUST_OUT row';
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sm_ro BEFORE UPDATE OR DELETE ON stock_movements
  FOR EACH ROW EXECUTE FUNCTION fn_sm_append_only();

-- =====================================================================
-- END OF SCHEMA
-- =====================================================================

-- =====================================================================
-- PHAN VA BAT BUOC (A1-A3, B2, B3): rang buoc kho / MRP
-- =====================================================================
-- ===== PATCH 2: movement phai khop dong chi tiet cua chung tu =====
ALTER TABLE stock_movements ADD CONSTRAINT fk_sm_receipt_item
  FOREIGN KEY (purchase_receipt_id, material_id) REFERENCES purchase_receipt_items (purchase_receipt_id, material_id);
ALTER TABLE stock_movements ADD CONSTRAINT fk_sm_mir_item
  FOREIGN KEY (material_issue_request_id, material_id) REFERENCES material_issue_request_items (request_id, material_id);
ALTER TABLE stock_movements ADD CONSTRAINT fk_sm_fgir_item
  FOREIGN KEY (fg_issue_request_id, product_id) REFERENCES finished_goods_issue_request_items (request_id, product_id);

-- ===== PATCH 3: fg issue item phai thuoc order =====
ALTER TABLE finished_goods_issue_requests ADD CONSTRAINT uq_fgir_id_order UNIQUE (id, order_id);
ALTER TABLE finished_goods_issue_request_items ADD COLUMN order_id BIGINT NOT NULL;
ALTER TABLE finished_goods_issue_request_items ADD CONSTRAINT fk_fgiri_request_order
  FOREIGN KEY (request_id, order_id) REFERENCES finished_goods_issue_requests (id, order_id);
ALTER TABLE finished_goods_issue_request_items ADD CONSTRAINT fk_fgiri_order_item
  FOREIGN KEY (order_id, product_id) REFERENCES order_items (order_id, product_id);

-- ===== PATCH 4: ADJUST bat buoc co ly do =====
ALTER TABLE stock_movements ADD CONSTRAINT ck_sm_adjust_note
  CHECK (movement_type NOT LIKE 'ADJUST_%' OR (note IS NOT NULL AND length(trim(note)) > 0));

-- ===== PATCH 5: trigger kiem tra chung tu nguon (trang thai, so luong, san pham, nguoi thao tac) =====
CREATE FUNCTION fn_sm_validate() RETURNS trigger AS $$
DECLARE v_status TEXT; v_qty NUMERIC; v_prod BIGINT; v_role TEXT;
BEGIN
  SELECT role INTO v_role FROM users WHERE id = NEW.created_by;

  IF NEW.purchase_receipt_id IS NOT NULL THEN
    SELECT r.status, i.quantity INTO v_status, v_qty
      FROM purchase_receipts r JOIN purchase_receipt_items i ON i.purchase_receipt_id = r.id
     WHERE r.id = NEW.purchase_receipt_id AND i.material_id = NEW.material_id;
    IF v_status <> 'COMPLETED' THEN RAISE EXCEPTION 'purchase_receipt % chua COMPLETED', NEW.purchase_receipt_id; END IF;
    IF v_qty <> NEW.quantity THEN RAISE EXCEPTION 'so luong movement (%) <> dong phieu nhap (%)', NEW.quantity, v_qty; END IF;

  ELSIF NEW.material_issue_request_id IS NOT NULL THEN
    SELECT r.status, i.quantity INTO v_status, v_qty
      FROM material_issue_requests r JOIN material_issue_request_items i ON i.request_id = r.id
     WHERE r.id = NEW.material_issue_request_id AND i.material_id = NEW.material_id;
    IF v_status <> 'APPROVED' THEN RAISE EXCEPTION 'material_issue_request % chua APPROVED', NEW.material_issue_request_id; END IF;
    IF v_qty <> NEW.quantity THEN RAISE EXCEPTION 'so luong movement (%) <> so luong duoc duyet (%)', NEW.quantity, v_qty; END IF;

  ELSIF NEW.production_id IS NOT NULL THEN
    SELECT p.status, p.quantity, pr.product_id INTO v_status, v_qty, v_prod
      FROM productions p JOIN production_requests pr ON pr.id = p.production_request_id
     WHERE p.id = NEW.production_id;
    IF v_status <> 'COMPLETED' THEN RAISE EXCEPTION 'production % chua COMPLETED', NEW.production_id; END IF;
    IF v_prod <> NEW.product_id THEN RAISE EXCEPTION 'movement.product_id (%) <> san pham cua production_request (%)', NEW.product_id, v_prod; END IF;
    IF v_qty <> NEW.quantity THEN RAISE EXCEPTION 'so luong movement (%) <> so luong san xuat (%)', NEW.quantity, v_qty; END IF;

  ELSIF NEW.fg_issue_request_id IS NOT NULL THEN
    SELECT r.status, i.quantity INTO v_status, v_qty
      FROM finished_goods_issue_requests r JOIN finished_goods_issue_request_items i ON i.request_id = r.id
     WHERE r.id = NEW.fg_issue_request_id AND i.product_id = NEW.product_id;
    IF v_status <> 'APPROVED' THEN RAISE EXCEPTION 'fg_issue_request % chua APPROVED', NEW.fg_issue_request_id; END IF;
    IF v_qty <> NEW.quantity THEN RAISE EXCEPTION 'so luong movement (%) <> so luong duoc duyet (%)', NEW.quantity, v_qty; END IF;
  END IF;

  -- nhap/xuat/dieu chinh kho do WM thuc hien; rieng nhap thanh pham tu san xuat co the do STAFF
  IF NOT (v_role = 'WAREHOUSE_MANAGER' OR (NEW.production_id IS NOT NULL AND v_role = 'STAFF')) THEN
    RAISE EXCEPTION 'user % (role %) khong duoc ghi stock_movements loai nay', NEW.created_by, v_role;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sm_validate BEFORE INSERT ON stock_movements
  FOR EACH ROW EXECUTE FUNCTION fn_sm_validate();

-- ===== PATCH 6 (optional): chan UPDATE truc tiep inventories, chi cho phep qua trigger cua ledger =====
CREATE FUNCTION fn_inv_guard() RETURNS trigger AS $$
BEGIN
  IF pg_trigger_depth() < 2 THEN
    RAISE EXCEPTION 'inventories chi duoc thay doi qua stock_movements';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;
CREATE TRIGGER trg_inv_guard BEFORE UPDATE ON inventories FOR EACH ROW EXECUTE FUNCTION fn_inv_guard();


-- ===== INDEX BO SUNG (tim kiem/loc cho Admin, join khi bao cao) =====
CREATE INDEX idx_production_requests_product ON production_requests (product_id);
CREATE INDEX idx_vouchers_program ON vouchers (promotion_program_id);
CREATE INDEX idx_orders_voucher ON orders (voucher_id);
CREATE INDEX idx_orders_status ON orders (status);

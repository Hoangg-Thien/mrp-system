CREATE TABLE "users" (
  "id" BIGSERIAL PRIMARY KEY,
  "username" "VARCHAR(50)" UNIQUE NOT NULL,
  "password_hash" "VARCHAR(255)" NOT NULL,
  "full_name" "VARCHAR(150)" NOT NULL,
  "email" "VARCHAR(150)" UNIQUE,
  "phone" "VARCHAR(20)",
  "address" TEXT,
  "role" "VARCHAR(30)" NOT NULL CHECK (role IN ('CUSTOMER', 'ADMIN', 'WAREHOUSE_MANAGER', 'EMPLOYEE')) DEFAULT 'CUSTOMER',
  "is_active" BOOLEAN NOT NULL DEFAULT true,
  "created_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE "categories" (
  "id" BIGSERIAL PRIMARY KEY,
  "name" "VARCHAR(150)" UNIQUE NOT NULL,
  "description" TEXT
);

CREATE TABLE "products" (
  "id" BIGSERIAL PRIMARY KEY,
  "category_id" BIGINT NOT NULL,
  "name" "VARCHAR(200)" NOT NULL,
  "description" TEXT,
  "unit" "VARCHAR(50)" NOT NULL,
  "selling_price" "NUMERIC(15,2)" NOT NULL CHECK (selling_price >= 0),
  "is_active" BOOLEAN NOT NULL DEFAULT true,
  "created_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE "orders" (
  "id" BIGSERIAL PRIMARY KEY,
  "customer_id" BIGINT NOT NULL,
  "created_by" BIGINT NOT NULL,
  "order_date" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  "status" "VARCHAR(30)" NOT NULL DEFAULT 'PENDING',
  "total_amount" "NUMERIC(15,2)" NOT NULL CHECK (total_amount >= 0) DEFAULT 0
);

CREATE TABLE "order_items" (
  "order_id" BIGINT NOT NULL,
  "product_id" BIGINT NOT NULL,
  "quantity" INTEGER NOT NULL CHECK (quantity > 0),
  "unit_price" "NUMERIC(15,2)" NOT NULL CHECK (unit_price >= 0),
  "amount" "NUMERIC(15,2)" NOT NULL CHECK (amount >= 0),
  PRIMARY KEY ("order_id", "product_id")
);

CREATE TABLE "suppliers" (
  "id" BIGSERIAL PRIMARY KEY,
  "name" "VARCHAR(200)" NOT NULL,
  "phone" "VARCHAR(20)",
  "email" "VARCHAR(150)",
  "address" TEXT
);

CREATE TABLE "purchase_receipts" (
  "id" BIGSERIAL PRIMARY KEY,
  "supplier_id" BIGINT NOT NULL,
  "created_by" BIGINT NOT NULL,
  "received_by" BIGINT,
  "receipt_date" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  "status" "VARCHAR(30)" NOT NULL DEFAULT 'PENDING',
  "total_amount" "NUMERIC(15,2)" NOT NULL CHECK (total_amount >= 0) DEFAULT 0
);

CREATE TABLE "materials" (
  "id" BIGSERIAL PRIMARY KEY,
  "code" "VARCHAR(50)" UNIQUE NOT NULL,
  "name" "VARCHAR(200)" NOT NULL,
  "unit" "VARCHAR(50)" NOT NULL,
  "standard_cost" "NUMERIC(15,2)" NOT NULL CHECK (standard_cost >= 0) DEFAULT 0,
  "is_active" BOOLEAN NOT NULL DEFAULT true,
  "created_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE "purchase_receipt_items" (
  "purchase_receipt_id" BIGINT NOT NULL,
  "material_id" BIGINT NOT NULL,
  "quantity" "NUMERIC(15,3)" NOT NULL CHECK (quantity > 0),
  "unit_price" "NUMERIC(15,2)" NOT NULL CHECK (unit_price >= 0),
  "amount" "NUMERIC(15,2)" NOT NULL CHECK (amount >= 0),
  PRIMARY KEY ("purchase_receipt_id", "material_id")
);

CREATE TABLE "inventories" (
  "id" BIGSERIAL PRIMARY KEY,
  "material_id" BIGINT,
  "product_id" BIGINT,
  "quantity" "NUMERIC(15,3)" NOT NULL CHECK (quantity >= 0) DEFAULT 0,
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
  "quantity" "NUMERIC(15,3)" NOT NULL CHECK (quantity > 0),
  "unit" "VARCHAR(50)" NOT NULL
);

CREATE TABLE "production_requests" (
  "id" BIGSERIAL PRIMARY KEY,
  "product_id" BIGINT NOT NULL,
  "quantity" INTEGER NOT NULL CHECK (quantity > 0),
  "created_by" BIGINT NOT NULL,
  "assigned_to" BIGINT,
  "status" "VARCHAR(30)" NOT NULL CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')) DEFAULT 'PENDING',
  "created_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  "note" TEXT
);

CREATE TABLE "material_issue_requests" (
  "id" BIGSERIAL PRIMARY KEY,
  "production_request_id" BIGINT NOT NULL,
  "requested_by" BIGINT NOT NULL,
  "approved_by" BIGINT,
  "status" "VARCHAR(30)" NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')) DEFAULT 'PENDING',
  "requested_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  "approved_at" TIMESTAMP,
  "note" TEXT
);

CREATE TABLE "material_issue_request_items" (
  "id" BIGSERIAL PRIMARY KEY,
  "request_id" BIGINT NOT NULL,
  "material_id" BIGINT NOT NULL,
  "quantity" "NUMERIC(15,3)" NOT NULL CHECK (quantity > 0)
);

CREATE TABLE "productions" (
  "id" BIGSERIAL PRIMARY KEY,
  "production_request_id" BIGINT NOT NULL,
  "product_id" BIGINT NOT NULL,
  "quantity" INTEGER NOT NULL CHECK (quantity > 0),
  "produced_by" BIGINT NOT NULL,
  "status" "VARCHAR(30)" NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'CANCELLED')) DEFAULT 'IN_PROGRESS',
  "started_at" TIMESTAMP,
  "completed_at" TIMESTAMP,
  "note" TEXT
);

CREATE TABLE "finished_goods_issue_requests" (
  "id" BIGSERIAL PRIMARY KEY,
  "order_id" BIGINT,
  "requested_by" BIGINT NOT NULL,
  "approved_by" BIGINT,
  "status" "VARCHAR(30)" NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')) DEFAULT 'PENDING',
  "requested_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  "approved_at" TIMESTAMP,
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
  "movement_type" "VARCHAR(20)" NOT NULL CHECK (movement_type IN ('IN', 'OUT', 'ADJUSTMENT')),
  "quantity" "NUMERIC(15,3)" NOT NULL CHECK (quantity > 0),
  "reference_type" "VARCHAR(50)",
  "reference_id" BIGINT,
  "created_by" BIGINT NOT NULL,
  "created_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  "note" TEXT,
  CHECK ((material_id IS NOT NULL AND product_id IS NULL) OR (material_id IS NULL AND product_id IS NOT NULL))
);

CREATE INDEX "idx_products_category" ON "products" ("category_id");

CREATE INDEX "idx_orders_customer" ON "orders" ("customer_id");

CREATE INDEX "idx_orders_created_by" ON "orders" ("created_by");

CREATE INDEX "idx_order_items_order" ON "order_items" ("order_id");

CREATE INDEX "idx_order_items_product" ON "order_items" ("product_id");

CREATE INDEX "idx_purchase_receipts_supplier" ON "purchase_receipts" ("supplier_id");

CREATE INDEX "idx_purchase_receipts_created_by" ON "purchase_receipts" ("created_by");

CREATE INDEX "idx_purchase_receipts_received_by" ON "purchase_receipts" ("received_by");

CREATE INDEX "idx_purchase_receipt_items_receipt" ON "purchase_receipt_items" ("purchase_receipt_id");

CREATE INDEX "idx_purchase_receipt_items_material" ON "purchase_receipt_items" ("material_id");

CREATE UNIQUE INDEX "uq_inventories_material" ON "inventories" ("material_id");

CREATE UNIQUE INDEX "uq_inventories_product" ON "inventories" ("product_id");

CREATE INDEX "idx_boms_product" ON "boms" ("product_id");

CREATE UNIQUE INDEX ON "boms" ("product_id", "version");

CREATE UNIQUE INDEX ON "bom_items" ("bom_id", "material_id");

CREATE INDEX "idx_bom_items_bom" ON "bom_items" ("bom_id");

CREATE INDEX "idx_bom_items_material" ON "bom_items" ("material_id");

CREATE INDEX "idx_production_requests_product" ON "production_requests" ("product_id");

CREATE INDEX "idx_production_requests_created_by" ON "production_requests" ("created_by");

CREATE INDEX "idx_production_requests_assigned_to" ON "production_requests" ("assigned_to");

CREATE INDEX "idx_material_issue_requests_production" ON "material_issue_requests" ("production_request_id");

CREATE INDEX "idx_material_issue_requests_requested_by" ON "material_issue_requests" ("requested_by");

CREATE INDEX "idx_material_issue_requests_approved_by" ON "material_issue_requests" ("approved_by");

CREATE UNIQUE INDEX ON "material_issue_request_items" ("request_id", "material_id");

CREATE INDEX "idx_material_issue_items_request" ON "material_issue_request_items" ("request_id");

CREATE INDEX "idx_material_issue_items_material" ON "material_issue_request_items" ("material_id");

CREATE INDEX "idx_productions_request" ON "productions" ("production_request_id");

CREATE INDEX "idx_productions_product" ON "productions" ("product_id");

CREATE INDEX "idx_productions_produced_by" ON "productions" ("produced_by");

CREATE INDEX "idx_finished_goods_issue_order" ON "finished_goods_issue_requests" ("order_id");

CREATE INDEX "idx_finished_goods_issue_requested_by" ON "finished_goods_issue_requests" ("requested_by");

CREATE INDEX "idx_finished_goods_issue_approved_by" ON "finished_goods_issue_requests" ("approved_by");

CREATE UNIQUE INDEX ON "finished_goods_issue_request_items" ("request_id", "product_id");

CREATE INDEX "idx_finished_goods_issue_items_request" ON "finished_goods_issue_request_items" ("request_id");

CREATE INDEX "idx_finished_goods_issue_items_product" ON "finished_goods_issue_request_items" ("product_id");

CREATE INDEX "idx_stock_movements_material" ON "stock_movements" ("material_id");

CREATE INDEX "idx_stock_movements_product" ON "stock_movements" ("product_id");

CREATE INDEX "idx_stock_movements_created_by" ON "stock_movements" ("created_by");

CREATE INDEX "idx_stock_movements_created_at" ON "stock_movements" ("created_at");

ALTER TABLE "products" ADD FOREIGN KEY ("category_id") REFERENCES "categories" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "orders" ADD FOREIGN KEY ("customer_id") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "orders" ADD FOREIGN KEY ("created_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "order_items" ADD FOREIGN KEY ("order_id") REFERENCES "orders" ("id") ON DELETE CASCADE DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "order_items" ADD FOREIGN KEY ("product_id") REFERENCES "products" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "purchase_receipts" ADD FOREIGN KEY ("supplier_id") REFERENCES "suppliers" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "purchase_receipts" ADD FOREIGN KEY ("created_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "purchase_receipts" ADD FOREIGN KEY ("received_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "purchase_receipt_items" ADD FOREIGN KEY ("purchase_receipt_id") REFERENCES "purchase_receipts" ("id") ON DELETE CASCADE DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "purchase_receipt_items" ADD FOREIGN KEY ("material_id") REFERENCES "materials" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "inventories" ADD FOREIGN KEY ("material_id") REFERENCES "materials" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "inventories" ADD FOREIGN KEY ("product_id") REFERENCES "products" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "boms" ADD FOREIGN KEY ("product_id") REFERENCES "products" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "bom_items" ADD FOREIGN KEY ("bom_id") REFERENCES "boms" ("id") ON DELETE CASCADE DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "bom_items" ADD FOREIGN KEY ("material_id") REFERENCES "materials" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "production_requests" ADD FOREIGN KEY ("product_id") REFERENCES "products" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "production_requests" ADD FOREIGN KEY ("created_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "production_requests" ADD FOREIGN KEY ("assigned_to") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "material_issue_requests" ADD FOREIGN KEY ("production_request_id") REFERENCES "production_requests" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "material_issue_requests" ADD FOREIGN KEY ("requested_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "material_issue_requests" ADD FOREIGN KEY ("approved_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "material_issue_request_items" ADD FOREIGN KEY ("request_id") REFERENCES "material_issue_requests" ("id") ON DELETE CASCADE DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "material_issue_request_items" ADD FOREIGN KEY ("material_id") REFERENCES "materials" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "productions" ADD FOREIGN KEY ("production_request_id") REFERENCES "production_requests" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "productions" ADD FOREIGN KEY ("product_id") REFERENCES "products" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "productions" ADD FOREIGN KEY ("produced_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "finished_goods_issue_requests" ADD FOREIGN KEY ("order_id") REFERENCES "orders" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "finished_goods_issue_requests" ADD FOREIGN KEY ("requested_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "finished_goods_issue_requests" ADD FOREIGN KEY ("approved_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "finished_goods_issue_request_items" ADD FOREIGN KEY ("request_id") REFERENCES "finished_goods_issue_requests" ("id") ON DELETE CASCADE DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "finished_goods_issue_request_items" ADD FOREIGN KEY ("product_id") REFERENCES "products" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "stock_movements" ADD FOREIGN KEY ("material_id") REFERENCES "materials" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "stock_movements" ADD FOREIGN KEY ("product_id") REFERENCES "products" ("id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "stock_movements" ADD FOREIGN KEY ("created_by") REFERENCES "users" ("id") DEFERRABLE INITIALLY IMMEDIATE;

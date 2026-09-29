-- =====================================================================
-- ERD-READY DDL FOR DBDATAGRAM.IO
-- =====================================================================

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
  "role" VARCHAR(30) NOT NULL DEFAULT 'CUSTOMER' CHECK (role = 'CUSTOMER'),
  "created_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE "staff" (
  "id" BIGSERIAL PRIMARY KEY,
  "user_id" BIGINT UNIQUE NOT NULL,
  "full_name" VARCHAR(150) NOT NULL,
  "email" VARCHAR(150) UNIQUE,
  "phone" VARCHAR(20),
  "address" TEXT,
  "role" VARCHAR(30) NOT NULL DEFAULT 'STAFF' CHECK (role = 'STAFF'),
  "created_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE "warehouse_managers" (
  "id" BIGSERIAL PRIMARY KEY,
  "user_id" BIGINT UNIQUE NOT NULL,
  "full_name" VARCHAR(150) NOT NULL,
  "email" VARCHAR(150) UNIQUE,
  "phone" VARCHAR(20),
  "address" TEXT,
  "role" VARCHAR(30) NOT NULL DEFAULT 'WAREHOUSE_MANAGER' CHECK (role = 'WAREHOUSE_MANAGER'),
  "created_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE "admins" (
  "id" BIGSERIAL PRIMARY KEY,
  "user_id" BIGINT UNIQUE NOT NULL,
  "full_name" VARCHAR(150) NOT NULL,
  "email" VARCHAR(150) UNIQUE,
  "phone" VARCHAR(20),
  "address" TEXT,
  "role" VARCHAR(30) NOT NULL DEFAULT 'ADMIN' CHECK (role = 'ADMIN'),
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
  "material_id" BIGINT UNIQUE,
  "product_id" BIGINT UNIQUE,
  "quantity" NUMERIC(15,3) NOT NULL CHECK (quantity >= 0) DEFAULT 0,
  "updated_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE "boms" (
  "id" BIGSERIAL PRIMARY KEY,
  "product_id" BIGINT NOT NULL,
  "version" INTEGER NOT NULL CHECK (version > 0) DEFAULT 1,
  "is_active" BOOLEAN NOT NULL DEFAULT true,
  "created_at" TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  CONSTRAINT uq_boms_id_product UNIQUE ("id", "product_id")
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
  "quantity" NUMERIC(15,3) NOT NULL CHECK (quantity > 0),
  CONSTRAINT uq_miri_request_material UNIQUE ("request_id", "material_id")
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
  "note" TEXT,
  CONSTRAINT uq_fgir_id_order UNIQUE ("id", "order_id")
);

CREATE TABLE "finished_goods_issue_request_items" (
  "id" BIGSERIAL PRIMARY KEY,
  "request_id" BIGINT NOT NULL,
  "order_id" BIGINT NOT NULL,
  "product_id" BIGINT NOT NULL,
  "quantity" INTEGER NOT NULL CHECK (quantity > 0),
  CONSTRAINT uq_fgiri_request_product UNIQUE ("request_id", "product_id")
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
  "note" TEXT
);

CREATE TABLE "promotion_programs" (
  "id" BIGSERIAL PRIMARY KEY,
  "name" VARCHAR(200) NOT NULL,
  "description" TEXT,
  "start_date" TIMESTAMP NOT NULL,
  "end_date" TIMESTAMP NOT NULL,
  "status" VARCHAR(30) NOT NULL CHECK (status IN ('DRAFT','ACTIVE','INACTIVE')) DEFAULT 'DRAFT'
);

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
  "status" VARCHAR(30) NOT NULL CHECK (status IN ('ACTIVE','INACTIVE')) DEFAULT 'ACTIVE'
);

-- =====================================================================
-- COMPOSITE / UNIQUE CONSTRAINTS CHO FOREIGN KEYS
-- =====================================================================
ALTER TABLE "users" ADD CONSTRAINT uq_users_id_role UNIQUE ("id", "role");

-- =====================================================================
-- FOREIGN KEY CONSTRAINTS (Tạo đường liên kết trong ERD)
-- =====================================================================

-- Role profile hierarchy
ALTER TABLE "customers" ADD FOREIGN KEY ("user_id", "role") REFERENCES "users" ("id", "role");
ALTER TABLE "staff" ADD FOREIGN KEY ("user_id", "role") REFERENCES "users" ("id", "role");
ALTER TABLE "warehouse_managers" ADD FOREIGN KEY ("user_id", "role") REFERENCES "users" ("id", "role");
ALTER TABLE "admins" ADD FOREIGN KEY ("user_id", "role") REFERENCES "users" ("id", "role");

-- Products & Categories
ALTER TABLE "products" ADD FOREIGN KEY ("category_id") REFERENCES "categories" ("id");

-- Cart & Order
ALTER TABLE "carts" ADD FOREIGN KEY ("customer_id") REFERENCES "customers" ("id");
ALTER TABLE "cart_items" ADD FOREIGN KEY ("cart_id") REFERENCES "carts" ("id") ON DELETE CASCADE;
ALTER TABLE "cart_items" ADD FOREIGN KEY ("product_id") REFERENCES "products" ("id");

ALTER TABLE "orders" ADD FOREIGN KEY ("customer_id") REFERENCES "customers" ("id");
ALTER TABLE "orders" ADD FOREIGN KEY ("staff_id") REFERENCES "staff" ("id");
ALTER TABLE "orders" ADD FOREIGN KEY ("voucher_id") REFERENCES "vouchers" ("id");
ALTER TABLE "order_items" ADD FOREIGN KEY ("order_id") REFERENCES "orders" ("id") ON DELETE CASCADE;
ALTER TABLE "order_items" ADD FOREIGN KEY ("product_id") REFERENCES "products" ("id");

-- Purchase
ALTER TABLE "purchase_receipts" ADD FOREIGN KEY ("supplier_id") REFERENCES "suppliers" ("id");
ALTER TABLE "purchase_receipts" ADD FOREIGN KEY ("warehouse_manager_id") REFERENCES "warehouse_managers" ("id");
ALTER TABLE "purchase_receipt_items" ADD FOREIGN KEY ("purchase_receipt_id") REFERENCES "purchase_receipts" ("id") ON DELETE CASCADE;
ALTER TABLE "purchase_receipt_items" ADD FOREIGN KEY ("material_id") REFERENCES "materials" ("id");

-- Inventories
ALTER TABLE "inventories" ADD FOREIGN KEY ("material_id") REFERENCES "materials" ("id");
ALTER TABLE "inventories" ADD FOREIGN KEY ("product_id") REFERENCES "products" ("id");

-- BOM
ALTER TABLE "boms" ADD FOREIGN KEY ("product_id") REFERENCES "products" ("id");
ALTER TABLE "bom_items" ADD FOREIGN KEY ("bom_id") REFERENCES "boms" ("id") ON DELETE CASCADE;
ALTER TABLE "bom_items" ADD FOREIGN KEY ("material_id") REFERENCES "materials" ("id");

-- Production
ALTER TABLE "production_requests" ADD FOREIGN KEY ("product_id") REFERENCES "products" ("id");
ALTER TABLE "production_requests" ADD FOREIGN KEY ("warehouse_manager_id") REFERENCES "warehouse_managers" ("id");
ALTER TABLE "production_requests" ADD FOREIGN KEY ("assigned_to") REFERENCES "staff" ("id");
ALTER TABLE "production_requests" ADD FOREIGN KEY ("bom_id", "product_id") REFERENCES "boms" ("id", "product_id");

ALTER TABLE "material_issue_requests" ADD FOREIGN KEY ("production_request_id") REFERENCES "production_requests" ("id");
ALTER TABLE "material_issue_requests" ADD FOREIGN KEY ("requested_by") REFERENCES "staff" ("id");
ALTER TABLE "material_issue_requests" ADD FOREIGN KEY ("reviewed_by") REFERENCES "warehouse_managers" ("id");
ALTER TABLE "material_issue_request_items" ADD FOREIGN KEY ("request_id") REFERENCES "material_issue_requests" ("id") ON DELETE CASCADE;
ALTER TABLE "material_issue_request_items" ADD FOREIGN KEY ("material_id") REFERENCES "materials" ("id");

ALTER TABLE "productions" ADD FOREIGN KEY ("production_request_id") REFERENCES "production_requests" ("id");
ALTER TABLE "productions" ADD FOREIGN KEY ("produced_by") REFERENCES "staff" ("id");

-- Finished Goods Issue
ALTER TABLE "finished_goods_issue_requests" ADD FOREIGN KEY ("order_id") REFERENCES "orders" ("id");
ALTER TABLE "finished_goods_issue_requests" ADD FOREIGN KEY ("requested_by") REFERENCES "staff" ("id");
ALTER TABLE "finished_goods_issue_requests" ADD FOREIGN KEY ("reviewed_by") REFERENCES "warehouse_managers" ("id");
ALTER TABLE "finished_goods_issue_request_items" ADD FOREIGN KEY ("request_id", "order_id") REFERENCES "finished_goods_issue_requests" ("id", "order_id");
ALTER TABLE "finished_goods_issue_request_items" ADD FOREIGN KEY ("order_id", "product_id") REFERENCES "order_items" ("order_id", "product_id");

-- Stock Movements
ALTER TABLE "stock_movements" ADD FOREIGN KEY ("material_id") REFERENCES "materials" ("id");
ALTER TABLE "stock_movements" ADD FOREIGN KEY ("product_id") REFERENCES "products" ("id");
ALTER TABLE "stock_movements" ADD FOREIGN KEY ("created_by") REFERENCES "users" ("id");
ALTER TABLE "stock_movements" ADD FOREIGN KEY ("purchase_receipt_id", "material_id") REFERENCES "purchase_receipt_items" ("purchase_receipt_id", "material_id");
ALTER TABLE "stock_movements" ADD FOREIGN KEY ("material_issue_request_id", "material_id") REFERENCES "material_issue_request_items" ("request_id", "material_id");
ALTER TABLE "stock_movements" ADD FOREIGN KEY ("production_id") REFERENCES "productions" ("id");
ALTER TABLE "stock_movements" ADD FOREIGN KEY ("fg_issue_request_id", "product_id") REFERENCES "finished_goods_issue_request_items" ("request_id", "product_id");

-- Promotions & Vouchers
ALTER TABLE "product_discounts" ADD FOREIGN KEY ("promotion_program_id") REFERENCES "promotion_programs" ("id") ON DELETE CASCADE;
ALTER TABLE "product_discounts" ADD FOREIGN KEY ("product_id") REFERENCES "products" ("id");
ALTER TABLE "order_discounts" ADD FOREIGN KEY ("promotion_program_id") REFERENCES "promotion_programs" ("id") ON DELETE CASCADE;
ALTER TABLE "vouchers" ADD FOREIGN KEY ("promotion_program_id") REFERENCES "promotion_programs" ("id") ON DELETE CASCADE;
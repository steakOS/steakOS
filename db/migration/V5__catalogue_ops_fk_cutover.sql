-- Big-bang: ops FKs Product → CatalogueItem (dev/test; may truncate ops tables)
-- Applied manually to steakos_dev on 2026-08-05 when hibernate dbCreate:update
-- did not rename product_id → catalogue_item_id.

-- bill_of_materials
ALTER TABLE bill_of_materials DROP COLUMN IF EXISTS product_id;
ALTER TABLE bill_of_materials ADD COLUMN IF NOT EXISTS catalogue_item_id bigint;

-- bom_item
ALTER TABLE bom_item DROP COLUMN IF EXISTS component_product_id;
ALTER TABLE bom_item ADD COLUMN IF NOT EXISTS component_item_id bigint;

-- production_order
ALTER TABLE production_order DROP COLUMN IF EXISTS product_id;
ALTER TABLE production_order ADD COLUMN IF NOT EXISTS catalogue_item_id bigint;

-- lot
ALTER TABLE lot DROP COLUMN IF EXISTS product_id;
ALTER TABLE lot ADD COLUMN IF NOT EXISTS catalogue_item_id bigint;

-- component
ALTER TABLE component DROP COLUMN IF EXISTS product_id;
ALTER TABLE component ADD COLUMN IF NOT EXISTS catalogue_item_id bigint;

-- stock_move
ALTER TABLE stock_move DROP COLUMN IF EXISTS product_id;
ALTER TABLE stock_move ADD COLUMN IF NOT EXISTS catalogue_item_id bigint;

-- aftersales
ALTER TABLE rma DROP COLUMN IF EXISTS product_id;
ALTER TABLE warranty DROP COLUMN IF EXISTS product_id;
ALTER TABLE receipt_line DROP COLUMN IF EXISTS product_ref_id;

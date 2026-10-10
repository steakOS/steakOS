-- Drop legacy Product / Service masters and catalogue migration backlinks.
-- CatalogueItem is the sole commercial master (ops FKs already catalogue_item_id).
-- Dev/test: empty product/service tables; safe to drop.

-- Orphan column from pre-cutover ServiceOrder.service association
ALTER TABLE service_order DROP CONSTRAINT IF EXISTS fk2m1fn4id32tpsgvgh427cvyc8;
ALTER TABLE service_order DROP COLUMN IF EXISTS service_id;

-- Legacy masters
DROP TABLE IF EXISTS product CASCADE;
DROP TABLE IF EXISTS service CASCADE;

-- Catalogue migration plumbing (sourceType/sourceId)
DROP INDEX IF EXISTS catalogue_item_source_idx;
ALTER TABLE catalogue_item DROP COLUMN IF EXISTS source_type;
ALTER TABLE catalogue_item DROP COLUMN IF EXISTS source_id;

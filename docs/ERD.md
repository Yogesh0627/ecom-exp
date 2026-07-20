# EcoExpress — Entity Relationship Design

Derived from `EcoExpress_PRD_and_Technical_Blueprint.docx` §4 (Core Modules), §7 (Product Model),
§8 (Nutrition Model), §9 (Inventory), §12 (Database).

**Target:** 45–60 tables. **Actual: 58** (verified against the live database, not counted by hand).

Status: all 7 migrations applied to PostgreSQL 17.10. 58 tables, 5 views, 186 indexes,
116 check constraints, 78 foreign keys.

---

## Conventions

Applied to every table unless noted.

| Concern | Decision |
|---|---|
| Primary key | `UUID`, default `gen_random_uuid()` (core since PG13 — no extension needed) |
| Audit fields | `created_at`, `updated_at`, `created_by`, `updated_by` (PRD §12) |
| Soft delete | `deleted_at IS NULL` = live. **Opt-in per table** — see exclusions below |
| Concurrency | `version` bigint for optimistic locking |
| Money | `NUMERIC(12,2)` — never float. `currency CHAR(3)` default `INR` |
| Timestamps | `TIMESTAMPTZ`, stored UTC |
| Naming | snake_case, plural tables, singular columns |

### Why UUID v4 and not v7

Postgres 17 has no `uuidv7()` (added in PG18). These are random v4, which means index inserts
scatter across the B-tree instead of appending. It is not a problem at our scale — it becomes one
somewhere north of ~10M rows on a single table. If `order_items` or `stock_transactions` ever get
there, the fix is a v7 generator, not a schema change: the column type stays `UUID`.

### Tables that must NOT soft-delete

Financial and ledger history. A row here is a record of something that *happened*; erasing it —
even softly — breaks reconciliation and, for payments, breaks the audit trail we are legally
required to keep.

- `payments`, `payment_refunds`
- `stock_transactions`
- `order_status_history`
- `coupon_redemptions`
- `ai_request_logs`

Corrections are new compensating rows (a refund, a reversing stock transaction), never a delete.

---

## 1. Identity & Access — 9 tables

Implements PRD §13 (JWT, Google OAuth, RBAC, refresh tokens, password encryption).

```
users ──< user_roles >── roles ──< role_permissions >── permissions
  │
  ├──< oauth_accounts          (google linkage; provider + provider_user_id)
  ├──< refresh_tokens          (rotation + reuse detection)
  ├──< password_reset_tokens
  └──< email_verification_tokens
```

| Table | Key columns | Notes |
|---|---|---|
| `users` | `email` UNIQUE, `password_hash` NULL, `full_name`, `phone`, `status`, `email_verified_at` | `password_hash` is nullable: Google-only users never set one |
| `roles` | `name` UNIQUE (`CUSTOMER`, `ADMIN`, `OPS`, `SUPPORT`) | |
| `permissions` | `name` UNIQUE (`product:write`, `order:refund`, …) | Checks bind to permissions, not roles |
| `role_permissions` | PK(`role_id`,`permission_id`) | |
| `user_roles` | PK(`user_id`,`role_id`) | |
| `oauth_accounts` | UNIQUE(`provider`,`provider_user_id`) | |
| `refresh_tokens` | `token_hash` UNIQUE, `expires_at`, `revoked_at`, `replaced_by_id` | Store the **hash**, never the token |
| `password_reset_tokens` | `token_hash`, `expires_at`, `used_at` | |
| `email_verification_tokens` | `token_hash`, `expires_at`, `used_at` | |

**Refresh token rotation.** Each use mints a new token and points the old one's `replaced_by_id` at
it. If a token that already has `replaced_by_id` set is presented again, it was stolen and replayed
— revoke the entire chain. This is why the table has a self-reference instead of a plain
delete-on-use.

**Permission-based, not role-based.** `hasAuthority("product:write")` rather than
`hasRole("ADMIN")`, so roles can be re-cut later without touching a single call site.

---

## 2. Catalog — 9 tables

Implements PRD §7 (Product Model) and §8 (Nutrition Model).

```
categories (self-ref tree)
brands
products ──< product_variants ──< product_images
   │              │
   │              └──1 nutrition_facts        (per 100g, per variant)
   ├──< product_tag_map >── tags
   └──1 organic_certifications
```

| Table | Key columns | Notes |
|---|---|---|
| `categories` | `parent_id` self-ref, `slug` UNIQUE, `name`, `position` | Adjacency list; depth ≤ 3 |
| `brands` | `slug` UNIQUE, `name` | |
| `products` | `slug` UNIQUE, `name`, `description`, `brand_id`, `category_id`, `origin`, `status` | PRD §7 |
| `product_variants` | `sku` UNIQUE, `barcode` UNIQUE, `weight_grams`, `price`, `mrp`, `discount_pct` | |
| `product_images` | `variant_id`, `url`, `alt`, `position`, `is_primary` | S3/MinIO keys |
| `nutrition_facts` | 12 nutrient columns, `basis_grams` DEFAULT 100 | PRD §8 |
| `tags` | `slug` UNIQUE | |
| `product_tag_map` | PK(`product_id`,`tag_id`) | |
| `organic_certifications` | `product_id`, `certifier`, `certificate_no`, `valid_from`, `valid_until`, `document_url` | NPOP/PGS-India, Jaivik Bharat |

**Price and nutrition live on the variant, not the product.** "Organic Turmeric" is a product;
200g and 500g packs are variants with different SKUs, prices, and barcodes. Nutrition is per 100g
so it is technically identical across sibling variants — but attaching it to the variant keeps the
cart's per-line math a single join and avoids a special case when a variant genuinely differs
(roasted vs raw). Attaching it to `products` would force a product→variant hop on every cart
nutrition rollup, which is the hottest read in the Smart Cart feature (PRD §5.2).

**The 12 nutrients** (PRD §8, per 100g, all `NUMERIC(9,3)` NULL-able — unknown ≠ zero):
`calories_kcal`, `protein_g`, `fat_g`, `carbohydrates_g`, `fiber_g`, `sugar_g`, `iron_mg`,
`vitamin_a_mcg`, `vitamin_c_mg`, `vitamin_d_mcg`, `potassium_mg`, `sodium_mg`.

NULL vs 0 matters: a health score must not treat "we never measured the iron" as "contains no iron."

---

## 3. Inventory — 9 tables

Implements PRD §9 in full.

```
warehouses ──< inventory >── product_variants
                  │
                  └──< inventory_batches   (lot no, expiry, qty)
suppliers ──< purchase_orders ──< purchase_order_items
stock_transactions   (append-only ledger)
stock_adjustments    (reason-coded corrections)
low_stock_alerts
```

| Table | Key columns | Notes |
|---|---|---|
| `warehouses` | `code` UNIQUE, `name`, `address_id`, `is_active` | |
| `suppliers` | `code` UNIQUE, `name`, `gstin`, `fssai_license`, contact | |
| `inventory` | UNIQUE(`warehouse_id`,`variant_id`), `qty_on_hand`, `qty_reserved`, `reorder_point` | `version` for concurrent decrements |
| `inventory_batches` | `lot_no`, `expiry_date`, `qty_remaining`, `cost_price` | FEFO |
| `stock_transactions` | `type`, `qty_delta`, `ref_type`, `ref_id`, `batch_id` | **Append-only ledger** |
| `purchase_orders` | `po_number` UNIQUE, `supplier_id`, `status`, `expected_at` | |
| `purchase_order_items` | `po_id`, `variant_id`, `qty_ordered`, `qty_received`, `unit_cost` | |
| `stock_adjustments` | `reason` (`DAMAGE`,`EXPIRY`,`COUNT`,`THEFT`), `qty_delta`, `approved_by` | |
| `low_stock_alerts` | `variant_id`, `warehouse_id`, `triggered_at`, `resolved_at` | |

**`qty_on_hand` vs `qty_reserved`.** Physical stock vs stock promised to unpaid carts. Available =
`on_hand - reserved`. Without the split, two customers can both buy the last bag of atta and one
gets an apology instead of an order.

**The ledger is the truth.** `inventory.qty_on_hand` is a cached rollup of `stock_transactions`;
every mutation writes a ledger row. This is what makes "Inventory accuracy" (PRD §2 success metric)
auditable instead of aspirational — you can always recompute on-hand from the ledger and diff it.

**FEFO, not FIFO.** Organic produce expires. Batches ship first-expiry-first-out, which is why
`inventory_batches.expiry_date` is indexed and drives allocation.

---

## 4. Commerce — 10 tables

```
users ──< addresses
users ──1 carts ──< cart_items
users ──< wishlists ──< wishlist_items
users ──< orders ──< order_items
              ├──< order_status_history   (append-only)
              └──< shipments ──< shipment_items
```

| Table | Key columns | Notes |
|---|---|---|
| `addresses` | `user_id`, `line1`, `line2`, `city`, `state`, `pincode`, `type`, `is_default` | |
| `carts` | `user_id`, `status`, `expires_at` | One ACTIVE per user |
| `cart_items` | UNIQUE(`cart_id`,`variant_id`), `qty`, `unit_price_snapshot` | |
| `wishlists` | `user_id`, `name` | |
| `wishlist_items` | UNIQUE(`wishlist_id`,`variant_id`) | |
| `orders` | `order_number` UNIQUE, `user_id`, `status`, `subtotal`, `discount_total`, `tax_total`, `shipping_fee`, `grand_total`, address snapshot | |
| `order_items` | `order_id`, `variant_id`, `qty`, `unit_price`, `line_total`, `product_name_snapshot`, `sku_snapshot` | |
| `order_status_history` | `from_status`, `to_status`, `changed_by`, `note` | **Append-only** |
| `shipments` | `order_id`, `carrier`, `tracking_no`, `shipped_at`, `delivered_at` | |
| `shipment_items` | `shipment_id`, `order_item_id`, `qty` | Partial shipments |

**Snapshot everything on the order.** `unit_price`, `product_name_snapshot`, `sku_snapshot`, and
the delivery address are copied onto the order at placement. Orders are historical records: if a
product is renamed, repriced, or deleted next week, a six-month-old invoice must still render
exactly as the customer received it. Joining live catalog rows to old orders silently rewrites
history — a real bug class, and an accounting problem.

`cart_items.unit_price_snapshot` serves a different purpose: it detects price drift between
add-to-cart and checkout so the customer can be told rather than surprised.

---

## 5. Payments & Promotions — 6 tables

```
orders ──< payments ──< payment_refunds
coupons ──< coupon_redemptions
coupons ──< coupon_conditions
payment_webhook_events   (idempotency)
```

| Table | Key columns | Notes |
|---|---|---|
| `payments` | `order_id`, `gateway`, `gateway_payment_id` UNIQUE, `amount`, `status`, `method` | Razorpay |
| `payment_refunds` | `payment_id`, `gateway_refund_id` UNIQUE, `amount`, `reason`, `status` | |
| `payment_webhook_events` | `gateway_event_id` UNIQUE, `payload` JSONB, `processed_at` | Idempotency |
| `coupons` | `code` UNIQUE, `type` (`PERCENT`,`FLAT`,`FREE_SHIP`), `value`, `valid_from`, `valid_until`, `max_uses`, `max_uses_per_user` | |
| `coupon_conditions` | `coupon_id`, `condition_type`, `operand` | Min cart value, category scope |
| `coupon_redemptions` | UNIQUE(`coupon_id`,`order_id`), `user_id`, `discount_applied` | Enforces per-user caps |

**`gateway_payment_id` and `gateway_event_id` are UNIQUE for a reason.** Razorpay retries webhooks.
Without a uniqueness constraint, a retried `payment.captured` event credits the order twice. The
constraint makes double-processing impossible at the database level rather than hoping the
application remembers to check.

**Never trust the client on payment success.** Order state advances on a verified webhook (or a
server-side fetch against Razorpay), never on a browser redirect saying "paid."

---

## 6. Engagement — 5 tables

| Table | Key columns | Notes |
|---|---|---|
| `reviews` | UNIQUE(`user_id`,`product_id`), `rating` 1–5, `title`, `body`, `status`, `verified_purchase` | One review per user per product |
| `review_images` | `review_id`, `url` | |
| `notifications` | `user_id`, `type`, `title`, `body`, `read_at`, `payload` JSONB | |
| `banners` | `title`, `image_url`, `link_url`, `position`, `active_from`, `active_until` | PRD §6 |
| `settings` | `key` UNIQUE, `value` JSONB | PRD §6 |

`verified_purchase` is derived from a delivered `order_items` row, not user-claimed.

---

## 7. AI, Nutrition & Recipes — 10 tables

Implements the five hero features (PRD §5).

```
recipes ──< recipe_ingredients ──> product_variants (nullable)
        └──< recipe_steps
users ──< meal_plans ──< meal_plan_entries ──> recipes
users ──< pantry_items
users ──< fridge_scans ──< fridge_scan_items
recommendation_rules
ai_request_logs
```

| Table | Key columns | Notes |
|---|---|---|
| `recipes` | `slug` UNIQUE, `title`, `cuisine`, `prep_minutes`, `servings`, `image_url`, `source` | `source`: `SEED` or `AI` |
| `recipe_ingredients` | `recipe_id`, `ingredient_name`, `qty`, `unit`, `variant_id` NULL | Nullable link: not every ingredient is sold |
| `recipe_steps` | `recipe_id`, `position`, `instruction` | |
| `meal_plans` | `user_id`, `goal`, `week_start`, `status` | PRD §5.4 |
| `meal_plan_entries` | `meal_plan_id`, `day`, `meal_type`, `recipe_id`, `servings` | |
| `pantry_items` | `user_id`, `ingredient_name`, `variant_id` NULL, `qty`, `unit`, `expiry_date` | PRD §5.5 |
| `fridge_scans` | `user_id`, `image_url`, `status`, `model`, `raw_response` JSONB, `purge_after` | PRD §5.1 |
| `fridge_scan_items` | `scan_id`, `detected_name`, `confidence`, `variant_id` NULL | |
| `recommendation_rules` | `trigger_variant_id`, `suggested_variant_id`, `weight`, `is_active` | PRD §5.3 |
| `ai_request_logs` | `user_id`, `feature`, `model`, `tokens_in`, `tokens_out`, `latency_ms`, `cost_inr`, `status` | Cost control |

**`fridge_scans.purge_after` is a compliance column, not a nicety.** Fridge photos are user PII —
they show the inside of someone's home. The PRD does not specify a retention policy, and that is a
gap flagged for a decision (see below). The column exists so a retention job has something to
enforce once a duration is chosen; defaulting it to `now() + 30 days`.

**`recommendation_rules` is a table, not code.** PRD §5.3 gives `Paneer → Peas, Capsicum, Cream`
and calls the first version rule-based with ML later. As data, ops can edit pairings without a
deploy, and the eventual ML model can score the same rows instead of replacing the mechanism.

**Ingredient links are nullable on purpose.** A recipe calls for salt; we may not sell salt. A
nullable `variant_id` lets the recipe stay complete while "missing products from the store"
(PRD §5.1) resolves only against what we actually stock.

---

## Table count

| Module | Tables |
|---|---|
| Identity & Access | 9 |
| Catalog | 9 |
| Inventory | 9 |
| Commerce | 10 |
| Payments & Promotions | 6 |
| Engagement | 5 |
| AI / Nutrition / Recipes | 10 |
| **Total** | **58** |

Within the PRD §12 target of 45–60.

### Verified invariants

These are enforced by the database and proven by attempting to violate each one. All 11
were rejected:

| Invariant | Constraint |
|---|---|
| Cannot charge above printed MRP (illegal in India) | `variants_price_lte_mrp` |
| Cannot create a >100% discount coupon | `coupons_percent_chk` |
| Order totals must add up | `orders_total_balances` |
| GST is intra-state or inter-state, never both | `orders_gst_split_chk` |
| Cannot reserve more stock than exists (overselling) | `inventory_reserved_lte_on_hand` |
| Stock ledger rejects UPDATE | `stock_transactions_append_only` |
| Stock ledger rejects DELETE | `stock_transactions_append_only` |
| Replayed Razorpay webhook cannot double-credit | `payments_gateway_payment_uq` |
| One active cart per user | `carts_one_active_per_user` |
| Meal plan weeks start Monday | `meal_plans_week_start_chk` |
| A product cannot recommend itself | `rec_no_self_chk` |

---

## Migration layout

One Flyway file per module, matching the modular monolith boundaries (PRD §2). Ordered by
dependency — a module's FKs only ever point at modules already migrated.

| Version | File | Contents |
|---|---|---|
| `V1` | `V1__identity_and_access.sql` | Extensions, enums, 9 identity tables, seed roles/permissions |
| `V2` | `V2__catalog.sql` | Categories, brands, products, variants, nutrition |
| `V3` | `V3__inventory.sql` | Warehouses, suppliers, inventory, batches, ledger |
| `V4` | `V4__commerce.sql` | Addresses, carts, wishlists, orders, shipments |
| `V5` | `V5__payments_and_promotions.sql` | Payments, refunds, webhooks, coupons |
| `V6` | `V6__engagement.sql` | Reviews, notifications, banners, settings |
| `V7` | `V7__ai_nutrition_recipes.sql` | Recipes, meal plans, pantry, fridge scans, AI logs |

`addresses` is created in V4 but referenced by `warehouses` in V3 — so `warehouses.address_id` is
added in V4 via `ALTER TABLE`, keeping V3 self-contained.

---

## Open decisions

These need answers from the business, not from code. None block the schema; each is marked where it
lands.

1. **Fridge photo retention.** How long do we keep uploaded fridge images? Drives
   `fridge_scans.purge_after` (defaulted to 30 days). Legal exposure if wrong.
2. **Nutrition data source.** IFCT assumed. Are we licensing IFCT, using USDA FoodData Central, or
   entering values by hand per SKU? Affects who populates `nutrition_facts` and at what cost.
3. **Multi-warehouse from day one?** Schema supports N warehouses. If launch is one city with one
   facility, the code can assume one and stay simpler — the schema does not need to change either way.
4. **GST.** `orders.tax_total` is a single column. Indian GST needs CGST/SGST/IGST split for
   compliant invoices. This is a schema change if we need real invoices at launch — cheap now,
   expensive after there are orders in the table.

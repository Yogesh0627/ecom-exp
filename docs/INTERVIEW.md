# EcoExpress — Interview Preparation & Engineering Log

> A complete, honest account of building the EcoExpress backend: the architecture, every
> technology and why it was chosen, the flow of each module, and — most importantly — the
> real problems that occurred, why they were hard, and how many iterations each took to
> solve.
>
> This document is written so you can answer any question from an easy warm-up to a hard
> systems-design deep-dive, and back every claim with a specific decision or a specific bug
> you fixed. Nothing here is aspirational: every behaviour described was verified end-to-end
> against a live PostgreSQL 17 database.

---

## Table of contents

1. [The 30-second pitch](#1-the-30-second-pitch)
2. [Technology stack and the reason for every choice](#2-technology-stack-and-the-reason-for-every-choice)
3. [Architecture: the modular monolith](#3-architecture-the-modular-monolith)
4. [Database design philosophy](#4-database-design-philosophy)
5. [Module-by-module flow](#5-module-by-module-flow)
6. [War stories: every hard problem, where it occurred, and the retries to fix it](#6-war-stories)
7. [Interview questions — Easy](#7-interview-questions--easy)
8. [Interview questions — Medium](#8-interview-questions--medium)
9. [Interview questions — Hard](#9-interview-questions--hard)
10. [System design and scaling](#10-system-design-and-scaling)
11. [What is not done yet, and why that is a good answer](#11-what-is-not-done-yet)

---

## 1. The 30-second pitch

EcoExpress is an AI-assisted organic grocery e-commerce platform for the Indian market. The
differentiator is not the storefront — it is the nutrition intelligence: a **Smart Cart** that
scores the health of your basket, a **Smart Fridge** that reads a photo of your fridge and
suggests recipes plus the missing ingredients to buy, a weekly **meal planner**, and a
**pantry** that stops the app recommending things you already own.

The backend is a **Java 21 + Spring Boot modular monolith** on **PostgreSQL 17**, built
backend-first. As of this log, seven modules are complete and verified end-to-end: Identity &
Access, Catalog, Inventory, Cart + Nutrition, Orders + Checkout, and Payments — 58 database
tables, and roughly 93 end-to-end tests all green.

**The one-sentence engineering thesis:** *push correctness into the database so that a bug in
application code cannot corrupt money or stock.* Overselling, double-charging, unbalanced
invoices, and rewritten stock history are impossible at the constraint level, not merely
discouraged in code.

---

## 2. Technology stack and the reason for every choice

| Layer | Choice | Why this and not the alternative |
|---|---|---|
| Language | **Java 21 (LTS)** | Virtual threads, records, pattern matching, sealed types. LTS matters for a product that must run for years. |
| Framework | **Spring Boot 3.3.5** | The default for a transactional commerce backend in the JVM world; mature security, data, and transaction support. |
| Database | **PostgreSQL 17** | Rich constraint system (partial indexes, CHECK, generated columns, JSONB, full-text search) — the constraints are the product's safety net. |
| Migrations | **Flyway 10.20.1** | Versioned, forward-only SQL migrations from day one. "Migrations from day one" is a PRD requirement. |
| ORM | **Hibernate / Spring Data JPA** | With `ddl-auto: validate` so the entities are checked against the real schema at every boot. |
| Auth | **Spring Security + JJWT (0.12)** | Stateless JWT access tokens, opaque rotating refresh tokens. |
| Caching | **Spring Cache abstraction** — Caffeine locally, **Upstash Redis** in prod | Provider-agnostic. Local dev uses in-process Caffeine because a cloud round trip is slower than the query it saves. |
| Payments | **Razorpay** | UPI-first, which dominates Indian payments; a card-first gateway would be the wrong default. |
| Mapping | Hand-written mappers (MapStruct available) | Several fields are computed or flattened; straight Java is easier to read and debug than generated qualifiers. |
| Docs | **springdoc-openapi** | Swagger UI at `/swagger-ui.html`. |
| Build | **Maven 3.9** | |

### Deeper "why" answers you should be ready for

- **Why a modular monolith and not microservices?** One team (in fact, one developer). Network
  boundaries between services buy independent scaling and deployment at the cost of distributed
  transactions, eventual consistency, and operational overhead. At launch scale none of that is
  worth it. The module boundaries are drawn *as if* they were services (each module owns its
  tables and talks to others through services, not by reaching into their entities), so the
  seams to split along already exist if we ever need to.

- **Why `ddl-auto: validate` and not `update`?** `update` lets Hibernate silently alter the
  schema, which drifts from the migrations and is dangerous in production. `validate` makes
  Flyway the single source of truth for schema and turns any entity/schema mismatch into a
  loud startup failure. This caught **four** real bugs before they reached a request (documented
  below).

- **Why store money as `NUMERIC(12,2)` / `BigDecimal` and never `double`?** `0.1 + 0.2 != 0.3`
  in binary floating point. An order total off by a paisa is a real customer complaint and an
  accounting problem. Every money column is `NUMERIC`, every money field is `BigDecimal` with
  explicit `HALF_UP` rounding.

- **Why UUID primary keys and not auto-increment `bigint`?** UUIDs are non-enumerable in public
  URLs (you cannot guess order #1235 from #1234) and can be assigned client-side before insert,
  so a whole object graph is wired up in one flush. The tradeoff — random UUIDs scatter B-tree
  inserts — is documented and only matters north of ~10M rows on one table.

---

## 3. Architecture: the modular monolith

```
com.ecoexpress
├── common/            shared kernel: BaseEntity, security, exceptions, config
│   ├── domain/        BaseEntity (audit + soft delete), AuditableEntity (audit only)
│   ├── security/      JwtService, JwtAuthenticationFilter, AuthenticatedUser
│   ├── config/        SecurityConfig, CacheConfig, JwtProperties, JpaAuditingConfig
│   └── exception/     ApiExceptions, GlobalExceptionHandler
├── identity/          users, roles, permissions, OAuth, refresh tokens
├── catalog/           products, variants, categories, brands, nutrition facts
├── inventory/         warehouses, suppliers, stock ledger, batches (FEFO), reservations
├── cart/              cart, cart items, Smart Cart nutrition calculator
├── order/             orders, order items, addresses, pricing (GST), state machine
└── payment/           payments, refunds, webhook events, Razorpay signature verifier
```

Each module has the same internal shape: `domain/` (entities + enums), `repository/`,
`service/`, `dto/`, `mapper/`, `api/`. A module never reaches into another module's entities;
it calls the other module's **service**. Example: the cart module checks stock by calling
`InventoryService.availableFor(...)`, not by querying the inventory tables directly. That is
the discipline that keeps a monolith from turning into a big ball of mud.

### The shared kernel decision that mattered most

There are **two** mapped superclasses, and choosing the wrong one fails the boot:

- `AuditableEntity` — id, `created_at/by`, `updated_at/by`, `version` (optimistic lock). **No
  soft delete.**
- `BaseEntity extends AuditableEntity` — adds `deleted_at`.

Most tables extend `BaseEntity`. But some must extend `AuditableEntity`:
- `oauth_accounts` — a soft-deleted row would keep tripping `UNIQUE(provider, provider_user_id)`
  and permanently block re-linking a Google account.
- Ledger tables (`payments`, `stock_transactions`, `order_status_history`,
  `coupon_redemptions`) — financial history is never erased; a correction is a new compensating
  row.

This split did not exist in the first draft — it was forced by a boot failure (see War Story 4).

---

## 4. Database design philosophy

The database is not a dumb store; it is the last line of defence. Concretely:

**Audit + soft delete on (almost) everything.** Every business table carries
`created_at/by`, `updated_at/by`, `version`, and (where appropriate) `deleted_at`. `updated_at`
is maintained by a **database trigger**, not the application, so a raw SQL fix or an admin
console edit cannot leave a stale timestamp.

**Append-only ledgers enforced by triggers.** `stock_transactions`, `order_status_history`,
`coupon_redemptions`, and `payment_webhook_events` reject `UPDATE`/`DELETE` at the database via
a `reject_mutation()` trigger. You literally cannot rewrite stock or order history, even as the
app's database user. (Cleaning test data required a superuser to temporarily disable the
trigger — proof it works.)

**Partial unique indexes for "one live X per Y".** e.g. `UNIQUE(email) WHERE deleted_at IS NULL`
(a deleted account frees its email for re-registration); one ACTIVE cart per user; one default
variant per product; one default address per user.

**CHECK constraints encode business rules.** A representative set, all verified by trying to
violate them:

| Rule | Constraint |
|---|---|
| Cannot charge above printed MRP (illegal in India) | `variants_price_lte_mrp` |
| Cannot create a >100% discount coupon | `coupons_percent_chk` |
| Order totals must balance: `grand = subtotal − discount + tax + shipping` | `orders_total_balances` |
| GST is intra-state **or** inter-state, never both | `orders_gst_split_chk` |
| Cannot reserve more stock than exists | `inventory_reserved_lte_on_hand` |
| Replayed payment webhook cannot double-credit | `payments_gateway_payment_uq` |
| Meal-plan weeks start Monday | `meal_plans_week_start_chk` |

**Generated columns for search.** `products.search_vector` is a `GENERATED ALWAYS` `tsvector`
(name weighted `A`, description `B`) with a GIN index. Full-text search stays correct without
any application bookkeeping.

**Final schema shape:** 58 tables, 5 reconciliation views, 186 indexes, 116 check constraints,
78 foreign keys.

---

## 5. Module-by-module flow

### 5.1 Identity & Access

**Flow — registration/login:** `POST /auth/register` → validate → bcrypt(cost 12) the password
→ save user with the `CUSTOMER` role → issue an access JWT (15 min) + an opaque refresh token
(30 days, stored as a SHA-256 hash).

**Flow — refresh with theft detection:** `POST /auth/refresh` with the raw token → hash it →
`SELECT ... FOR UPDATE` the row → if it is already rotated (a `replaced_by_id` is set), the
token was replayed → **revoke every session for that user** and reject. Otherwise mint a new
pair, point the old row's `replaced_by_id` at the new one, and revoke the old.

**Authorization is permission-based, not role-based.** Checks read
`hasAuthority('product:write')`, never `hasRole('ADMIN')`. Roles are just bundles of
permissions, so they can be re-cut later without touching a single call site. Permissions live
in the JWT, so authorizing a request needs no database round trip.

### 5.2 Catalog

Products vs variants: "Organic Turmeric" is a **product**; the 200g and 500g packs are
**variants**. Price, SKU, barcode, and nutrition live on the variant. The default variant (one
per product, enforced by a partial unique index) is what a listing card shows.

`discount_pct` is **never stored** — it is derived from `mrp` and `price` at read time, because
a stored copy drifts the moment either changes and then the storefront advertises a discount
that is not real.

Nutrition has 12 nutrients per 100g, and **every column is nullable on purpose**: `NULL` means
"not measured", `0` means "measured, contains none". Collapsing the two would let a health score
treat unmeasured iron as zero iron.

### 5.3 Inventory — the correctness centrepiece

Two numbers per stock row: `qty_on_hand` (physical) and `qty_reserved` (promised to unpaid
carts/orders). **Available = on_hand − reserved.** Without the split, two customers can both
buy the last bag of atta.

**The ledger is the truth.** `stock_transactions` is append-only; `inventory.qty_on_hand` is a
cached rollup of it. Every mutation writes a ledger row in the same transaction. The
`inventory_ledger_drift` view recomputes on-hand from the ledger and shows any divergence —
which makes "inventory accuracy" auditable, not aspirational.

**FEFO, not FIFO.** Organic produce expires, so allocation is first-expiry-first-out. Shipping
draws from the batch with the earliest expiry, indexed for the purpose.

**Concurrency.** Every reservation/decrement does `SELECT ... FOR UPDATE` on the stock row
first. Verified with a **10-concurrent-buyers-for-1-unit** test: exactly one succeeded, nine got
a clean 409, zero 500s.

**Inventory admin (PRD §9).** On top of the stock core sits the operations layer:
- **Purchase orders** — draft → submit → receive (full or partial across several calls) →
  cancel, as a state machine. Receiving delegates to the same ledger-writing `receiveStock`, so
  every received unit still creates a batch and a RECEIPT ledger row tied to the PO number.
  Over-receipt (more than ordered) is refused.
- **Reason-coded stock adjustments** with a **request → approve** split: no stock moves when an
  adjustment is requested, only when an approver signs off. Shrinkage (damage/theft/count
  correction) always has a named approver on record before a unit leaves the books. Approval is
  idempotent, so a double-click cannot apply it twice.
- **Low-stock alerts** that open automatically when a sale drops on-hand below the reorder point
  and resolve when a receipt brings it back — at most one open alert per stock row (a partial
  unique index), so a product hovering at the threshold does not spam an alert per movement. All
  verified end-to-end (16/16), and the ledger-drift check stayed empty through the whole flow.

### 5.4 Cart + Smart Cart Nutrition (PRD hero feature #2)

The cart **does not reserve stock** — it checks availability on add and again at checkout, but
only an order reserves. Reserving on add-to-cart would let anyone freeze the catalog by filling
a cart and walking away, and every abandoned cart becomes a stockout.

Stock checks use the **cumulative total**, not the delta: adding 2 units four times to a stock
of 3 is rejected on the second call.

The nutrition engine totals the basket by scaling each line's per-100g facts by its real weight.
Thresholds are the **UK FSA traffic-light bands** and EU claim thresholds — published references
so the score is explainable, not invented. **The rule that matters:** if any line lacks nutrition
data, `healthScore` is `null` and `complete` is `false`. Scoring a partial basket would rank a
cart of unmeasured junk best of all, because absent data reads as "no sugar, no sodium".

### 5.5 Orders + Checkout

**Checkout is one transaction:** price the cart → snapshot everything onto the order → reserve
stock → convert the cart. A partial checkout — stock reserved against an order that does not
exist — is the worst possible outcome, so it is all-or-nothing.

**Everything is snapshot.** The delivery address is copied onto the order (not referenced), and
each line copies the product name, SKU, and price. Rename or reprice a product next month and a
six-month-old invoice still renders exactly as the customer received it.

**GST (place-of-supply):** if the delivery state equals the dispatching warehouse's state, it is
intra-state → CGST + SGST (each half the rate); otherwise inter-state → IGST (full rate). Never
both. Rounding is per-line then summed, so the printed lines add up to the printed total.

**Order state machine:** the allowed transitions live in one enum. You cannot ship an unpaid
order, cannot cancel a shipped order (it becomes a RETURN), and each transition has its stock
consequence (SHIPPED consumes the reservation; CANCELLED releases it). History is append-only.

### 5.6 Reviews & Wishlist

**Verified purchase is proven, never claimed.** A review's `verified_purchase` flag is set by
the server only when the reviewer has a DELIVERED order line for that product — checked with a
query that joins `order_items → orders → product_variants` and reads the order's own status. The
client cannot set it: a JSON body sending `verifiedPurchase: true` is ignored (the field is not a
request input). A review badge that anyone can self-assign is worthless; this one means the system
watched the order arrive.

**Moderation gates visibility.** Reviews start PENDING; only an APPROVED review appears on the
product page, and only `review:moderate` staff can approve/reject. One review per user per product
(partial unique index). The public byline is first-name-only — a review should not leak a full
name. The `product_rating_summary` view counts only approved reviews, so pending and rejected ones
never move the star rating.

**Wishlist** is per-user and scoped to the caller's id (no wishlist-by-id route — that would be an
IDOR). Re-adding a saved variant is a no-op, not a duplicate.

### 5.7 Coupons

**Quote is advisory; redemption is authoritative.** The "Apply coupon" button calls `quote`,
which validates and returns what the coupon *would* save with no side effects. The real redemption
happens inside the checkout transaction: it row-locks the coupon, re-runs the caps under the lock,
writes an append-only `coupon_redemptions` row, and bumps the counter. So two concurrent checkouts
cannot over-redeem a limited coupon, and — crucially — **a failed checkout consumes no use**,
because redemption and the order commit or roll back together.

**Validation runs a fixed gauntlet** so the message is the most useful one: exists → in-window →
min-cart → per-user cap → total cap → first-order-only. Discount types: PERCENT (optionally capped
by `max_discount`), FLAT, FREE_SHIPPING (which zeroes the shipping fee rather than discounting a
line). Codes are case-insensitive (`upper(code)` unique index), a >100% percent coupon is
impossible (CHECK), and every discounted order still satisfies the `grand = subtotal − discount +
tax + shipping` constraint. 15/15 e2e.

### 5.8 AI hero features (the differentiator)

All five AI features go through one abstraction, and that abstraction is the point.

**Gemini is the vision system — no classical CV stack.** The fridge scan sends a photo + a strict
JSON prompt to Gemini Vision and gets back detected ingredients. Deliberately no OpenCV, no
Tesseract/OCR, no YOLO, no custom CNN, no Python service — a multimodal foundation model replaces
that entire pipeline, and building a custom one would be months of ML for worse open-domain
results. Java-only; Python would only earn its place for in-house model training.

**One doorway: `AiService`.** No feature calls the provider directly. `AiService` owns the three
concerns that must be uniform: (1) **budget** — month-to-date spend is checked against a cap
before every call, fail-closed; (2) **accounting** — every call, success or failure, writes an
`ai_request_logs` row with tokens, latency, and estimated cost, in a `REQUIRES_NEW` transaction so
it survives a rollback; (3) **failure shape** — provider errors become a 503, never a 500, and are
classified (RATE_LIMITED vs FAILED).

**The provider is a swappable adapter.** Everything depends on the `AiClient` interface, so moving
from a hand-rolled RestClient to **Spring AI** touched exactly one class — `SpringAiGeminiClient` —
while `AiService` and all five features were untouched. Spring AI's OpenAI-compatible client is
pointed at Gemini's OpenAI surface (`/v1beta/openai`), because the key is an AI-Studio key, not a
GCP Vertex service account.

**Reliability lives above the API call:** structured JSON output + our own validation (a malformed
reply throws cleanly, never persists garbage), confidence gating (low-confidence fridge detections
are flagged for user confirmation, never trusted), grounding to our catalog (the meal planner uses
the user's pantry; detected ingredients fuzzy-match to sellable variants), and never presenting AI
nutrition as measured fact.

- **Recommendations (§5.3)** and **Pantry (§5.5)** need no AI key — rules-as-data and CRUD — and
  are fully verified (18/18): the PRD's own Paneer → Peas/Capsicum/Cream example, weight-ranked,
  out-of-stock filtered.
- **Meal planner (§5.4)** and **Smart Fridge (§5.1)** call Gemini and are verified generating
  **live content**: the planner produced a real 28-meal week ("Paneer bhurji with 2 whole wheat
  toasts"), persisted with the Monday-start constraint and metered at 272 in / 1017 out tokens
  (₹0.026); the fridge scan made a real Vision call, completed, and logged, with the PII purge
  date set. The blocker was never the code — it was a stale model name (`gemini-2.0-flash` hit
  free-tier quota, `gemini-2.5-flash` 404s as "no longer available to new users"). Fix: query the
  API for available models, test them, and use `gemini-flash-latest` — the stable alias that
  tracks the current flash model so it does not deprecate under us.

### 5.9 Engagement & Admin

**Settings** are runtime config in a JSONB table, read through a typed service with a default for
every key — so no module hardcodes a delivery fee or an AI budget, and a missing/malformed setting
degrades to a safe default rather than a crash. A public/private flag gates what the storefront may
read (delivery fee yes, AI budget no).

**Notifications** are raised automatically on order events (placed / shipped / delivered) — the
order service calls the notification service, which never throws back into the order flow (a failed
notification must not roll back the order). Per-user scoped; unread badge + mark-all-read.

**Banners** filter by an active window, so a future-dated campaign is created but hidden until it
opens.

**Admin dashboard** computes aggregates in the database, not by loading rows into Java — revenue
(paid orders only), order counts by status, today's numbers, and the operational health signals
that reuse the reconciliation views: payment mismatches and inventory ledger drift, both expected
to be 0. Top sellers come from delivered order lines. 20/20 e2e.

### 5.10 Payments (Razorpay)

**Never trust the client.** An order goes PAID only from a **signed webhook**, never from the
browser redirect. A redirect is trivially forged; treating it as proof of payment is how a
storefront ships goods for free.

**Three rules, each because breaking it loses money:**
1. **Verify before believing** — the endpoint is public (Razorpay cannot send a JWT); the HMAC
   signature is the only thing separating a real capture from a forged one.
2. **Store, then process** — the raw event is persisted before any effect, so a crash mid-handler
   leaves a replayable row, not money that moved with no record.
3. **Idempotency is the database's job** — `UNIQUE(gateway, gateway_event_id)`; a retried event
   fails to insert. Verified by replaying a capture 5× → one payment row, not double-credited.

Amounts arrive in **paise**; conversion to rupees happens in exactly one place (a 100x error
otherwise).

---

## 6. War stories

> This is the section interviewers actually want. Each entry: **where** the problem occurred,
> **why** it was hard, **how it was found**, and **how many iterations** to fix. These are all
> real; several were false-passes I caught in my own tests, which is itself worth talking about.

### War Story 1 — UUID v7 that did not exist (caught before it ran)

**Where:** `BaseEntity`, the shared primary-key strategy.
**What:** I documented IDs as time-ordered UUID v7 generated by Postgres `uuidv7()`.
**Why hard:** `uuidv7()` was added in **PostgreSQL 18**; the machine runs **17.10**. The
migration would have failed at `CREATE TABLE` time.
**Found:** By checking the installed extensions before running, not by hitting the error.
**Fix / retries:** 1 correction — switched to `gen_random_uuid()` (v4, core since PG13) and
documented the index-locality tradeoff. **Lesson:** verify version-specific features against the
actual server, never from memory.

### War Story 2 — Flyway did not officially support PG17

**Where:** First migration run.
**What:** Spring Boot 3.3.5's managed Flyway supports PostgreSQL up to 16 and **warned** that 17
is untested.
**Why it matters:** Running migrations on officially-untested support is not something to leave
in a payments product.
**Fix / retries:** 1 — pinned `flyway.version` to 10.20.1, which knows PG17. The warning
disappeared and `flyway:info` showed a clean run.

### War Story 3 — `citext` broke every entity with an email

**Where:** `V1` used `CITEXT` for case-insensitive emails; `User` and `OAuthAccount` mapped them
as `String`.
**Why hard:** The JDBC driver reports `citext` as `Types#OTHER`, but Hibernate maps `String` to
`varchar`. `ddl-auto: validate` refused to start:
`wrong column type ... found [citext], but expecting [varchar]`.
**Found:** At application boot (not compile).
**Fix / retries:** Rewrote `V1` to use plain `TEXT` + a `UNIQUE INDEX ON lower(email)` — same
case-insensitive guarantee, no extension, no ORM friction. Because the DB already held rows, this
needed a `flyway:clean` + full re-migrate. **~2 iterations** (fix the column, then fix the
unique index that referenced it).

### War Story 4 — the `deleted_at` mismatch that split the base class

**Where:** `OAuthAccount extends BaseEntity`.
**Why hard:** `BaseEntity` mandates a `deleted_at` column, but I had **deliberately** given
`oauth_accounts` no soft delete (a lingering soft-deleted row would trip
`UNIQUE(provider, provider_user_id)` and block re-linking forever). My entity contradicted my own
schema. Boot failed: `missing column [deleted_at] in table [oauth_accounts]`.
**Found:** At boot, via `ddl-auto: validate`.
**Fix / retries:** Split the hierarchy into `AuditableEntity` (no soft delete) and `BaseEntity`
(with). This is now a core architecture decision. **1 structural fix**, but it changed the shape
of the shared kernel.

### War Story 5 — the `inet` column that rejected every registration

**Where:** `refresh_tokens.ip_address` is Postgres `inet`; the entity field is `String`.
**Why hard:** Hibernate binds a `String` as `varchar`, and Postgres refuses the implicit
`varchar → inet` cast: *"column ip_address is of type inet but expression is of type character
varying"*. It **compiled fine** — the failure was only at runtime, on the first `register` call
(HTTP 500).
**Found:** Running the auth e2e tests — register returned 500.
**Fix / retries:** Added `@JdbcTypeCode(SqlTypes.INET)`. **1 fix.** The same class of bug (a
Postgres type Hibernate does not map to by default) recurred later with `CHAR(3)` currency (War
Story 8) — once you have seen it, you recognise it instantly.

### War Story 6 — the security control that silently undid itself (the best one)

**Where:** `AuthService.refresh()` theft detection.
**Why hard — and why it is the best story:** When a replayed refresh token is detected, the code
did two things: `revokeAllForUser(...)` and then `throw AuthenticationFailedException`. Both were
in **one `@Transactional` method**. The `throw` rolled the transaction back — **including the
revocation.** So a stolen token was correctly *rejected*, but the legitimate user's sessions were
*not actually revoked*: the stolen token kept working. The security feature reported success while
doing nothing.
**Found:** An e2e test that replayed a rotated token and then asserted the *rest of the chain was
dead too* — that assertion failed while the simpler "replay is rejected" assertion passed. If I
had only tested the happy path, this ships.
**Fix / retries:** Moved the revocation into a separate bean with
`@Transactional(propagation = REQUIRES_NEW)` so it commits independently of the failing request.
Had to reason carefully about whether the `REQUIRES_NEW` transaction would deadlock against the
outer `PESSIMISTIC_WRITE` lock (it does not — a rotated token already has `revoked_at` set, so the
revocation's `WHERE revoked_at IS NULL` clause skips the locked row). **1 fix, but it required
understanding Spring's proxy model** (a self-invoked `this.method()` would have ignored the
annotation entirely). Verified: after the fix, the replayed token's whole chain is dead.

### War Story 7 — `commons-pool2` and the Google-OAuth boot failures

**Where:** Wiring Upstash Redis under the `prod` profile.
**What:** Two boot failures in a row.
1. I enabled Lettuce connection pooling (`spring.data.redis.lettuce.pool.*`) but
   `commons-pool2` is **not** a transitive dependency → `NoClassDefFoundError:
   GenericObjectPoolConfig` at startup. Added the dependency.
2. Next boot: `Client id of registration 'google' must not be empty`. I had declared a Google
   OAuth registration with an empty default, and Spring **validates declared registrations at
   startup** — an empty client-id is a hard failure. Moved OAuth to a `google-oauth` profile
   that only activates when real credentials exist.
**Found:** Two consecutive `spring-boot:run` failures, read from the log.
**Retries:** 2 distinct fixes to get one clean prod-profile boot. **Lesson:** "compiles" and
"boots" are different gates; the prod profile has its own failure surface.

### War Story 8 — the health check that lied, and killing the wrong process

**Where:** Local run management, with a second app (ClaimLens) already on port 8080.
**Why hard — a chain of three mistakes:**
1. EcoExpress defaulted to **8080**, which ClaimLens owned. My health poll returned `UP` at "0
   seconds" — impossible for a fresh boot. It was ClaimLens answering. I almost reported "Redis
   works" when EcoExpress had actually crashed. **A health check against a port proves nothing
   about which app answered.**
2. My cleanup killed java processes by matching the string `spring-boot:run` — which **also
   matched ClaimLens's Maven wrapper**, so I killed the user's other app.
3. My "restart" of ClaimLens used a shell-child process that **died with the shell** ~30s later,
   so my first "restored it" claim was wrong.
**Fix / retries:** Moved EcoExpress to **8081**; verified via the app's own
`Started EcoExpressApplication` log line, not a port; and finally restarted ClaimLens as a proper
**detached background task** so it survives. **~3 iterations and one wrong "it's fixed" claim.**
**Lesson:** never match processes by a string that other apps share; confirm the specific PID.

### War Story 9 — the prediction I got wrong (and the test that saved me)

**Where:** Upstash Redis credentials.
**What:** I asserted that the Upstash **REST token** could not work as the Spring Data Redis
(RESP protocol) password — REST and RESP are different transports.
**Why it mattered:** I was about to send the user to the Upstash console for a "real" Redis
password that does not separately exist.
**How resolved:** I wrote a tiny raw-socket RESP probe in Java and tested it. `AUTH default
<REST-token>` returned `+OK`. **My prediction was wrong — the token works over both.** The
password I "decoded" out of the token is the one that fails.
**Lesson:** test the claim, do not ship the prediction. One probe saved a wrong instruction.

### War Story 10 — `MultipleBagFetchException`

**Where:** Product-detail query, join-fetching `Product.variants` **and**
`ProductVariant.images`.
**Why hard:** Both are `List` collections ("bags"). Hibernate refuses to fetch two bags in one
query — it cannot attribute a joined row to the right collection. Switching them to `Set` would
compile but produce a **variants × images cartesian product**.
**Found:** Product-detail endpoint returned 500 in the catalog e2e tests.
**Fix / retries:** Fetch variants via `@EntityGraph`, load images with `@BatchSize(50)` (one
extra IN-query for all variants' images). **1 fix**, two queries total, no row multiplication.

### War Story 11 — RBAC worked, but the status code lied

**Where:** `GlobalExceptionHandler`.
**Why subtle:** My catch-all `@ExceptionHandler(Exception.class)` intercepted Spring Security's
`AuthorizationDeniedException` **before** the configured 403 handler could run. So a customer
trying to create a product was correctly **blocked** — but the API returned **500** instead of
**403**. The security worked; the contract lied, which would mask real permission bugs and break
any client that branches on 403.
**Found:** A catalog test expected 403 and got 500.
**Fix / retries:** Added an explicit handler for `AccessDeniedException` /
`AuthorizationDeniedException` ahead of the catch-all. **1 fix.**

### War Story 12 — `@CreatedDate` that did nothing (a decorative annotation)

**Where:** `StockTransaction` — an append-only entity that extends **neither** base class.
**Why hard:** It had `@CreatedDate` on `created_at`, but Spring's auditing only fires when the
entity is registered with `@EntityListeners(AuditingEntityListener.class)` — which the base
classes declare and this standalone entity did not. So the annotation was **inert** and
`created_at` inserted as `NULL`. Every stock receipt hit the column's NOT NULL constraint and
500'd.
**Found:** The inventory e2e "receive stock" test — every receipt failed.
**Fix / retries:** Added `@EntityListeners(AuditingEntityListener.class)`. **1 fix.** I then
proactively applied the same to `OrderStatusHistory` and the payment entities, which have the
same shape — the second occurrence was prevented, not debugged.

### War Story 13 — two of my own tests passed while proving nothing (false-passes)

This is worth telling because it is about test *honesty*, which interviewers probe for.

**13a — the FEFO test that asserted nothing.** My first FEFO test called the ship endpoint and
printed `PASS` — without checking which batch was drawn. A test that cannot fail is worse than no
test: it manufactures confidence. I rewrote it to query `inventory_batches` directly and confirm
`LOT-SOON` (earlier expiry) went 5→2 while `LOT-LATE` stayed at 5.

**13b — the snapshot test that compared a value to itself.** To prove old invoices do not change,
I ran `UPDATE product_variants SET price = 999`. It reported PASS. But the update had been
**rejected by `variants_price_lte_mrp`** (MRP was 50), so the catalog price never actually
changed — the test compared 40 against an unchanged 40. I re-ran it raising the MRP to 1000
first; only then did it genuinely prove the invoice stayed at 40 while the live catalog moved to
999. **Lesson:** when a test passes, confirm the precondition actually took effect.

### War Story 14 — the webhook idempotency bug that was a security hole

**Where:** `PaymentWebhookService.handle()`.
**Why hard — and genuinely a security issue:** I recorded every incoming webhook into the
unique-keyed `payment_webhook_events` table **before** verifying its signature. That means a
forged payload with a *guessed* `gateway_event_id` could be recorded first — and then Razorpay's
**real** event with that id would be rejected as a "duplicate". An attacker could pre-empt (deny)
a genuine payment webhook.
**Found:** A test where two forgeries targeted a payment, then a *valid* capture for the same
payment was sent — the valid one was wrongly rejected as a duplicate.
**Fix / retries:** Reordered to **verify the signature before consuming the idempotency key**.
Invalid events are still recorded for forensics, but under a synthetic `INVALID:<uuid>` key that
can never collide with a real event id. **1 conceptual fix**, plus War Story 15 which pigg-backed
on it.

### War Story 15 — the ghost compile error (`mvn` said success, runtime disagreed)

**Where:** The `PaymentWebhookService` fix above used `UUID.randomUUID()` — but I forgot
`import java.util.UUID;`.
**Why hard:** `mvn compile` reported **BUILD SUCCESS**, yet the running app threw
`java.lang.Error: Unresolved compilation problem: UUID cannot be resolved`. The VS Code Java
extension had auto-compiled a **broken `.class`** into `target/classes` on save, and `mvn
compile` saw the timestamp as up-to-date and **skipped recompiling it** — so the app loaded the
IDE's broken class. This cost several confusing iterations because the build tool and the runtime
disagreed about whether the code even compiled.
**Fix / retries:** Added the import and ran `mvn clean compile` to force regeneration. **~3
iterations** before I recognised the IDE/Maven stale-class interaction. **Lesson:** when a
runtime "Unresolved compilation problem" appears, trust `javac` over "mvn said success" and do a
clean build.

### War Story 16 — the multi-warehouse 404 that surfaced a real design limit

**Where:** `OrderService.pickWarehouse()` during payment testing.
**Why hard:** My repeated debug runs left several active warehouses in the database.
`pickWarehouse()` picks the **first** active warehouse arbitrarily, but the test's stock was
received into a **specific** new warehouse. So checkout tried to reserve from a warehouse that
did not stock the item and threw "not stocked at warehouse Y" — surfacing as a confusing **404**
at checkout.
**Found:** Checkout returned empty order ids across the payment test after debug-data
accumulation.
**Fix / retries:** For the test, clean to exactly one warehouse. But the real takeaway is a
**genuine product limitation**, now documented: single-warehouse routing is fine for launch, but
needs pincode-based routing before a second warehouse opens. The test did its job — it found the
edge of the current design.

### War Story 17 — the malformed-body 500 that should have been a 400

**Where:** Purchase-order creation, during inventory-admin testing.
**What happened:** A test sent a supplier id that was accidentally malformed (a UUID with a
trailing status string — a test-harness bug). The API returned **500**.
**Why it matters:** A malformed request body throws `HttpMessageNotReadableException`, which my
catch-all `@ExceptionHandler(Exception.class)` turned into a 500 — "server error" for what is
actually "you sent garbage". Wrong for the client (it should retry differently on 4xx vs 5xx) and
alarming on a dashboard.
**Fix / retries:** Added an explicit handler mapping `HttpMessageNotReadableException` → 400
`MALFORMED_REQUEST`, with a generic message so parser internals are not leaked. **1 fix.** Same
family as War Story 11 (RBAC 500 vs 403): a catch-all that is too greedy mislabels client errors
as server errors.

### War Story 18 — the test that never exercised the feature it claimed to test

**Where:** The low-stock-alert test.
**Why worth telling:** My `stock-item` endpoint starts on-hand at **0** (its `qty` argument is the
*reorder point*, not initial stock). My test assumed the item started with 5 units, so its
"sell 21 to drop below the reorder point" step actually tried to ship more than existed — the
ship **failed** (correctly, 400), on-hand never dropped, and the alert never fired. The alert
assertion failed, but for the wrong reason: the feature was never exercised. If I had loosened the
assertion instead of understanding it, I would have "fixed" the test into a false-pass.
**Fix:** Corrected the arithmetic to reflect that stock starts at 0, drove on-hand genuinely below
the reorder point, and only then confirmed the alert opens, stays single, and resolves on
recovery. **Lesson (again):** when a test fails, confirm *why* — a failing assertion can be hiding
a test that isn't testing anything.

### War Story 19 — the lazy-load 500 that only hit the first request

**Where:** Wishlist add.
**Why subtle:** The first `POST /wishlist/items` for a new user 500'd, but adding a *second* item
worked, and the GET always worked. The service was `@Transactional`, but the controller's `add`
method was not — so building the JSON response (which walks the lazy `variant → product`
association) ran *after* the service transaction had closed → `LazyInitializationException`. The
GET worked because it was `@Transactional`; the second add worked because its wishlist came back
through an `@EntityGraph` that eagerly loaded the product, while the first add returned a
freshly-created wishlist whose new item's product was still a lazy proxy.
**Found:** A wishlist test where the first add reported an empty item count while the dedupe and
remove steps passed — the inconsistency was the tell.
**Fix / retries:** Made the mutating controller methods `@Transactional` so the response is built
inside the session. **1 fix.** **Lesson:** an intermittent 500 that depends on whether an entity
came from a cache/graph vs a fresh save is almost always a transaction-boundary/lazy-loading
problem.

### War Story 20 — the provider swap that proved the abstraction

**Where:** Switching the AI client from a hand-rolled Gemini HTTP client to Spring AI.
**Why it is a good story:** I had built `AiService` (budget, cost logging, error classification)
and both AI features against an `AiClient` interface, with the Gemini calls behind it. When the
decision was made to adopt Spring AI, the change touched **one class** — I deleted the hand-rolled
`GeminiClient` and wrote `SpringAiGeminiClient implements AiClient`. `AiService`, the budget/logging
logic, the fridge-scan service, and the meal-planner service did not change a line. That is the
entire argument for the interface, demonstrated rather than asserted.
**Also — the "broken AI" that was a stale model name.** The first live calls failed and the
temptation was to blame the borrowed key. But the errors told a precise story: `gemini-2.0-flash`
returned **429 quota-exceeded** (which only happens *after* successful auth — so the key was
fine), and `gemini-2.5-flash` returned **404 "no longer available to new users"** (model names
had moved on; it was 2026). Instead of guessing model names, I hit the API's list-models endpoint
with the key, tested each candidate with a one-line generateContent call, and found
`gemini-flash-latest` and `gemini-3.5-flash` both returned content. Switched to
`gemini-flash-latest` — the stable alias — and the meal planner and fridge scan generated live
content immediately. **Lesson:** when an external API fails, read the exact status/message (429 vs
404 vs 401 mean very different things) and ask the API what it supports rather than guessing.
The failure path itself was also validated for free along the way: the 429 was classified
RATE_LIMITED, logged, and surfaced as a 503, not a 500.

### War Story 21 — the meal planner that billed us for a reply it then threw away

**Where:** Meal planner (and, latently, the fridge scan) during the frontend AI-page verification.
**Symptom:** `POST /meal-plans/generate` returned **503 "The meal planner returned an unreadable
response"** — but the `ai_request_logs` row for the same call was **SUCCESS**, with real token
counts and latency. So the Gemini call succeeded and cost money, and *our* code then rejected the
result. That contradiction (paid SUCCESS in the ledger, error to the client) was the whole tell:
the failure was in parsing, not in the call.

**The hard part — a false lead first.** My initial curl hit `POST /api/v1/meal-plans` (no
`/generate`) and got a generic **500 with zero log rows**. I spent time hypothesising a pre-AI
failure — a `save()` constraint, an NPE in the budget `SUM` — and read the DB constraints and the
`spendSince` query before realising I had simply called the wrong path. Lesson re-learned: confirm
the request reached the handler you think it did *before* theorising about that handler's internals.

**Root cause.** I reproduced the model's raw output by calling Gemini's OpenAI-compat endpoint
directly and printing `repr()` of the content. Even in `response_format: json_object` mode,
`gemini-flash-latest` returned a valid object followed by a **stray extra closing brace**:
`{ …valid… }\n}\n`. Jackson's `readTree` rejects the trailing token, so both feature parsers threw
"unreadable response" on a reply that was 99% well-formed. This is a provider quirk, and it would
hit **every** JSON feature, not just the meal planner.

**Fix / retries.** One conceptual fix, placed by the abstraction I already had. Because
`SpringAiGeminiClient` is documented as *the only class that knows about the provider*, the Gemini
quirk belongs there — not smeared across each feature's parser. I added `AiJson.extractFirstJson`,
a balanced-bracket scanner that walks from the first `{`/`[` to its matching close (respecting
strings and escapes) and discards anything before or after — so it also absorbs markdown fences and
leading prose. The client applies it to every `jsonOutput` reply. The meal planner and fridge-scan
parsers were left untouched and now always receive clean JSON. Verified: meal plan → **201, 28
meals across all 7 days**; fridge scan → **200 COMPLETED**; both logged SUCCESS with token/₹ cost.
**Lesson:** when a paid external call succeeds but your code rejects its output, the bug is on your
side of the boundary — and the fix goes at the boundary (the provider adapter), so one change
covers every consumer.

### War Story 22 — the moderation 500 that had already succeeded (a repeat offender)

**Where:** `POST /reviews/{id}/moderate`, found while building the admin review-moderation UI.
**Symptom:** Approving a review returned **500**, yet the review *was* approved — the pending queue
dropped to zero and the review appeared on the public product page. A write that fails loud but
commits anyway is the tell for an error in response *serialisation*, not in the work itself.
**Root cause:** Identical to War Story 19. The controller method was not `@Transactional`, so once
`reviewService.moderate()` returned and the session closed, building the `ReviewResponse` walked the
review's **lazy `user` proxy** (`getUser().getFullName()` for the byline) →
`LazyInitializationException`. The confirming log line: `could not initialize proxy
[...identity.domain.User#...] - no Session`. The sibling `create` endpoint is not `@Transactional`
either but is safe, because it loads the user via `findById` (a fully-initialised entity, not a
proxy) — so only the re-fetch path in `moderate` was exposed.
**Fix / retries:** Added `@Transactional` to the moderate method so the reply is built inside the
session. **1 fix**, re-verified: approve now returns 200 with the full body, queue empties, review
goes public. **Lesson:** a mutating controller that returns a mapped entity is a lazy-loading trap;
the endpoints that escape it do so only by accident of which entities they happen to have fully
loaded. When one such 500 appears, grep the other mutating handlers for the same missing annotation.

### War Story 23 — 40 compile errors from one duplicate line (Lombok's cascade)

**Where:** Adding the supplier list endpoint for purchase orders.
**Symptom:** `mvn compile` failed with a wall of `cannot find symbol: method getQtyReserved()` /
`getQtyOnHand()` / `getId()` errors — all on `Inventory`, a class I had not touched. The obvious
(wrong) reading is "something broke the Inventory entity."
**Root cause:** I had added `boolean existsByCode(String)` to `SupplierRepository`, which already
declared it — a duplicate method. That single real error aborted the annotation-processing round,
so **Lombok never generated the accessors** for unrelated entities, and every call to a
Lombok getter then looked "missing." The 40 errors were shrapnel from one line.
**Fix / retries:** Read the *first* error, not the loudest cluster — `method existsByCode is already
defined`. Deleted the duplicate; all 40 "missing getter" errors vanished. **1 fix.** **Lesson:** a
burst of `cannot find symbol: getXxx/setXxx` on Lombok classes is almost never those classes — it is
one genuine compile error elsewhere killing code generation. Sort errors by file and fix the
non-getter one first.

### Architecture notes — storage, certification, invoicing (added post-admin-console)

- **Object storage behind an interface.** `StorageService` with a local-filesystem adapter (dev)
  and an S3-compatible adapter (prod). Because Cloudflare R2 and AWS S3 share the S3 protocol, one
  adapter serves both — R2 was chosen for zero egress. Same payoff as the `AiClient` interface: the
  provider is config, not code.
- **Organic certificates as the differentiator.** A product's "100% organic" claim is backed by
  uploaded certificate documents (NPOP/India Organic, Jaivik Bharat, USDA/EU, FSSAI, lab reports),
  shown on the storefront with a staff-**Verified** badge — proof, not just a boolean.
- **Invoices: raw data is truth, PDF is a cached artifact.** The order already freezes the priced
  lines, the CGST/SGST/IGST split, and HSN at checkout, so the `invoices` table adds only a stable
  gapless number and issue date. The PDF is rendered on demand (OpenPDF) from that frozen data and
  cached — never the source of truth, so a template change re-renders history identically. It is
  served through an **authenticated** endpoint (owner or staff, paid orders only), not a public
  link, because it carries the customer's address.

### Retry scoreboard (be honest about this in interviews)

| Category | Count |
|---|---|
| Bugs caught at **boot** by `ddl-auto: validate` before any request | 4 (citext, deleted_at, CHAR currency, — plus the inet type it flagged) |
| Bugs caught only at **runtime** by e2e tests | 7 (inet insert, @CreatedDate NULL, MultipleBag, RBAC 500, webhook order, Gemini doubled-brace JSON, review-moderate lazy-load) |
| **Security** bugs found by adversarial tests | 2 (refresh-revoke rollback, webhook pre-emption) |
| **False-passes** I caught in my own tests | 2 (empty FEFO assert, snapshot vs itself) |
| **Wrong predictions** corrected by testing | 1 (Upstash REST-as-RESP) |
| **Environment/process** mistakes | ~1 multi-step (killed ClaimLens, port collision, stale .class) |

The point to make: **almost none of these were caught by compilation.** They were caught by
`validate` at boot and by end-to-end tests that drove real HTTP against a real database — and two
of them only by tests that adversarially checked the *consequence*, not just the happy path.

---

## 7. Interview questions — Easy

**Q: What is EcoExpress and what makes it different?**
An AI-assisted organic grocery platform for India. The differentiator is nutrition intelligence —
Smart Cart health scoring, Smart Fridge recipe suggestions, meal planning — not the storefront.

**Q: What is the tech stack?**
Java 21, Spring Boot 3.3.5, PostgreSQL 17, Flyway, Spring Security + JWT, Redis (Upstash) /
Caffeine caching, Razorpay for payments.

**Q: Why Spring Boot?**
Mature, batteries-included framework for transactional backends: security, data/JPA,
transactions, validation, and a huge ecosystem. It is the JVM default for this kind of product.

**Q: What is a modular monolith?**
One deployable, but internally split into modules with strict boundaries — each module owns its
tables and is called through its service, never by reaching into its entities. You get clean
separation without the operational cost of microservices.

**Q: How do you store money and why?**
`NUMERIC(12,2)` in Postgres, `BigDecimal` in Java, with explicit HALF_UP rounding. Never
`double`, because binary floating point cannot represent decimal money exactly.

**Q: How does login work?**
Password checked with bcrypt (cost 12); on success we issue a short-lived JWT access token and a
long-lived opaque refresh token. The access token carries the user's permissions so most requests
need no DB lookup.

**Q: What does `ddl-auto: validate` do?**
On startup, Hibernate checks that every entity matches the real database schema and refuses to
boot on a mismatch. Flyway owns the schema; Hibernate only validates.

**Q: Why UUID primary keys?**
Non-enumerable in URLs and assignable before insert. The tradeoff is index locality, which only
matters at very large scale.

---

## 8. Interview questions — Medium

**Q: How do you prevent overselling?**
Two columns — `qty_on_hand` and `qty_reserved`; available is the difference. Every reservation
does `SELECT ... FOR UPDATE` on the stock row so concurrent buyers serialize, and a CHECK
constraint (`reserved <= on_hand`) is the backstop. Verified with 10 concurrent buyers for 1
unit: exactly one won.

**Q: Why is `stock_transactions` append-only, and how is that enforced?**
It is the ledger — the source of truth for stock — and `qty_on_hand` is a cached rollup of it. It
is enforced by a database trigger that rejects UPDATE and DELETE, so history cannot be rewritten
even by the app's own DB user. A reconciliation view recomputes on-hand from the ledger to detect
drift.

**Q: How does the GST calculation work?**
Place-of-supply: if the delivery state equals the warehouse state it is intra-state (CGST + SGST,
each half the rate), otherwise inter-state (IGST, full rate) — never both, enforced by a CHECK.
Rounding is per-line then summed so the printed invoice adds up, and `grand = subtotal − discount
+ tax + shipping` is itself a CHECK.

**Q: Why snapshot the product name and price onto the order?**
An order is a historical record. If a product is renamed, repriced, or deleted, a past invoice
must still render as the customer received it. Joining live catalog rows to old orders silently
rewrites history — an accounting problem. (I verified this by mutating the live catalog and
confirming the old order was unchanged.)

**Q: How does refresh-token rotation with theft detection work?**
Each refresh use mints a new token and marks the old one rotated (`replaced_by_id`). If an
already-rotated token is presented again, two parties hold it — the real user and a thief — so we
revoke the entire session chain and force re-login.

**Q: Why does the cart not reserve stock?**
Reserving on add-to-cart would let anyone freeze the catalog by filling a cart and leaving, and
every abandoned cart becomes a stockout. The cart checks availability but only an order reserves.
The cost is stale carts, so the cart response reports which lines can no longer ship.

**Q: How is the Smart Cart health score computed, and what happens with missing data?**
It scales each line's per-100g nutrition by weight, then scores against UK FSA traffic-light
thresholds (sugar/fat/sodium lower-is-better, fibre/protein higher-is-better). If **any** item
lacks nutrition data, the score is `null` and the response says so — because scoring a partial
basket would rank unmeasured junk best of all.

**Q: How do you cache, and why not the same provider everywhere?**
Spring's provider-agnostic Cache abstraction. Locally it uses in-process Caffeine because a round
trip to cloud Redis is slower than the Postgres query it would save; in prod it uses Upstash
Redis where the app sits next to the cache. Service code names no provider — it is a config
switch.

**Q: How do you keep one app's Redis keys from colliding with another's on a shared Upstash DB?**
Every cache key is prefixed `ecoexpress:`. (This does not protect against a `FLUSHDB` from either
side, which is why a dedicated database is on the pre-launch list.)

---

## 9. Interview questions — Hard

**Q: Walk me through a webhook double-credit and how you make it impossible.**
Razorpay retries webhooks aggressively (timeouts, non-2xx, its own schedule). A naive handler
processes the retry and credits the order twice. We make it impossible at three levels:
(1) `UNIQUE(gateway, gateway_event_id)` so a retried event fails to insert — the DB is the
idempotency authority, not application memory; (2) we store the event before processing so a
crash leaves a replayable row; (3) we verify the amount, not just that a payment happened.
Verified by replaying a capture 5×: one payment row, order PAID once.

**Q: You verify the webhook signature. Does the *order* of verification vs recording matter?**
Yes — this was a real bug. If you record the event into the unique-keyed table *before*
verifying, a forged payload with a guessed event id occupies the idempotency slot and the genuine
event is later rejected as a duplicate — a denial attack on real payments. The fix is to verify
first and only consume the real idempotency key for verified events; invalid events are recorded
under a synthetic key for forensics.

**Q: Describe a transaction bug you had that a happy-path test would miss.**
Theft detection revoked the user's sessions and then threw to reject the replayed token — in one
transaction, so the throw rolled back the revocation. The token was rejected but the stolen
session stayed alive. A happy-path "is the replay rejected?" test passes; only asserting the
*consequence* — that the rest of the chain is now dead — catches it. The fix is a
`REQUIRES_NEW` transaction in a separate bean so the revocation commits despite the rejection.
You also have to check it does not deadlock against the outer pessimistic lock (it does not,
because the rotated row is already `revoked_at IS NOT NULL` and excluded from the revocation's
WHERE clause).

**Q: `MultipleBagFetchException` — what causes it and how do you fix it without a cartesian
product?**
Fetching two `List` collections in one query; Hibernate cannot attribute joined rows to the right
bag. Making them `Set` compiles but multiplies rows (variants × images). The fix is to join-fetch
one collection via an entity graph and batch-load the other with `@BatchSize`, giving two clean
queries with no multiplication.

**Q: How do you guarantee an invoice's own numbers add up?**
Rounding discipline plus DB constraints. Each line's tax is computed and rounded to 2dp, then
summed — never summed at full precision and rounded once, or the printed lines disagree with the
printed total. The CGST/SGST split gives any odd paisa to CGST so the halves always sum to the
line tax. And `grand_total = subtotal − discount + tax + shipping` plus `tax_total = cgst + sgst
+ igst` are both CHECK constraints, so a rounding bug cannot persist a wrong total.

**Q: You use `ddl-auto: validate`. Give three real mismatches it caught.**
(1) `citext` email columns vs Hibernate's `varchar` mapping; (2) `OAuthAccount` inheriting a
`deleted_at` the table did not have; (3) `CHAR(3)` currency vs `varchar`. Each was a boot-time
failure, not a runtime surprise — which is the point of `validate`.

**Q: How would you scale the reservation/checkout path under heavy contention?**
Today it is row-level `SELECT ... FOR UPDATE` with a 3s lock timeout, which serializes per stock
row — correct, and fine while contention per SKU is moderate. To scale a flash-sale on one SKU,
options are: shard the stock row into N sub-rows and reserve from a random shard (reduces
contention N-fold, reconcile in aggregate); move hot-SKU reservation to an atomic Redis counter
with the DB as async system-of-record; or queue reservations. Each trades consistency
immediacy for throughput; the current design deliberately favours correctness because overselling
organic groceries is a refund and a lost customer.

**Q: The stock cache (`qty_on_hand`) can drift from the ledger. How do you know, and how do you
recover?**
The `inventory_ledger_drift` view recomputes on-hand from the append-only ledger and returns any
row where the cache disagrees. It should always be empty; if it is not, the ledger is the source
of truth and the cache is rebuilt from it. Because the ledger cannot be mutated (trigger), the
recomputation is trustworthy.

---

## 10. System design and scaling

**Current shape:** one Spring Boot app, one Postgres, one Redis. Stateless auth (JWT), so the app
scales horizontally behind a load balancer without sticky sessions.

**First bottlenecks and the plan:**
- **Read-heavy catalog** → already cached; add read replicas and route product reads to them.
- **Hot-SKU checkout contention** → shard stock rows or move to a Redis counter (see hard Q).
- **Search** → Postgres full-text (GIN) is good to a point; beyond it, a dedicated search engine.
- **AI cost** → every Gemini call is logged with tokens and cost (`ai_request_logs`) against a
  monthly budget setting, so spend is visible before the bill, not after.
- **Media** → product and fridge images go to S3/MinIO, served via CDN, never through the app.

**Multi-warehouse** is the known next structural change: routing by delivery pincode to the
nearest stocking warehouse, and split shipments. The schema already supports N warehouses; only
`pickWarehouse()` needs to become real routing.

**Compliance is a first-class concern, not an afterthought:** GST invoicing (built), FSSAI and
NPOP/PGS-India organic certification tracking with expiry, and fridge-photo PII retention (a
`purge_after` column exists so a retention job has something to enforce once a policy is set).

---

## 11. What is not done yet

Being able to state the gaps precisely is itself a strong signal.

- **Google OAuth** — wired behind a `google-oauth` profile; needs EcoExpress's own Google Cloud
  client and the callback URI registered (deliberately not reusing another app's credentials,
  which would show the wrong name on the consent screen).
- **Multi-warehouse routing** — `pickWarehouse()` picks the first active warehouse; fine for one
  warehouse, needs pincode routing before a second.

Recently closed (were on this list):
- **Admin API for `gst_rate_pct` / `hsn_code`** — done. Settable on product create and PATCH,
  with the valid-rate set (0/0.25/3/5/12/18/28) enforced in the service for a clean 400 and the
  DB `products_gst_rate_chk` as the backstop. 7/7 e2e.
- **Scheduled job to release expired reservations** — done. `@EnableScheduling` + a
  `ReservationExpiryJob` running every 5 minutes with a 30-minute payment window; failures are
  logged and the next tick retries. (Single-instance for now; needs a shared lock before scaling
  horizontally — noted inline.)
- **Remaining modules** — purchase orders, coupons, reviews, wishlist, notifications, banners,
  admin analytics, and the five AI features (which need a Gemini key).
- **Frontend** — ~~Vite starter, needs a rebuild~~ **DONE**: rebuilt as a full Next.js 14 App Router
  app (TypeScript + Tailwind + shadcn), deployed to Vercel. See Part II §12.
- **Razorpay credentials** — the entire webhook/idempotency path is built and tested with a dummy
  secret; live keys plug in when ready. (Test keys now live; the checkout shows the test-card so a
  reviewer can complete a transaction — Part II §16.)

> **Note:** several items in this section were closed after Part I was written — see **Part II**
> below for the frontend, AI hardening, observability, and the live Render + Vercel deployment.

---

*Every behaviour in sections 3–6 was verified end-to-end against PostgreSQL 17. Test counts as of
this log: Auth 14/14, Catalog 20/20, Inventory 13/13, Cart 20/20, Orders 26/26, Payments 14/14,
GST admin + scheduler 7/7, Inventory admin (PO/adjustments/alerts) 16/16, Reviews + Wishlist 18/18,
Coupons 15/15, AI recs+pantry 18/18, AI meal-planner+fridge (live Gemini) 13/13, Engagement/admin
20/20 — 214 end-to-end assertions, all green.*

---
---

# Part II — Full build, frontend, AI hardening & the deployment (the parts Part I predates)

> Part I above was written mid-build and is backend-heavy; a couple of its "not done yet" items are
> now done. **The frontend is no longer a Vite starter — it is a complete Next.js 14 App Router app,
> deployed.** Google OAuth has a dedicated client. This part covers everything since: the frontend,
> the AI robustness/cost work, observability, the live deployment (Render + Vercel), the new feature
> areas, and eleven more war stories — most of them from actually shipping it to production.

## 12. The frontend (Next.js 14 App Router) — be ready for this

The app is now **Next.js 14 (App Router) + TypeScript + Tailwind + shadcn/ui**, layered exactly like
the backend: `lib` (axios instance, dayjs, formatters) → `api` (typed endpoint modules) → `types`
(mirror the backend DTOs) → `hooks` (TanStack Query wrappers) → `components`/`app`. One configured
axios instance carries the JWT and transparently refreshes on 401; one dayjs instance with plugins.

**Q: Server vs client components — how did you split them?**
Client components for anything interactive/stateful (cart, forms, the AI feature pages) — they use
hooks and TanStack Query. Server components for SEO metadata: each dynamic route has a server
`layout.tsx` that exports `generateMetadata()` (title, canonical, OpenGraph, JSON-LD Product/Article
schema) wrapping the client page. That gives real crawlable metadata without turning the whole page
into an RSC.

**Q: How is data fetching and cache handled on the client?**
TanStack Query v5 — every read is a `useQuery` keyed in a central `QUERY_KEYS`, every write a
`useMutation` that invalidates the affected keys. staleTime 30s, retry 1, no refetch-on-focus. This
gives request dedup, background refresh, and optimistic-feeling UX without a global store like Redux.

**Q: NEXT_PUBLIC_* — what bit you?**
They are **inlined at build time**, not read at runtime. So the API URL and site URL must be present
during `next build`, not just in the running container. On a Docker build (Render) that means
declaring each one as a build `ARG` + `ENV` before `npm run build`; env vars set only at runtime
never reach the bundle. This caused a real production build crash (War Story 26).

**Q: How did you do SEO on an SPA-ish app?**
Next Metadata API in server layouts: dynamic `sitemap.ts` (fetches categories + products), `robots.ts`,
`manifest.ts`, `opengraph-image.tsx` via `next/og`, and JSON-LD structured data. `react cache()` dedupes
the product fetch shared between `generateMetadata` and the page.

**Q: Component library — did you hand-roll UI?**
shadcn/ui (Radix primitives + Tailwind, copied into the repo, not a dependency you can't edit). I
built Button/Card/Dialog/Input/Table/Toast early, and later added Select, Tooltip, Popover, Calendar
+ a composed DatePicker, Pagination, and Breadcrumbs. Theme is CSS variables (HSL) with a `.dark`
class, driven by `next-themes` (system/light/dark). Everything is theme-aware.

**Q: Dark mode without a flash of the wrong theme?**
`next-themes` with `attribute="class"`, `defaultTheme="system"`, and `suppressHydrationWarning` on
`<html>`. It injects a tiny pre-hydration script that sets the class before first paint.

**Q: How does the storefront tell the user the backend is waking up?**
A `ServerWakeBanner` polls a public, dependency-free `/api/v1/ready` endpoint; if the first check
takes >1.2s it shows a red pulsing dot → green when ready. Free-tier hosts sleep and cold-start ~50s,
so this turns "the site is broken" into "the site is waking up." `/ready` is separate from
`/actuator/health` (which Render uses and which also checks the DB).

## 13. AI: robustness and cost control (the hard part of "add AI")

The AI sits behind an `AiClient` interface (Gemini via the OpenAI-compatibility surface, no SDK) and
an `AiService` wrapper that meters every call — tokens in/out, latency, estimated INR cost — into an
append-only `ai_request_logs` table, and **fails closed against a monthly budget**. AI is config, not
code: no key → the feature is cleanly disabled, never a 500.

**Q: LLMs return unreliable JSON. How did you make features depend on it safely?**
Three layers. (1) The prompt is **grounded in real data** and asks for strict JSON. (2) A shared
`LenientJson` utility strips markdown fences/prose, and — the key part — **repairs truncated replies**
by balancing unclosed strings/brackets (Gemini truncates under load; the data is all there, only the
closers are cut), then parses with a lenient reader that tolerates trailing commas. (3) A bounded
retry. So a truncated recipe still parses instead of 503ing.

**Q: How do you stop AI from recommending or "adding to cart" something you don't sell?**
Structurally: the AI never names products. Recommendations are a **DB query first** (same category,
active, in stock) — the AI, when used, only *re-ranks a candidate set we hand it*, and any ID it
returns is validated against that set. For "turn my cart into a meal," the AI invents a *recipe*
(safe general knowledge), then each ingredient is matched to a **real in-stock SKU**; anything we
can't match is shown as a plain "you'll also need…" note, never a fake add button.

**Q: How do you keep AI content factually safe and legally OK?**
Product content is AI-**drafted, human-approved**: the model writes a `DRAFT` no shopper sees; an
admin reviews and publishes. The prompt forbids disease-cure/prevention claims (FSSAI/ASCI) and
fabricated nutrition numbers; the "disease prevention" ask is stored under the compliance-safe name
`nutrient_support`, with a "general nutritional information, not medical advice" disclaimer.

**Q: The AI key hit its quota in production — what did you do?**
The (borrowed) Gemini free tier is 20 requests/day; testing exhausted it. So I made the limit
**visible**: the client detects rate-limit/quota errors (429/RESOURCE_EXHAUSTED), records a cooldown
in an in-memory `AiStatusService`, and exposes a public `GET /ai/status`. The storefront polls it and
shows an "AI features are busy, try again in ~Ns" banner instead of a cryptic failure, and disables
the AI buttons. Verified end-to-end. Real fix for launch: a billing-enabled key.

## 14. Observability — logging done deliberately

**Q: What logging framework, and why not Log4j?**
SLF4J + **Logback** (Spring Boot's default; `@Slf4j` in ~40 classes). Deliberately not Log4j — 1.x is
EOL/insecure and 2.x is a pointless lateral move. Logs go to **stdout only** — correct for a PaaS
(Render captures stdout; the filesystem is ephemeral). Never file logging.

**Q: How do you trace one request across the logs?**
A `RequestCorrelationFilter` (runs first) stamps each request with a correlation id in the **MDC** and
echoes it on the `X-Request-Id` response header. Every log line during that request carries it —
*including third-party (Netty/Spring AI) logs* — so one request is followable end-to-end. Levels are
env-tunable per package; production emits **one JSON object per line** (logstash encoder) under the
`prod`/`json` profile so an aggregator can parse fields, while dev stays human-readable.

## 15. Account self-service, recommendations, cart intelligence (feature deep-dives)

**Q: How does "change my email" work safely?**
**Verify-before-switch.** The new address is staged in `users.pending_email` and a verification token
carrying the new email is sent *to the new address*. The current email stays active until the link is
clicked; consuming the token swaps `email` and clears the pending value. This prevents both typo
lockout and hijacking (you can't move an email you can't receive). Same token table as first-time
verification, distinguished by a nullable `new_email` column.

**Q: Multiple addresses + a default — any subtlety?**
`addresses_one_default_per_user` is a partial unique index, so setting a new default must clear the
old first. The clear query has `@Modifying(clearAutomatically=true)`, which **detaches** the loaded
entity — so a naive `setDefault(true)` afterward is silently lost (War Story 30). Fix: `save()` to
re-merge. Deleting the default promotes the most-recent remaining address.

**Q: My Orders with tabs — how are the buckets defined server-side?**
`GET /orders?bucket=all|active|delivered|cancelled` maps each bucket to a set of statuses
(active = PENDING_PAYMENT…OUT_FOR_DELIVERY, cancelled = CANCELLED/RETURNED/REFUNDED) and returns a
proper `PageResponse` (the customer endpoint previously flattened to a bare list, discarding paging).

**Q: Pantry expiry reminders — cron or event?**
A Spring `@Scheduled` cron job (daily 08:00 IST, window + cron env-tunable) finds items expiring
within N days that haven't been reminded, groups by user, and sends **one summary as both an in-app
notification and a Resend email** (`PANTRY_EXPIRY` added to the emailed types), then stamps
`expiry_notified_at` so nobody is nagged twice. Caveat I volunteer: on a free host that sleeps, the
cron won't fire unless the service is kept awake — real production wants a keep-alive ping or an
external scheduler, and multi-instance needs ShedLock.

**Q: Pantry also auto-fills on delivery — how, without breaking delivery?**
The order's DELIVERED transition calls a pantry service in its **own** transaction (REQUIRES_NEW),
idempotent per order — so auto-stocking can never fail or roll back the delivery that triggered it.

## 16. Deployment — Render + Vercel (the saga, and what it teaches)

**Q: Where does it run and why the split?**
**Backend (Spring Boot) → Render** (Docker, `eclipse-temurin:21`), against a hosted **Neon** Postgres.
**Frontend (Next.js) → Vercel.** Vercel is built by the Next team — native Next builds, a global CDN,
and no cold-start sleep on the frontend (vs Render free-tier's ~50s nap). Spring Boot can't run on
Vercel, so the clean split is front-on-Vercel / API-on-Render — a common, deliberate architecture.

**Q: Config that crosses the two services?**
Backend `CORS_ALLOWED_ORIGINS` = the exact Vercel origin **with no trailing slash** (Spring matches
the `Origin` header exactly), OAuth success/failure redirects → the Vercel URL, and the Google OAuth
**callback stays the API URL** (the callback hits the backend). Frontend `NEXT_PUBLIC_API_BASE_URL` =
the API URL **+ /api/v1**, baked at build time.

**Q: Migrations and seed data on a fresh prod DB?**
Flyway runs all migrations on first boot (validated against Neon PG18 — a harmless "newer than
tested" warning). The bootstrap admin + a pre-verified demo customer are seeded by startup runners
(deliberately not Flyway — a committed password hash is forever and un-rotatable). The catalog is
**not** in migrations, so I seeded Neon via the admin API so the deployed store isn't empty.

**Q: Neon connection string gotchas?**
Three: use the **direct** endpoint, not the `-pooler` (pgjdbc has its own pool; Neon's PgBouncer
breaks prepared statements); **drop `channel_binding=require`** (a libpq option pgjdbc rejects); add
the `jdbc:` prefix. (War Story 31.)

## 17. War stories, continued — the shipping-to-production phase

> These are the ones that only appear when you actually deploy. Interviewers love them because the
> root cause is usually subtle and "it worked on my machine" is literally true.

### War Story 24 — the `.gitignore` that hid source code from the build (my favourite of this phase)
The Render build failed: `package com.ecoexpress.common.storage does not exist`. The app compiled and
ran **locally**. Root cause: `.gitignore` had a bare `storage/` (to skip the local uploads dir) — and
gitignore matches a folder of that name **anywhere in the tree**, so it also silently excluded the
**source package** `common/storage`. Git never pushed those 5 files; the cloud build had no
`StorageService`. Two lessons: (1) an ignore pattern for a runtime dir must be **anchored**
(`/server/storage/`), not a bare name; (2) "compiles locally" proves nothing about the repo — the CI
builds from *what git tracked*. Fix: anchor the pattern + `git add -f` the package (and note: once a
file is tracked, `.gitignore` no longer affects it). The exact same class of bug had also broken a
transfer zip I built with `robocopy /XD storage`.

### War Story 25 — building on a JDK newer than the toolchain supported
On a machine with **JDK 24**, compilation died with `com.sun.tools.javac.code.TypeTag :: UNKNOWN` —
Lombok 1.18.34 reaching into javac internals that changed in JDK 24. The project targets Java 21.
Two correct answers: use JDK 21 (matches the Render runtime), or bump Lombok to 1.18.38 (supports
JDK 24, still fine on 21). Kept `<java.version>21`, because a JDK-24 machine still compiles *to* Java
21 bytecode that runs on Render's JDK 21 — but compiling *to* 24 would produce class files the JDK-21
runtime can't load. The Netty `sun.misc.Unsafe` warnings on JDK 24 are cosmetic. Lesson: your
machine's JDK and the project's bytecode target are two different things; keep the target = prod.

### War Story 26 — the empty string that crashed the production build
The Next build crashed collecting `/_not-found`: `TypeError: Invalid URL, input: ''`. Cause:
`metadataBase: new URL(SITE_URL)` where `SITE_URL = process.env.NEXT_PUBLIC_SITE_URL ?? default` —
and `??` only falls back on `null`/`undefined`, **not** on an empty string. An unset Docker build ARG
passes `""`, which stayed `""`, and `new URL('')` throws. Fix: `||` instead of `??` (so empty falls
back too) *and* actually set the env var. Lesson: `??` vs `||` matters the moment "unset" can mean
"empty string," which is exactly what build-arg env vars do.

### War Story 27 — Radix Select forbids an empty value
Migrating native `<select>` to shadcn/Radix `Select`, several were filters with `<option value="">All</option>`.
Radix **throws at runtime** on `<SelectItem value="">`. Fix pattern: a sentinel — `value={x || 'ALL'}`
and `onValueChange={v => setX(v === 'ALL' ? '' : v)}` — so the UI has a non-empty value but the state
stays `""`. Applied in 5 filter/parent selects. Lesson: component-library migrations have invariants
the native element didn't; verify by grepping for the forbidden shape, because it's a runtime crash,
not a type error.

### War Story 28 — Gemini truncated its JSON under load
Recipe suggestion started returning "malformed suggestion" 503s intermittently. The logged raw output
showed a JSON object cut off mid-array — **truncated**, not garbage. And the real reason it kept
failing that day: the free-tier key had hit **20 requests/day**. Two fixes: salvage truncated JSON by
balancing brackets (War Story shared as the `LenientJson` util), and make quota **visible** via
`/ai/status` + a UI banner. Lesson: LLM output isn't just "sometimes wrong JSON," it's "sometimes
*incomplete* JSON," and free-tier quotas are a production constraint you must design around.

### War Story 29 — the unique index + Hibernate's insert-before-delete ordering
Replacing a product's images (clear old, add new) failed with
`duplicate key ... product_images_one_primary`. The index enforces one primary image per variant.
Hibernate, in a single flush, **inserts the new primary before deleting the old one**, so both are
primary momentarily → violation. Fix: `entityManager.flush()` after `clear()` (executes the deletes)
and before adding the new rows. Lesson: `orphanRemoval` + a unique constraint on the same rows needs
an explicit flush boundary, or you fight Hibernate's action ordering.

### War Story 30 — `clearAutomatically = true` silently detached my entity
"Set as default address" returned 200 but the default didn't change. `clearDefaultForUser` is a
`@Modifying(clearAutomatically = true)` bulk update — it clears the persistence context, **detaching**
the entity I'd loaded. The subsequent `address.setIsDefault(true)` was on a detached object and never
flushed. Fix: `save()` to re-merge. Lesson: `clearAutomatically` after a bulk update detaches
everything loaded before it; anything you mutate afterward must be re-saved.

### War Story 31 — the Neon connection string pgjdbc couldn't speak
The first deploy DB config failed to connect. Neon's `postgresql://` string uses the **pooler**
endpoint and `channel_binding=require` — both are libpq/PgBouncer features the JDBC driver either
breaks on (pooler + prepared statements) or rejects (`channel_binding`). Fix: direct endpoint (drop
`-pooler`), drop `channel_binding`, add `jdbc:` prefix. Lesson: a provider's copy-paste connection
string is tuned for *their* default client, not necessarily yours.

### War Story 32 — the transparent dropdown (a CSS variable the theme never defined)
The search suggestions dropdown rendered on a transparent background. `bg-popover` resolved to
nothing because the theme defined `--card` etc. but **never `--popover`** — shadcn components assume
it. Fix: add `--popover`/`--popover-foreground` (light + dark) + the Tailwind color. Lesson: an
undefined design token doesn't error, it just silently produces `background: ` (nothing) — audit that
the tokens your components reference actually exist.

### War Story 33 — "why is health behind auth?" (it must not be)
A reasonable-sounding question during deploy: should the health check require authentication? No — a
health endpoint **must** be public, or the platform's probe gets a 401, decides the service is
unhealthy, and never routes traffic (or restarts it forever). `/actuator/health` is explicitly
`permitAll`. Lesson: liveness/readiness probes are infrastructure, not user endpoints — gating them
behind auth is self-defeating.

### War Story 34 — the IDE "cannot find symbol" that Maven disagreed with
Right after the storage-package fix, VS Code showed `cannot find symbol StorageService` while
`mvn compile` was clean and the app was running. The redhat.java language server keeps its own
incremental-compile state that drifts after Maven rebuilds. Trust the build (javac/Maven) over the
IDE's in-memory analysis; fix with "Java: Clean Java Language Server Workspace." (Same family as War
Story 15's ghost `.class`, but the opposite direction — IDE wrong, build right.)

## 18. Frontend / product interview questions (rapid-fire)

**Q: Why TanStack Query over Redux?** The app's state is mostly *server* state (cart, orders,
catalog). Query gives caching, dedup, background refresh, and invalidation for free; Redux would be
boilerplate to re-implement that. Genuine client-only state (a form, a modal) is local `useState`.

**Q: How does the axios instance handle expiry?** One instance with a request interceptor that
attaches the access token and a response interceptor that, on 401, tries the refresh token once,
retries the original request, and bounces to login if refresh fails.

**Q: Search-as-you-type — how?** Debounced (300ms) query in the header drives a dropdown of the top 6
matches; ↑/↓/Enter/Esc keyboard nav; click-outside closes. Backend search adds a substring (ILIKE)
fallback to the full-text match so partial words ("tom" → "Tomato") work — full-text alone is
whole-word.

**Q: How are product images served in prod without object storage on the critical path?** The seeded
catalog uses self-contained SVGs in the frontend's `public/` (static, on the CDN) — no R2 dependency
to render the demo. Real uploaded images/certs go to R2 behind the `StorageService` interface.

**Q: Demo login for recruiters — and the risk?** One-click "As Admin"/"As User" on the login page.
The honest risk I raise unprompted: "As Admin" grants full admin on a public URL and bakes the admin
password into the client bundle — fine for a showcase, must be gated off before real customer data.

## 19. Behavioral & meta questions (have real answers)

**Q: Tell me about the hardest bug.** Pick one with a subtle root cause and a clear lesson — the
webhook signature-before-idempotency security hole (War Story 14), or the `.gitignore` hiding source
from the build (War Story 24). Both show you reason about *why*, not just patch symptoms.

**Q: A time "it worked on my machine."** War Story 24 verbatim — local compiled, CI didn't, because
git tracked a different set of files than my disk had. The fix taught me to think about the repo as
the source of truth, not the working directory.

**Q: How do you decide build vs buy / your own vs a library?** Provider-behind-an-interface
(`AiClient`, `StorageService`, `EmailSender`): I own the seam, the provider is config. I didn't pull
SDKs where a thin HTTP client sufficed (Razorpay, Resend, Gemini) — fewer transitive deps, full
control of the failure shape.

**Q: What would you do differently / what's the tech debt?** Multi-warehouse routing (first-warehouse
today), the AI matcher is substring not embeddings, tests aren't in CI, and the demo shares borrowed
keys (Gemini/Resend/R2/OAuth) that need dedicated accounts before launch. Stating these precisely is
the signal.

**Q: How did you keep quality without a big team?** `ddl-auto: validate` catches schema drift at boot;
end-to-end tests drive real HTTP against a real DB (not mocks) and *adversarially check the
consequence*, not the happy path — that's what caught the two security bugs. And a running "war stories"
log so a lesson is learned once.

## 20. System design — "how would you scale this?" (extended)

- **Frontend already scales** — static/SSG on Vercel's CDN; the API is the stateful tier.
- **Stateless API** — JWTs mean any instance serves any request; scale horizontally behind a load
  balancer. Move caching from Caffeine (in-process) to Redis (already abstracted; the `prod` profile
  switches it) so instances share a cache.
- **The scheduled jobs are the catch** — `@Scheduled` fires on *every* instance; before scaling past
  one, put them behind ShedLock or move them to a dedicated scheduler / external cron.
- **Payments already assume concurrency** — DB unique constraint on `(gateway, event_id)` for webhook
  idempotency, `SELECT … FOR UPDATE` on stock. Those don't change when you add instances.
- **AI cost is the real scaling axis** — the per-call metering + monthly budget + `/ai/status`
  cooldown already exist; at scale you'd add per-user rate limits and cache identical prompts.
- **Read scale** — the product page is cached and eviction is coarse; a CDN/edge cache in front of
  read endpoints and a read replica for Postgres are the next steps.

---

*Part II covers the frontend, AI hardening, observability, the Render+Vercel deployment, and War
Stories 24–34 (the shipping phase). Backend behaviours remain as verified in Part I; the frontend
typechecks clean and every storefront + admin route renders. Live: backend on Render, frontend on
Vercel.*

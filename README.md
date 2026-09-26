# Hypermedia Resource API (Java / Spring Boot)

A hypermedia-driven HTTP API in which clients advance resource state **only**
through links and executable fields returned by the server. No path is
hard-coded by the client: navigation starts at `GET /` and follows link
relations.

The domain is a small order lifecycle service (customers, products, orders)
that demonstrates:

1. state-dependent transition links (illegal transitions are never linked);
2. on-demand embedded resources with depth limits, field selection and cycle
   protection;
3. stable, paginated collection navigation (`first` / `last` / `prev` /
   `next`) with distinguishable out-of-range and empty representations;
4. ETag / `If-Match` / `If-None-Match` conditional reads and updates,
   optimistic concurrency conflicts and idempotent repeated submissions;
5. content negotiation between two hypermedia JSON representations with an
   explicit negotiation-failure contract;
6. errors that are themselves navigable hypermedia resources.

## Run and test

```bash
mvn spring-boot:run          # starts on :8080
mvn test                     # 42 tests (Spring Boot integration tests)
```

Try it:

```bash
curl -s localhost:8080/
curl -s localhost:8080/orders/o-1
curl -s localhost:8080/orders/o-3?embed=customer,items.product
curl -s localhost:8080/orders?page=1&size=2
curl -s localhost:8080/orders/o-1 -H 'Accept: application/vnd.hypermedia+json'
```

## Resource model

| Resource | Routes | State / notes |
|---|---|---|
| Home | `GET /` | link container; bootstrap point |
| Customer | `GET /customers`, `POST /customers`, `GET /customers/{id}`, `PUT /customers/{id}`, `GET /customers/{id}/orders` | mutable, optimistic `version`, ETag |
| Product | `GET /products`, `GET /products/{id}` | immutable catalog items |
| Order | `GET /orders`, `POST /orders`, `GET /orders/{id}`, `POST /orders/{id}/transitions` | state machine, optimistic `version`, ETag |

### Order state machine

```
CREATED ──pay────▶ PAID ──ship────▶ SHIPPED ──deliver────▶ DELIVERED
   │                 │
   └──cancel─────────┴──cancel────▶ CANCELLED
```

- `pay` is legal only in `CREATED`;
- `ship` only in `PAID`;
- `deliver` only in `SHIPPED`;
- `cancel` in `CREATED` or `PAID`;
- `DELIVERED` and `CANCELLED` are terminal (no transition links rendered).

Transition links are POST links to `/orders/{id}/transitions` with an
`action` attribute:

```json
"pay": {
  "href": "/orders/o-1/transitions",
  "method": "POST",
  "action": "pay",
  "title": "Execute 'pay' transition"
}
```

A transition that is not legal for the **current** state is never rendered
and, if invoked directly, is rejected with `409 illegal-transition` without
changing state.

## Navigation semantics

- Every representation carries a `self` link and only transitions legal in
  the current state/context.
- Link order is fixed and documented (assemblers populate link maps in a
  deterministic order: `self`, collection, related resources, then
  state-dependent transitions in the order `pay, ship, deliver, cancel`).
- Links use absolute paths; the optional prefix is configured with
  `hypermedia.base-path`.
- Unsafe operations are advertised with a `method` attribute (`POST`/`PUT`).

## Representations (content negotiation)

Negotiation is driven **only** by the `Accept` header (path extensions and
`?format=` are disabled).

| Media type | Shape |
|---|---|
| `application/prs.hal-custom+json` (default) | HAL-style: `_links` map, `_embedded` map, properties at top level |
| `application/vnd.hypermedia+json` | Plain: `state` object, `links` array (`rel` + `href`), `embedded` object |
| `application/json` / `*/*` | Accepted; served with the HAL shape |

When none of the client's preferred types is available the response is
`406 content-negotiation-failed` with a `details.availableRepresentations`
list. Switching representation never changes resource state; both shapes
carry the same properties, links, ETag semantics and status codes.

### HAL example

```json
{
  "_links": {
    "self": { "href": "/orders/o-1" },
    "orders": { "href": "/orders" },
    "customer": { "href": "/customers/c-1" },
    "pay": { "href": "/orders/o-1/transitions", "method": "POST", "action": "pay" },
    "cancel": { "href": "/orders/o-1/transitions", "method": "POST", "action": "cancel" }
  },
  "id": "o-1",
  "state": "created",
  "version": 0,
  "items": [ { "productId": "p-1", "quantity": 1, "unitPrice": 1000 } ]
}
```

## Embedding (`embed`, `embed-depth`, `fields`)

- `?embed=customer` — embed single relations;
- `?embed=items.product` — multi-segment paths (nested embeds);
- repeatable / comma-separated; paths are de-duplicated;
- `?embed-depth=N` limits expansion depth; values above
  `hypermedia.embed-max-depth` (default 3) are clamped;
- `?fields=id,state` — sparse top-level properties (links are unaffected).

Determinism rules:

- `_embedded` keys appear in **first-occurrence order** from the query string;
- embedded list elements keep the aggregate's stable store order;
- unknown relations are ignored (best-effort embedding), never an error;
- expansion tracks an ancestry chain of resource ids. When a chain revisits
  an already present resource (e.g. `customer → orders → customer`), the
  cycle edge stays a **link** and is not re-expanded, so no infinite
  recursion occurs;
- paths beyond the depth budget are truncated at the budget boundary
  (deepest nodes remain links only).

## Pagination

`?page=` (zero-based, default 0) and `?size=` (default
`hypermedia.default-page-size=5`, capped at `hypermedia.max-page-size=20`).

Collection responses include a `page` object:

```json
"page": { "size": 2, "number": 1, "totalElements": 5, "totalPages": 3 }
```

Navigation links:

- `self` — current page;
- `first` / `last` — present for any non-empty collection;
- `prev` — absent on page 0; `next` — absent on the last page;
- on an **empty collection** (`totalElements = 0`), `self`/`first`/`last`
  describe page 0 and the response is **200** with `items: []`,
  `totalPages: 0`;
- an **out-of-range page** on a non-empty collection is **416**
  `page-out-of-range` — a distinct, documented representation;
- invalid `page`/`size`/`embed-depth` values are **422** `invalid-request`.

Ordering is stable: orders sort by `createdAt` ascending then `id`;
customers and products preserve insertion order.

## Conditional requests and concurrency

Each mutable aggregate has a monotonic `version`; its strong ETag is

```
"<id>:v<version>"     e.g.  "o-1:v2"
```

- `GET` with `If-None-Match: <etag>` returns **304** when current, **200**
  after the resource changes.
- `PUT /customers/{id}` requires `If-Match` (**428**
  `precondition-required` when absent). A stale validator returns **412**
  `precondition-failed` and applies no change; a matching validator applies
  the update, bumps `version`, and returns the new ETag.
- `POST /orders/{id}/transitions` accepts an optional `If-Match` for
  optimistic concurrency (412 on mismatch).
- Repeating an already completed transition without idempotency keys
  returns 409 and never changes state twice.
- Sending `Idempotency-Key` on `POST /orders` or on a transition makes the
  request execute **at most once**: duplicate submissions replay the first
  outcome (same status/resource) without a second state change. The same key
  with a different payload is rejected with **422** `invalid-request`.
  Concurrent requests sharing a key are serialized.
- All validate-then-write sequences run under the store's exclusive lock;
  concurrent identical legal transitions result in exactly one success and
  409 for the rest, and the version increments exactly once. Failures leave
  no intermediate state.

## Error contract

Every error is a hypermedia resource:

```json
{
  "_links": {
    "self": { "href": "/orders/o-1/transitions" },
    "home": { "href": "/" },
    "retry": { "href": "/orders/o-1" },
    "current": { "href": "/orders/o-1" }
  },
  "error": {
    "status": 409,
    "code": "illegal-transition",
    "message": "Transition 'ship' is not legal from state CREATED.",
    "retryable": true
  }
}
```

- `self` and `home` are always present;
- `retry`/`current` are safe GET links and appear only where applicable;
- error bodies honour the negotiated media type, including the 406 case
  (served as HAL with the list of available representations);
- stack traces and internal class names are never exposed.

| HTTP | code | retryable | Meaning |
|---|---|---|---|
| 404 | `resource-not-found` | yes | aggregate does not exist |
| 406 | `content-negotiation-failed` | no | no acceptable representation |
| 409 | `illegal-transition` | yes | transition not legal in current state |
| 412 | `precondition-failed` | yes | If-Match / If-None-Match failed |
| 416 | `page-out-of-range` | no | page index beyond last page |
| 422 | `invalid-request` | no | malformed body / query / idempotency reuse |
| 428 | `precondition-required` | no | update attempted without If-Match |
| 500 | `internal-error` | no | generic, detail-free fallback |

## Configuration

| Property | Default | Purpose |
|---|---|---|
| `hypermedia.embed-max-depth` | `3` | max `embed-depth`, deeper paths truncated |
| `hypermedia.default-page-size` | `5` | default `size` |
| `hypermedia.max-page-size` | `20` | cap for `size` |
| `hypermedia.base-path` | empty | prefix for all link hrefs |

## Project layout

```
domain/    Order state machine, Customer, Product, OrderItem
store/     locked in-memory store with snapshot reads (InMemoryStore)
represent/ Link, Resource, ResourceList, ErrorResource, Representable
service/   assemblers, EmbedTree (cycles/depth/order), ListAssembler,
           OrderStateService, IdempotencyService, ConditionalRequests
web/       two hypermedia JSON message converters, deterministic writer,
           content-negotiation config, query-parameter parser
api/       Home/Customer/Product/Order controllers and request DTOs
error/     typed exceptions, ApiExceptionHandler, ErrorFactory
config/    HypermediaProperties, deterministic seed data
```

## Seed data

2 customers (`c-1`, `c-2`), 3 products (`p-1` Widget 1000, `p-2` Gadget
2500, `p-3` Gizmo 500), and 5 orders spread across every state
(`o-1` CREATED … `o-5` CANCELLED) with fixed ids/timestamps, so listing
order, ETags, link visibility and pagination behaviour are reproducible.

## Test coverage

- `LinkVisibilityIntegrationTest` — transition links appear/disappear with
  state; illegal transition 409; terminal states have no links.
- `EmbedIntegrationTest` — on-demand embeds, nested `items.product`, cycle
  termination, depth truncation, field selection, deterministic key order.
- `PaginationIntegrationTest` — stable ordering, prev/next presence, 416 vs
  empty 200, size clamping, invalid page 422.
- `ConditionalConcurrencyIntegrationTest` — 304/412/428, idempotent replay,
  key reuse 422, 8-thread race with exactly one winner, no intermediate state.
- `ContentNegotiationIntegrationTest` — HAL/plain semantic equivalence, 406
  contract, representation switch is side-effect free.
- `ErrorContractIntegrationTest` — every error status, navigable links,
  retry flags, no internal detail leakage.

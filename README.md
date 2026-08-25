# Café Billing

A small **Spring Boot** café counter: browse the menu, add drinks, adjust quantities, and show an itemized bill whose **prices, discount, and final amount come from the backend**.

## Prerequisites

- Java 21 or newer
- A browser

Gradle is provided by the wrapper. No database, Docker, cloud account, or API keys are required.

## Run locally

```bash
./gradlew bootRun
```

This starts the Spring Boot app. Open [http://127.0.0.1:8080](http://127.0.0.1:8080).


## Run backend tests

```bash
./gradlew test
```
- `CafeApiTest` — `GET /api/menu`, `POST /api/bills` success path, 400 error shape, client-supplied prices ignored
- `BillingCalculatorTest` — 100 / 200 CNY boundaries, the 115 / 201 examples, Latte promo stacking, duplicate codes merged, unknown code / bad quantity rejected, BigDecimal money

## What it does

1. On load, the UI fetches the menu from the server. The bill starts empty (*No items yet…*).
2. Adding an item that is already on the bill increases its quantity instead of adding a duplicate line.
3. Quantity can be increased, decreased, or removed. Decreasing below 1 removes the line.
4. The UI sends `{ code, quantity }` to `POST /api/bills` after each cart change so the displayed totals always come from the server. Prices never go in that request.
5. The rendered totals are the server response: line totals, subtotal, discount, final amount, currency **CNY**.
6. If `POST /api/bills` fails, the cart is kept. A **red error banner** appears **above the bill**, with a **Retry** button. That banner is not shown on a successful update. To see it: load the app and add an item, stop the server, add or change a quantity — the red bar and **Retry** show up. Start the server again and click **Retry**.

## Discount rules (server)

| Subtotal | Discount |
| --- | --- |
| ≤ 100 CNY | None |
| > 100 and ≤ 200 CNY | 10% of the entire subtotal |
| > 200 CNY | 10% of the first 200 CNY, plus 20% of the remainder |

Worked example from the brief (this is the desktop demo order):

| Item | Qty | Line |
| --- | --- | --- |
| Coffee — Latte (CL, 30) | 1 | 30 |
| Tea — Ice (TI, 15) | 3 | 45 |
| Cold Drink — Pepsi (CDP, 20) | 2 | 40 |
| **Subtotal** | | **115.00** |

115 is in the 10% band → discount 11.50, final **103.50**.

Second brief example (subtotal 201, not a specific menu combo):

| Band | Amount | Rate | Discount |
| --- | --- | --- | --- |
| First 200 CNY | 200.00 | 10% | 20.00 |
| Remainder | 1.00 | 20% | 0.20 |
| **Total** | **201.00** | | **20.20** |

Final amount: 201.00 − 20.20 = **180.80**.

## API

### Request flow

- `GET /api/menu`: browser → `CafeApiController` → `MenuCatalog` → JSON menu
- `POST /api/bills`: browser → `CafeApiController.createBill` → `BillingCalculator.calculateBill` (prices from `MenuCatalog`) → JSON bill

### `GET /api/menu`

```json
{
  "currency": "CNY",
  "items": [
    { "code": "CL", "name": "Coffee — Latte", "category": "Coffee", "unitPrice": "30.00" }
  ]
}
```

### `POST /api/bills`

Note: The request may only send **item code and quantity**. Prices are not accepted from the browser. Extra fields such as `unitPrice` are ignored; the line still uses the server menu (Latte stays 30.00, not 1.00)

#### Success

Request:

```json
{
  "items": [
    { "code": "CL", "quantity": 1 },
    { "code": "TI", "quantity": 3 },
    { "code": "CDP", "quantity": 2 }
  ]
}
```

Response. Money fields are two-decimal strings so the JSON does not go through binary floating point:

```json
{
  "currency": "CNY",
  "lines": [
    {
      "code": "TI",
      "name": "Tea — Ice",
      "unitPrice": "15.00",
      "quantity": 3,
      "lineTotal": "45.00"
    },
    {
      "code": "CL",
      "name": "Coffee — Latte",
      "unitPrice": "30.00",
      "quantity": 1,
      "lineTotal": "30.00"
    },
    {
      "code": "CDP",
      "name": "Cold Drink — Pepsi",
      "unitPrice": "20.00",
      "quantity": 2,
      "lineTotal": "40.00"
    }
  ],
  "subtotal": "115.00",
  "discount": {
    "amount": "11.50",
    "description": "10% of the entire subtotal"
  },
  "finalAmount": "103.50"
}
```

#### Failure (HTTP 400)

Unknown code, request:

```json
{
  "items": [
    { "code": "ZZ", "quantity": 1 }
  ]
}
```

Response:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "The bill request is invalid.",
    "details": [
      { "field": "items[0].code", "issue": "UNKNOWN_ITEM", "message": "Unknown item code 'ZZ'." }
    ]
  }
}
```

Non-positive quantity uses the same envelope. Request:

```json
{
  "items": [
    { "code": "CL", "quantity": 0 }
  ]
}
```

Response:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "The bill request is invalid.",
    "details": [
      {
        "field": "items[0].quantity",
        "issue": "INVALID_QUANTITY",
        "message": "Quantity must be a positive integer."
      }
    ]
  }
}
```


## Layout

- **Desktop (≥ 900px):** menu and bill sit side by side; the bill stays sticky while the menu scrolls.
- **Mobile:** a single column. Quantity and remove controls are at least 44px. A header chip shows the current final amount and jumps to the receipt, so totals stay visible without covering bill controls.

Screenshots:

- Desktop: `screenshots/desktop.png`
- Mobile: `screenshots/mobile.png`

With more time: a collapsible mobile bill drawer.

## Project structure

```
src/main/java/com/cafebilling/CafeBillingApplication.java  Spring Boot entry point
src/main/java/com/cafebilling/billing                     Pure discount and bill calculation (unit-tested)
src/main/java/com/cafebilling/menu                        In-memory menu and server prices
src/main/java/com/cafebilling/api                         REST controllers and error model
src/main/resources/static                                 Browser UI (served by Spring Boot)
src/test/java                                             Billing unit tests and API tests
```

## Assumptions and trade-offs

- Duplicate codes in one request are merged rather than rejected.
- An empty `items` list is a valid zero bill, not an error.
- The UI updates the bill automatically after cart changes (no separate Calculate button).
- No persistence, auth, tax, or payments — those are out of scope.

## Bonus extras

### Accessibility

#### Keyboard

- Controls are real HTML `button`s, so the browser handles Tab focus and Enter / Space activation
- When clicking the Tab, it reaches **Skip to menu**, Add, quantity `+` / `−`, Remove, Retry, and Print in sequence
- Keyboard focus shows a visible outline (`:focus-visible`).

#### Screen reader

- Landmarks (`header`, `main`, `section`, `aside`) and headings keep Menu and Bill as separate regions.
- Quantity `+` / `−` and Remove use full `aria-label`s (e.g. “Increase Latte”), not a bare “+”.
- While recalculating, the bill panel is marked `aria-busy`; the final amount uses `aria-live` so updates can be spoken.

### Latte 25%

25% off Latte when quantity is 2 or more. Applied to the list-price subtotal first; the 100 / 200 CNY tiers then apply to what remains, so the same 60 CNY is not discounted twice. Line totals stay at unit price × quantity.

Example — two Lattes + 3 Mocha (list 180). After the Latte promo, **165** is still in the 100–200 band, so 10% of that remainder also applies:

| | Amount |
| --- | --- |
| Latte line (30 × 2) | 60.00 |
| Mocha line (40 × 3) | 120.00 |
| **List subtotal** | **180.00** |
| Latte promo (25% of 60) | −15.00 |
| Amount after promo | 165.00 |
| Tiered discount (10% of 165) | −16.50 |
| **Total discount** | **31.50** |
| **Final** | **148.50** |

### Printable receipt

**Print bill** opens the browser print dialog. A print stylesheet hides the menu and controls and keeps the receipt, instead of a second HTML page. Larger type on the printed receipt would be a further improvement.

### Formatter 

**Spotless** is a Gradle plugin that enforces a consistent code style. Here it runs **Google Java Format** on `src/**/*.java` (indentation, wrapping, import order). It does **not** run the app or the unit tests. HTML, CSS, JS, and the README are not included.

```bash
./gradlew spotlessCheck   # check only: fails if any Java file is off-format; does not rewrite files
./gradlew spotlessApply   # rewrite: formats every src/**/*.java to match Google Java Format
```

## AI Coding Disclosure

### Model

Cursor Grok 4.6 

### Tools

Cursor agent. Used for: implementation, tests, UI, README

### Validation harness

- `./gradlew test` — billing thresholds at 100 / 200 CNY, the 115 and 201 examples, Latte quantity ≥ 2, unknown codes, invalid quantities, and API error shape
- `./gradlew spotlessCheck` — verify every `src/**/*.java` matches Google Java Format; fails if off, does not rewrite files
- Manual desktop journey: open app → add Latte, 3× Ice Tea, 2× Pepsi → confirm CNY 103.50
- Manual mobile journey: same flow at a narrow viewport, including quantity, remove, and the final-amount chip
- Keyboard: Tab to Add / quantity / Remove; confirm a visible focus outline and that `+` is labelled as increase for that drink
- Two Lattes: confirm discount 15.00 and final 45.00
- Print bill: confirm the print preview is the receipt without menu/controls
- Forced API failure: stop the server (or go offline in DevTools), change the cart, confirm the selection is kept and **Retry** is shown; start the server and click **Retry**.

### Decisions after reviewing generated code

#### Architecture

- **Billing vs HTTP:** the first version mixed discount math into the controller. Totals and discounts were moved into `BillingCalculator` so the 100 / 200 CNY rules are unit-tested with plain JUnit; `CafeApiTest` only samples the JSON contract. Domain calculation stays free of Spring Web.
- **UI hosting:** static HTML/CSS/JS is served by the same Spring Boot process as the API. A second frontend server would add setup with no product value for this brief.
- **Error surface:** early version mixed 400 and 422. The API was unified on HTTP **400** with one envelope (`VALIDATION_ERROR` + field-level `details`) so the UI has a single Retry path and tests assert one shape.

#### Technology choices

- **Stack:** the first backend was Python / FastAPI (fast to scaffold). It was replaced with **Java 21 / Spring Boot / Gradle** for three reasons: (1) **money correctness** — `BigDecimal` with half-up to two cents is the natural fit for CNY bills; Python `float` (and even casual `Decimal` use) is easier to get wrong under time pressure; (2) **reproducible review** — `./gradlew` + wrapper pins the Gradle version, Spring Boot BOM pins libraries, so reviewers do not need a matching local Python/venv; (3) **test story matches the brief** — JUnit unit tests on `BillingCalculator` plus Spring MockMvc API tests are a clear split. Prototype speed was traded for currency safety and a one-command run/test path.
- **No extra frontend framework:** the counter is small enough that vanilla JS + `createElement` / `textContent` is enough; React (or similar) would add a build step without changing the server-owned bill contract.

#### Technical details

- **Money in JSON:** amounts are two-decimal strings (`"11.50"`), not JSON numbers, so clients never re-parse money as binary floats. Rates use `new BigDecimal("0.10")`, never a `double` literal like `0.10`.
- **DOM safety:** generated menu rendering used `innerHTML` for prices. It was switched to `textContent` / `createElement` so drink names and amounts are never interpreted as HTML.
- **Mobile layout (found in browser):** a sticky bottom “final amount” bar covered quantity and Remove. It was replaced with a header chip that jumps to the receipt — only visible after a real narrow-viewport pass, not in the first generated CSS.
- **Cart ↔ bill sync:** the first UI needed an **Update bill** click, so the receipt could lag the cart. After using it, every cart change POSTs `/api/bills` immediately; displayed totals are always the last successful server response (with Retry if the request fails).

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

Request flow:

- `GET /api/menu`: browser → `CafeApiController` → `MenuCatalog` → JSON menu
- `POST /api/bills`: browser → `CafeApiController.createBill` → `BillingCalculator.calculateBill` (prices from `MenuCatalog`) → JSON bill

`GET /api/menu`

```json
{
  "currency": "CNY",
  "items": [
    { "code": "CL", "name": "Coffee — Latte", "category": "Coffee", "unitPrice": "30.00" }
  ]
}
```

`POST /api/bills`

```json
{
  "items": [
    { "code": "CL", "quantity": 1 },
    { "code": "TI", "quantity": 3 },
    { "code": "CDP", "quantity": 2 }
  ]
}
```

Successful response. Money fields are two-decimal strings so the JSON does not go through binary floating point:

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

Invalid item codes or non-positive quantities return **400** with a consistent body:

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

The browser cannot set prices. Extra fields such as `unitPrice` on a bill request are ignored.

## Layout

- **Desktop (≥ 900px):** menu and bill sit side by side; the bill stays sticky while the menu scrolls.
- **Mobile:** a single column. Quantity and remove controls are at least 44px. A header chip shows the current final amount and jumps to the receipt, so totals stay visible without covering bill controls.

Screenshots:

- Desktop: `screenshots/desktop.png`
- Mobile: `screenshots/mobile.png`

### Accessibility

The UI is keyboard-usable and labelled for assistive tech, not only clickable:

- Semantic regions (`header`, `main`, `section`, `aside`) and headings so Menu and Bill are distinct.
- A skip link jumps to the menu. Quantity `+` / `−` and Remove have `aria-label`s (a lone “+” is not enough).
- Real `button`s, so Tab reaches Add, quantity, Remove, Retry, and Print. `:focus-visible` draws an outline on the focused control.
- Errors use text plus a Retry control, not colour alone. Discount includes a short description.
- While a bill request is in flight, controls are `disabled` and the bill region is `aria-busy`.

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

- Java 21 / Spring Boot / Gradle so money can be `BigDecimal` with half-up rounding to two cents, and so the app runs with the Gradle wrapper. The wrapper pins Gradle; the Spring Boot BOM pins library versions.
- Duplicate codes in one request are merged rather than rejected.
- An empty `items` list is a valid zero bill, not an error.
- The UI updates the bill automatically after cart changes.
- Latte’s 25% promotion is applied before the subtotal tiers, so the two discounts do not double-count the same 60 CNY.
- No persistence, auth, tax, or payments — those are out of scope. Completed bills are not stored; a JSON file or an embedded store could be added later without changing the bill calculator.

## AI Coding Disclosure

- **Model:** Cursor Grok 4.6 (SpaceXAI / Cursor)
- **Tools:** Cursor agent (implementation, tests, UI, README). No other agents.
- **Validation harness:**
  - `./gradlew test` — billing thresholds at 100 / 200 CNY, the 115 and 201 examples, Latte quantity ≥ 2, unknown codes, invalid quantities, and API error shape
  - `./gradlew spotlessCheck` — Google Java Format
  - Manual desktop journey: open app → add Latte, 3× Ice Tea, 2× Pepsi → confirm CNY 103.50
  - Manual mobile journey: same flow at a narrow viewport, including quantity, remove, and the final-amount chip
  - Keyboard: Tab to Add / quantity / Remove; confirm a visible focus outline and that `+` is labelled as increase for that drink
  - Two Lattes: confirm discount 15.00 and final 45.00
  - Print bill: confirm the print preview is the receipt without menu/controls
  - Forced API failure: stop the server (or go Offline in DevTools), change the cart, confirm the selection is kept and **Retry** is shown; start the server and click **Retry**.

### Decisions after reviewing generated code

These are the choices kept or changed after reading the generated implementation, not the first draft as-is:

- **Stack:** the first backend was Python / FastAPI. After review it was replaced with Java 21 / Spring Boot / Gradle so money could be `BigDecimal` and the project would match a Java workspace.
- **Billing isolation:** discount and line totals live in `BillingCalculator`, not the REST controller, so the 100 / 200 CNY rules can be unit-tested without HTTP.
- **Money in JSON:** amounts are two-decimal strings (`"11.50"`), not JSON numbers, so clients never parse them as binary floats. Internally, rates are `new BigDecimal("0.10")`, never `0.10`.
- **Errors:** one HTTP **400** envelope (`VALIDATION_ERROR` + `details`) instead of mixing 400 and 422.
- **UI hosting:** static HTML/CSS/JS is served by the same Spring Boot process as the API, so there is no second frontend server.
- **Mobile layout (after a browser pass):** a bottom “final amount” bar covered quantity/remove controls. It was moved to a header chip that jumps to the receipt.
- **DOM (after reviewing generated JS):** menu prices are set with `textContent`, not `innerHTML`.
- **Cart UX:** the first UI required an **Update bill** click. After using it, cart changes POST to `/api/bills` immediately so the displayed totals always match the server.

### Bonus extras

Added after the required slice was working. Not needed to run the café flow.

- **Latte 25%** (PDF Functional Extension, not required): 25% off Latte when quantity is 2 or more. Applied to the list-price subtotal first; the 100 / 200 CNY tiers then apply to what remains. Line totals stay at unit price × quantity. Two Lattes: list 60.00, discount 15.00, final 45.00.
- **Printable receipt** (PDF Functional Extension, not required): **Print bill** opens the browser print dialog. A print stylesheet hides the menu and controls and keeps the receipt, instead of a second HTML page. Larger type on the printed receipt would be a further improvement.
- **Persistence** of completed bills was skipped so the in-memory calculator stays the source of truth.
- **Accessibility** (labels, keyboard, focus) was in the first UI, not added as a late extra.

**Formatter (PDF Engineering Practices):** Spotless with Google Java Format. This does **not** run the app or the unit tests. It only checks that every `src/**/*.java` file matches the format (indentation, wrapping, import order). The build fails if something is off; it does not rewrite files. HTML, CSS, JS, and the README are not included.

```bash
./gradlew spotlessCheck
```

To apply the format: `./gradlew spotlessApply`.

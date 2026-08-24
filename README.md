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

The same process serves the UI and the JSON API:

- `GET /api/menu`
- `POST /api/bills`

## Run backend tests

```bash
./gradlew test
```

## What it does

1. On load, the UI fetches the menu from the server and starts with an empty bill.
2. Adding an item that is already on the bill increases its quantity instead of adding a duplicate line.
3. Quantity can be increased, decreased, or removed. Decreasing below 1 removes the line.
4. The UI sends `{ code, quantity }` to `POST /api/bills` after each change, and also has an **Update bill** button.
5. The rendered totals are the server response: line totals, subtotal, discount, final amount, currency **CNY**.
6. If a request fails, the current selection is kept, an error is shown, and Retry / Update bill can be used again.

### Discount rules (server)

| Subtotal | Discount |
| --- | --- |
| ≤ 100 CNY | None |
| > 100 and ≤ 200 CNY | 10% of the entire subtotal |
| > 200 CNY | 10% of the first 200 CNY, plus 20% of the remainder |

Worked examples from the brief: 115 CNY → discount 11.50, final 103.50; 201 CNY → discount 20.20, final 180.80.

## API

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

Successful response includes each line’s name, unit price, quantity, and line total, plus subtotal, discount amount and description, final amount, and currency. Money fields are two-decimal strings so the JSON does not go through binary floating point.

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

- Java 21 / Spring Boot / Gradle so money can be `BigDecimal` with half-up rounding to two cents, and so the app runs with the Gradle wrapper.
- Duplicate codes in one request are merged rather than rejected.
- An empty `items` list is a valid zero bill, not an error.
- The UI updates the bill automatically after cart changes; the explicit button is still there for retries and for the required journey.
- No persistence, auth, tax, or payments — those are out of scope.

With more time: a printable receipt, a second discount (for example 25% off Latte at quantity ≥ 2), and a focused frontend test for the add-same-item path.

## AI Coding Disclosure

- **Model:** Cursor Grok 4.6 (SpaceXAI / Cursor)
- **Tools:** Cursor agent (implementation, tests, UI, README). No other agents.
- **Validation harness:**
  - `./gradlew test` — billing thresholds at 100 / 200 CNY, the 115 and 201 examples, unknown codes, invalid quantities, and API error shape
  - Manual desktop journey: open app → add Latte, 3× Ice Tea, 2× Pepsi → confirm CNY 103.50
  - Manual mobile journey: same flow at a narrow viewport, including quantity, remove, and the final-amount chip
  - Forced API failure: confirm the cart is kept and Retry works
- **Decisions after reviewing generated code:** keep billing logic in a Spring-light `BillingCalculator`; return money as strings; use HTTP 400 with one error envelope instead of mixed 400/422; serve the UI from the same Spring Boot process so local setup stays empty of extra infrastructure.

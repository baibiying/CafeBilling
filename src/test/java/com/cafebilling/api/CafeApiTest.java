package com.cafebilling.api;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HTTP-level checks for {@code GET /api/menu} and {@code POST /api/bills}. These complement {@code
 * BillingCalculatorTest}; they do not replace the discount unit tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CafeApiTest {

  @Autowired private MockMvc mockMvc;

  /** Menu prices are server-owned: 9 items, Masala 10.00, Latte 30.00, currency CNY. */
  @Test
  void getMenuReturnsAuthoritativePrices() throws Exception {
    mockMvc
        .perform(get("/api/menu"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currency").value("CNY"))
        .andExpect(jsonPath("$.items.length()").value(9))
        .andExpect(jsonPath("$.items[?(@.code=='TM')].name").value(hasItem("Tea — Masala")))
        .andExpect(jsonPath("$.items[?(@.code=='TM')].unitPrice").value(hasItem("10.00")))
        .andExpect(jsonPath("$.items[?(@.code=='CL')].unitPrice").value(hasItem("30.00")));
  }

  /**
   * The brief's example over HTTP: Latte + 3 Ice Tea + 2 Pepsi → subtotal 115.00, discount 11.50,
   * final 103.50. Amounts are JSON strings so they never go through binary floats.
   */
  @Test
  void createBillMatchesBriefExample() throws Exception {
    mockMvc
        .perform(
            post("/api/bills")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "items": [
                                    { "code": "CL", "quantity": 1 },
                                    { "code": "TI", "quantity": 3 },
                                    { "code": "CDP", "quantity": 2 }
                                  ]
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currency").value("CNY"))
        .andExpect(jsonPath("$.subtotal").value("115.00"))
        .andExpect(jsonPath("$.discount.amount").value("11.50"))
        .andExpect(jsonPath("$.finalAmount").value("103.50"))
        .andExpect(jsonPath("$.lines.length()").value(3));
  }

  /** Empty {@code items} is 200 with a zero bill, not an error (UI starts empty). */
  @Test
  void emptyItemsReturnsZeroBill() throws Exception {
    mockMvc
        .perform(
            post("/api/bills").contentType(MediaType.APPLICATION_JSON).content("{\"items\": []}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines.length()").value(0))
        .andExpect(jsonPath("$.subtotal").value("0.00"))
        .andExpect(jsonPath("$.discount.amount").value("0.00"))
        .andExpect(jsonPath("$.finalAmount").value("0.00"));
  }

  /** Unknown code → HTTP 400, envelope {@code VALIDATION_ERROR} / {@code UNKNOWN_ITEM}. */
  @Test
  void unknownItemReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/bills")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\": [{\"code\": \"ZZ\", \"quantity\": 1}]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.details[0].issue").value("UNKNOWN_ITEM"));
  }

  /** Quantity 0 → HTTP 400, {@code INVALID_QUANTITY}. */
  @Test
  void invalidQuantityReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/bills")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\": [{\"code\": \"CL\", \"quantity\": 0}]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.details[0].issue").value("INVALID_QUANTITY"));
  }

  /**
   * Missing quantity still uses the same error envelope (400 + {@code VALIDATION_ERROR} + details
   * array), not a different status or shape.
   */
  @Test
  void malformedBodyReturnsConsistentErrorShape() throws Exception {
    mockMvc
        .perform(
            post("/api/bills")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\": [{\"code\": \"CL\"}]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.details").isArray());
  }

  /** Bonus Latte promo over HTTP: qty 2 → list 60, off 15, pay 45. */
  @Test
  void twoLattesIncludeItemPromotion() throws Exception {
    mockMvc
        .perform(
            post("/api/bills")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\": [{\"code\": \"CL\", \"quantity\": 2}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subtotal").value("60.00"))
        .andExpect(jsonPath("$.discount.amount").value("15.00"))
        .andExpect(
            jsonPath("$.discount.description").value("25% off Latte when quantity is 2 or more"))
        .andExpect(jsonPath("$.finalAmount").value("45.00"));
  }

  /**
   * The browser cannot set prices. Extra {@code unitPrice: "1.00"} on the request is ignored; Latte
   * is still 30.00 from the server menu.
   */
  @Test
  void clientSuppliedPricesAreIgnored() throws Exception {
    mockMvc
        .perform(
            post("/api/bills")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"items": [{"code": "CL", "quantity": 1, "unitPrice": "1.00"}]}
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines[0].unitPrice").value("30.00"))
        .andExpect(jsonPath("$.lines[0].lineTotal").value("30.00"));
  }
}

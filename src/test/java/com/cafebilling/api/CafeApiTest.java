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

@SpringBootTest
@AutoConfigureMockMvc
class CafeApiTest {

  @Autowired private MockMvc mockMvc;

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

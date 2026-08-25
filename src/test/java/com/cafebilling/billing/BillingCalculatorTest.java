package com.cafebilling.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cafebilling.billing.BillingCalculator.RequestedItem;
import com.cafebilling.menu.MenuCatalog;
import com.cafebilling.money.Money;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for discount math. These call {@link BillingCalculator} directly — no HTTP, no UI —
 * which is what the brief requires for the 100 / 200 CNY rules.
 */
class BillingCalculatorTest {

  private final BillingCalculator billing = new BillingCalculator(new MenuCatalog());

  /**
   * Required: subtotal 0 and exactly 100 CNY get no discount. 100 is inclusive of the first band.
   */
  @Test
  void noDiscountAtOrBelow100() {
    assertEquals(Money.of(0), billing.calculateDiscount(Money.of(0)).amount());
    assertEquals(Money.of(0), billing.calculateDiscount(Money.of(100)).amount());
    assertEquals("No discount", billing.calculateDiscount(Money.of(100)).description());
  }

  /**
   * Required: above 100 and up to 200, 10% of the whole subtotal. 115 is the brief's example
   * (11.50). 100.01 is the first cent that qualifies; 200 is still this band, not the 20% band.
   */
  @Test
  void tenPercentBetween100And200() {
    Discount discount = billing.calculateDiscount(Money.of(115));
    assertEquals(Money.of("11.50"), discount.amount());
    assertEquals("10% of the entire subtotal", discount.description());

    assertEquals(Money.of("10.00"), billing.calculateDiscount(Money.of("100.01")).amount());
    assertEquals(Money.of("20.00"), billing.calculateDiscount(Money.of(200)).amount());
  }

  /**
   * Required: above 200, 10% of the first 200 plus 20% of the rest. 201 → 20.00 + 0.20 = 20.20
   * (brief). 250 → 20 + 10 = 30.
   */
  @Test
  void tieredDiscountAbove200() {
    Discount discount = billing.calculateDiscount(Money.of(201));
    assertEquals(Money.of("20.20"), discount.amount());
    assertTrue(discount.description().contains("20%"));
    assertEquals(Money.of("30.00"), billing.calculateDiscount(Money.of(250)).amount());
  }

  /**
   * Required demo order: 1 Latte (30) + 3 Ice Tea (45) + 2 Pepsi (40) = 115 → discount 11.50, pay
   * 103.50. Same numbers as the desktop walkthrough.
   */
  @Test
  void specExampleOrderIs115() {
    Bill bill =
        billing.calculateBill(
            List.of(
                new RequestedItem("CL", 1),
                new RequestedItem("TI", 3),
                new RequestedItem("CDP", 2)));
    assertEquals(Money.of("115.00"), bill.subtotal());
    assertEquals(Money.of("11.50"), bill.discount().amount());
    assertEquals(Money.of("103.50"), bill.finalAmount());
    assertEquals("CNY", bill.currency());
  }

  /** 5 Mocha (200) + 1 Masala (10) = 210, so the band above 200: 20 + 2 = 22 off, pay 188. */
  @Test
  void mochaAndMasalaProduceTieredDiscount() {
    Bill bill =
        billing.calculateBill(List.of(new RequestedItem("CM", 5), new RequestedItem("TM", 1)));
    assertEquals(Money.of("210.00"), bill.subtotal());
    assertEquals(Money.of("22.00"), bill.discount().amount());
    assertEquals(Money.of("188.00"), bill.finalAmount());
  }

  /** Bonus Latte promo does not fire at quantity 1. One Latte is just 30, no discount. */
  @Test
  void oneLatteDoesNotGetItemPromo() {
    Bill bill = billing.calculateBill(List.of(new RequestedItem("CL", 1)));
    assertEquals(Money.of("30.00"), bill.subtotal());
    assertEquals(Money.of(0), bill.discount().amount());
    assertEquals("No discount", bill.discount().description());
    assertEquals(Money.of("30.00"), bill.finalAmount());
  }

  /**
   * Bonus: 2 Lattes, list 60, 25% off = 15. Line total stays 60 (unit × qty); the 15 appears on the
   * discount line. Pay 45.
   */
  @Test
  void twoLattesGetTwentyFivePercentOff() {
    Bill bill = billing.calculateBill(List.of(new RequestedItem("CL", 2)));
    assertEquals(Money.of("60.00"), bill.subtotal());
    assertEquals(Money.of("15.00"), bill.discount().amount());
    assertEquals(BillingCalculator.LATTE_PROMO_DESCRIPTION, bill.discount().description());
    assertEquals(Money.of("45.00"), bill.finalAmount());
    assertEquals(Money.of("60.00"), line(bill, "CL").lineTotal());
  }

  /**
   * Bonus stacking: 2 Lattes (60) + 5 Mocha (200) = 260 list. Latte promo 15 first, remaining 245 →
   * 10% of 200 + 20% of 45 = 29. Total off 44, pay 216. Item promo before the 100/200 tiers so the
   * same 60 is not discounted twice.
   */
  @Test
  void lattePromoAppliesBeforeTieredDiscount() {
    Bill bill =
        billing.calculateBill(List.of(new RequestedItem("CL", 2), new RequestedItem("CM", 5)));
    assertEquals(Money.of("260.00"), bill.subtotal());
    assertEquals(Money.of("44.00"), bill.discount().amount());
    assertTrue(bill.discount().description().contains("25%"));
    assertTrue(bill.discount().description().contains("20%"));
    assertEquals(Money.of("216.00"), bill.finalAmount());
  }

  /** Empty {@code items} is a valid zero bill, not a 400. Matches the UI starting empty. */
  @Test
  void emptyBillIsZero() {
    Bill bill = billing.calculateBill(List.of());
    assertTrue(bill.lines().isEmpty());
    assertEquals(Money.of(0), bill.subtotal());
    assertEquals(Money.of(0), bill.discount().amount());
    assertEquals(Money.of(0), bill.finalAmount());
  }

  /**
   * Prices come from {@link MenuCatalog}, not the request. 1 Latte 30 + 3 Masala 30 = 60, under 100
   * so no discount.
   */
  @Test
  void lineTotalsUseServerPrices() {
    Bill bill =
        billing.calculateBill(List.of(new RequestedItem("CL", 1), new RequestedItem("TM", 3)));
    BillLine latte = line(bill, "CL");
    BillLine masala = line(bill, "TM");
    assertEquals(Money.of(30), latte.unitPrice());
    assertEquals(Money.of(30), latte.lineTotal());
    assertEquals(Money.of(10), masala.unitPrice());
    assertEquals(Money.of(30), masala.lineTotal());
    assertEquals(Money.of(60), bill.subtotal());
    assertEquals(Money.of(0), bill.discount().amount());
    assertEquals(Money.of(60), bill.finalAmount());
  }

  /**
   * Duplicate codes in one request are merged (1 + 2 Ice Tea → one line, qty 3, 45). Same idea as
   * tapping Add twice in the UI.
   */
  @Test
  void duplicateCodesAreMerged() {
    Bill bill =
        billing.calculateBill(List.of(new RequestedItem("TI", 1), new RequestedItem("TI", 2)));
    assertEquals(1, bill.lines().size());
    assertEquals(3, bill.lines().getFirst().quantity());
    assertEquals(Money.of(45), bill.lines().getFirst().lineTotal());
  }

  /** Required validation: unknown code is rejected with UNKNOWN_ITEM. */
  @Test
  void unknownItemCode() {
    BillingException ex =
        assertThrows(
            BillingException.class,
            () -> billing.calculateBill(List.of(new RequestedItem("XX", 1))));
    assertEquals("UNKNOWN_ITEM", ex.details().getFirst().issue());
    assertTrue(ex.details().getFirst().message().contains("XX"));
  }

  /** Required validation: quantity 0 and negative are INVALID_QUANTITY. */
  @Test
  void nonPositiveQuantity() {
    for (int quantity : List.of(0, -2)) {
      BillingException ex =
          assertThrows(
              BillingException.class,
              () -> billing.calculateBill(List.of(new RequestedItem("CL", quantity))));
      assertEquals("INVALID_QUANTITY", ex.details().getFirst().issue());
    }
  }

  /**
   * One request can report both UNKNOWN_ITEM and INVALID_QUANTITY instead of failing on the first.
   */
  @Test
  void collectsMultipleValidationErrors() {
    BillingException ex =
        assertThrows(
            BillingException.class,
            () ->
                billing.calculateBill(
                    List.of(new RequestedItem("NOPE", 1), new RequestedItem("CL", 0))));
    Set<String> issues = ex.details().stream().map(ErrorDetail::issue).collect(Collectors.toSet());
    assertEquals(Set.of("UNKNOWN_ITEM", "INVALID_QUANTITY"), issues);
  }

  /**
   * Money is {@link BigDecimal}, not float. 115 × 10% is exactly 11.50; a {@code double} could
   * drift (e.g. 11.499999).
   */
  @Test
  void discountUsesBigDecimalNotFloat() {
    Discount discount = billing.calculateDiscount(new BigDecimal("115.00"));
    assertInstanceOf(BigDecimal.class, discount.amount());
    assertEquals(new BigDecimal("11.50"), discount.amount());
  }

  private static BillLine line(Bill bill, String code) {
    return bill.lines().stream().filter(item -> item.code().equals(code)).findFirst().orElseThrow();
  }
}

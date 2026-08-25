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

class BillingCalculatorTest {

  private final BillingCalculator billing = new BillingCalculator(new MenuCatalog());

  @Test
  void noDiscountAtOrBelow100() {
    assertEquals(Money.of(0), billing.calculateDiscount(Money.of(0)).amount());
    assertEquals(Money.of(0), billing.calculateDiscount(Money.of(100)).amount());
    assertEquals("No discount", billing.calculateDiscount(Money.of(100)).description());
  }

  @Test
  void tenPercentBetween100And200() {
    Discount discount = billing.calculateDiscount(Money.of(115));
    assertEquals(Money.of("11.50"), discount.amount());
    assertEquals("10% of the entire subtotal", discount.description());

    assertEquals(Money.of("10.00"), billing.calculateDiscount(Money.of("100.01")).amount());
    assertEquals(Money.of("20.00"), billing.calculateDiscount(Money.of(200)).amount());
  }

  @Test
  void tieredDiscountAbove200() {
    Discount discount = billing.calculateDiscount(Money.of(201));
    assertEquals(Money.of("20.20"), discount.amount());
    assertTrue(discount.description().contains("20%"));
    assertEquals(Money.of("30.00"), billing.calculateDiscount(Money.of(250)).amount());
  }

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

  @Test
  void mochaAndMasalaProduceTieredDiscount() {
    Bill bill =
        billing.calculateBill(List.of(new RequestedItem("CM", 5), new RequestedItem("TM", 1)));
    assertEquals(Money.of("210.00"), bill.subtotal());
    assertEquals(Money.of("22.00"), bill.discount().amount());
    assertEquals(Money.of("188.00"), bill.finalAmount());
  }

  @Test
  void oneLatteDoesNotGetItemPromo() {
    Bill bill = billing.calculateBill(List.of(new RequestedItem("CL", 1)));
    assertEquals(Money.of("30.00"), bill.subtotal());
    assertEquals(Money.of(0), bill.discount().amount());
    assertEquals("No discount", bill.discount().description());
    assertEquals(Money.of("30.00"), bill.finalAmount());
  }

  @Test
  void twoLattesGetTwentyFivePercentOff() {
    Bill bill = billing.calculateBill(List.of(new RequestedItem("CL", 2)));
    assertEquals(Money.of("60.00"), bill.subtotal());
    assertEquals(Money.of("15.00"), bill.discount().amount());
    assertEquals(BillingCalculator.LATTE_PROMO_DESCRIPTION, bill.discount().description());
    assertEquals(Money.of("45.00"), bill.finalAmount());
    assertEquals(Money.of("60.00"), line(bill, "CL").lineTotal());
  }

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

  @Test
  void emptyBillIsZero() {
    Bill bill = billing.calculateBill(List.of());
    assertTrue(bill.lines().isEmpty());
    assertEquals(Money.of(0), bill.subtotal());
    assertEquals(Money.of(0), bill.discount().amount());
    assertEquals(Money.of(0), bill.finalAmount());
  }

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

  @Test
  void duplicateCodesAreMerged() {
    Bill bill =
        billing.calculateBill(List.of(new RequestedItem("TI", 1), new RequestedItem("TI", 2)));
    assertEquals(1, bill.lines().size());
    assertEquals(3, bill.lines().getFirst().quantity());
    assertEquals(Money.of(45), bill.lines().getFirst().lineTotal());
  }

  @Test
  void unknownItemCode() {
    BillingException ex =
        assertThrows(
            BillingException.class,
            () -> billing.calculateBill(List.of(new RequestedItem("XX", 1))));
    assertEquals("UNKNOWN_ITEM", ex.details().getFirst().issue());
    assertTrue(ex.details().getFirst().message().contains("XX"));
  }

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

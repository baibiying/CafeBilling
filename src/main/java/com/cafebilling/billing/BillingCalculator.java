package com.cafebilling.billing;

import com.cafebilling.menu.MenuCatalog;
import com.cafebilling.menu.MenuItem;
import com.cafebilling.money.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class BillingCalculator {

    static final BigDecimal NO_DISCOUNT_LIMIT = Money.of(100);
    static final BigDecimal TIER_LIMIT = Money.of(200);
    private static final BigDecimal MID_RATE = new BigDecimal("0.10");
    private static final BigDecimal HIGH_RATE = new BigDecimal("0.20");

    private final MenuCatalog menu;

    public BillingCalculator(MenuCatalog menu) {
        this.menu = menu;
    }

    public Discount calculateDiscount(BigDecimal subtotal) {
        subtotal = Money.of(subtotal);
        if (subtotal.compareTo(NO_DISCOUNT_LIMIT) <= 0) {
            return new Discount(Money.of(0), "No discount");
        }
        if (subtotal.compareTo(TIER_LIMIT) <= 0) {
            return new Discount(
                    Money.of(subtotal.multiply(MID_RATE)), "10% of the entire subtotal");
        }
        BigDecimal remainder = subtotal.subtract(TIER_LIMIT);
        BigDecimal amount =
                Money.of(TIER_LIMIT.multiply(MID_RATE)).add(Money.of(remainder.multiply(HIGH_RATE)));
        return new Discount(
                amount, "10% of the first 200 CNY, plus 20% of the portion above 200 CNY");
    }

    public Bill calculateBill(List<RequestedItem> items) {
        List<ErrorDetail> details = new ArrayList<>();
        Map<String, Integer> merged = new LinkedHashMap<>();

        for (int index = 0; index < items.size(); index++) {
            RequestedItem item = items.get(index);
            String field = "items[" + index + "]";
            String code = item.code() == null ? "" : item.code().trim();
            if (menu.findByCode(code).isEmpty()) {
                details.add(new ErrorDetail(
                        field + ".code", "UNKNOWN_ITEM", "Unknown item code '" + code + "'."));
                continue;
            }
            Integer quantity = item.quantity();
            if (quantity == null || quantity < 1) {
                details.add(new ErrorDetail(
                        field + ".quantity",
                        "INVALID_QUANTITY",
                        "Quantity must be a positive integer."));
                continue;
            }
            merged.merge(code, quantity, Integer::sum);
        }

        if (!details.isEmpty()) {
            throw new BillingException("The bill request is invalid.", details);
        }

        List<BillLine> lines = new ArrayList<>();
        for (MenuItem menuItem : menu.items()) {
            Integer quantity = merged.get(menuItem.code());
            if (quantity == null) {
                continue;
            }
            BigDecimal lineTotal = Money.of(menuItem.unitPrice().multiply(BigDecimal.valueOf(quantity)));
            lines.add(new BillLine(
                    menuItem.code(), menuItem.name(), menuItem.unitPrice(), quantity, lineTotal));
        }

        BigDecimal subtotal = lines.stream()
                .map(BillLine::lineTotal)
                .reduce(Money.of(0), BigDecimal::add);
        subtotal = Money.of(subtotal);
        Discount discount = calculateDiscount(subtotal);
        BigDecimal finalAmount = Money.of(subtotal.subtract(discount.amount()));
        return new Bill(List.copyOf(lines), subtotal, discount, finalAmount, Money.CURRENCY);
    }

    public record RequestedItem(String code, Integer quantity) {}
}

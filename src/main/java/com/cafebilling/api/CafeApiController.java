package com.cafebilling.api;

import com.cafebilling.api.dto.BillLineResponse;
import com.cafebilling.api.dto.BillRequest;
import com.cafebilling.api.dto.BillResponse;
import com.cafebilling.api.dto.DiscountResponse;
import com.cafebilling.api.dto.MenuItemResponse;
import com.cafebilling.api.dto.MenuResponse;
import com.cafebilling.billing.Bill;
import com.cafebilling.billing.BillingCalculator;
import com.cafebilling.billing.BillingCalculator.RequestedItem;
import com.cafebilling.menu.MenuCatalog;
import com.cafebilling.money.Money;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CafeApiController {

  private final MenuCatalog menu;
  private final BillingCalculator billing;

  public CafeApiController(MenuCatalog menu, BillingCalculator billing) {
    this.menu = menu;
    this.billing = billing;
  }

  @GetMapping("/menu")
  public MenuResponse menu() {
    return new MenuResponse(
        Money.CURRENCY,
        menu.items().stream()
            .map(
                item ->
                    new MenuItemResponse(
                        item.code(), item.name(), item.category(), Money.format(item.unitPrice())))
            .toList());
  }

  @PostMapping("/bills")
  public BillResponse createBill(@Valid @RequestBody BillRequest request) {
    List<RequestedItem> items =
        request.items().stream()
            .map(item -> new RequestedItem(item.code(), item.quantity()))
            .toList();
    return toResponse(billing.calculateBill(items));
  }

  private static BillResponse toResponse(Bill bill) {
    return new BillResponse(
        bill.currency(),
        bill.lines().stream()
            .map(
                line ->
                    new BillLineResponse(
                        line.code(),
                        line.name(),
                        Money.format(line.unitPrice()),
                        line.quantity(),
                        Money.format(line.lineTotal())))
            .toList(),
        Money.format(bill.subtotal()),
        new DiscountResponse(Money.format(bill.discount().amount()), bill.discount().description()),
        Money.format(bill.finalAmount()));
  }
}

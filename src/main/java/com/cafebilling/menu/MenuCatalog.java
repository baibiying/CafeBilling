package com.cafebilling.menu;

import static com.cafebilling.money.Money.of;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class MenuCatalog {

    private static final List<MenuItem> ITEMS = List.of(
            new MenuItem("TM", "Tea — Masala", "Tea", of(10)),
            new MenuItem("TI", "Tea — Ice", "Tea", of(15)),
            new MenuItem("TL", "Tea — Lemon", "Tea", of(15)),
            new MenuItem("CC", "Coffee — Cold", "Coffee", of(15)),
            new MenuItem("CL", "Coffee — Latte", "Coffee", of(30)),
            new MenuItem("CM", "Coffee — Mocha", "Coffee", of(40)),
            new MenuItem("CDC", "Cold Drink — Coke", "Cold Drink", of(20)),
            new MenuItem("CDP", "Cold Drink — Pepsi", "Cold Drink", of(20)),
            new MenuItem("CDS", "Cold Drink — Sprite", "Cold Drink", of(15)));

    private static final Map<String, MenuItem> BY_CODE =
            ITEMS.stream().collect(Collectors.toUnmodifiableMap(MenuItem::code, Function.identity()));

    public List<MenuItem> items() {
        return ITEMS;
    }

    public Optional<MenuItem> findByCode(String code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }
}

const menuList = document.querySelector("#menu-list");
const menuStatus = document.querySelector("#menu-status");
const billLines = document.querySelector("#bill-lines");
const subtotalEl = document.querySelector("#subtotal");
const discountEl = document.querySelector("#discount");
const discountNoteEl = document.querySelector("#discount-note");
const finalAmountEl = document.querySelector("#final-amount");
const mobileFinalEl = document.querySelector("#mobile-final");
const errorBanner = document.querySelector("#error-banner");
const billStatus = document.querySelector("#bill-status");

const state = {
  menu: [],
  cart: new Map(),
  bill: null,
  loading: false,
  error: null,
};

let billRequestId = 0;

function moneyLabel(amount) {
  return `CNY ${amount}`;
}

function cartItems() {
  return [...state.cart.entries()].map(([code, quantity]) => ({ code, quantity }));
}

function showError(message) {
  state.error = message;
  errorBanner.hidden = false;
  errorBanner.replaceChildren();
  const text = document.createElement("span");
  text.textContent = message;
  const retry = document.createElement("button");
  retry.type = "button";
  retry.className = "retry";
  retry.textContent = "Retry";
  retry.addEventListener("click", () => updateBill());
  errorBanner.append(text, retry);
}

function clearError() {
  state.error = null;
  errorBanner.hidden = true;
  errorBanner.replaceChildren();
}

async function fetchJson(url, options) {
  const response = await fetch(url, {
    headers: { "Content-Type": "application/json", ...(options?.headers ?? {}) },
    ...options,
  });
  let body = null;
  try {
    body = await response.json();
  } catch {
    body = null;
  }
  if (!response.ok) {
    const details = body?.error?.details?.map((item) => item.message).join(" ");
    throw new Error(details || body?.error?.message || "Request failed. Please try again.");
  }
  return body;
}

function addItem(code) {
  state.cart.set(code, (state.cart.get(code) ?? 0) + 1);
  render();
  updateBill();
}

function setQuantity(code, quantity) {
  if (quantity < 1) {
    state.cart.delete(code);
  } else {
    state.cart.set(code, quantity);
  }
  render();
  updateBill();
}

function removeItem(code) {
  state.cart.delete(code);
  render();
  updateBill();
}

function renderMenu() {
  menuList.replaceChildren();
  const groups = new Map();
  for (const item of state.menu) {
    if (!groups.has(item.category)) {
      groups.set(item.category, []);
    }
    groups.get(item.category).push(item);
  }

  for (const [category, items] of groups) {
    const group = document.createElement("section");
    group.className = "menu-group";
    group.setAttribute("aria-label", category);
    const heading = document.createElement("h3");
    heading.textContent = category;
    group.append(heading);

    for (const item of items) {
      const quantity = state.cart.get(item.code) ?? 0;
      const row = document.createElement("article");
      row.className = quantity > 0 ? "menu-item selected" : "menu-item";

      const copy = document.createElement("div");
      const name = document.createElement("p");
      name.className = "item-name";
      name.textContent = item.name;
      const meta = document.createElement("p");
      meta.className = "item-meta";
      const price = document.createElement("span");
      price.className = "price";
      price.textContent = moneyLabel(item.unitPrice);
      meta.append(price, ` · ${item.code}`);
      copy.append(name, meta);

      const add = document.createElement("button");
      add.type = "button";
      add.className = "ghost";
      add.textContent = quantity > 0 ? `Add another (${quantity})` : "Add";
      add.setAttribute("aria-label", `Add ${item.name}`);
      add.addEventListener("click", () => addItem(item.code));

      row.append(copy, add);
      group.append(row);
    }
    menuList.append(group);
  }
}

function quantityControls(code, name, quantity) {
  const wrap = document.createElement("div");
  wrap.className = "qty";

  const decrease = document.createElement("button");
  decrease.type = "button";
  decrease.className = "icon-btn";
  decrease.setAttribute("aria-label", `Decrease ${name}`);
  decrease.textContent = "−";
  decrease.addEventListener("click", () => setQuantity(code, quantity - 1));

  const value = document.createElement("span");
  value.className = "qty-value";
  value.textContent = String(quantity);

  const increase = document.createElement("button");
  increase.type = "button";
  increase.className = "icon-btn";
  increase.setAttribute("aria-label", `Increase ${name}`);
  increase.textContent = "+";
  increase.addEventListener("click", () => setQuantity(code, quantity + 1));

  wrap.append(decrease, value, increase);
  return wrap;
}

function renderBill() {
  billLines.replaceChildren();
  const bill = state.bill;

  if (!bill || bill.lines.length === 0) {
    const empty = document.createElement("p");
    empty.className = "empty-bill";
    empty.textContent = "No items yet. Add drinks from the menu to start a bill.";
    billLines.append(empty);
    subtotalEl.textContent = moneyLabel("0.00");
    discountEl.textContent = moneyLabel("0.00");
    discountNoteEl.textContent = "";
    finalAmountEl.textContent = moneyLabel("0.00");
    mobileFinalEl.textContent = moneyLabel("0.00");
    return;
  }

  for (const line of bill.lines) {
    const row = document.createElement("article");
    row.className = "bill-line";

    const copy = document.createElement("div");
    const head = document.createElement("div");
    head.className = "bill-line-head";
    const name = document.createElement("p");
    name.className = "bill-line-name";
    name.textContent = line.name;
    head.append(name, quantityControls(line.code, line.name, line.quantity));
    const meta = document.createElement("p");
    meta.className = "bill-line-meta";
    meta.textContent = `${moneyLabel(line.unitPrice)} × ${line.quantity}`;
    copy.append(head, meta);

    const total = document.createElement("div");
    total.className = "line-total";
    total.textContent = moneyLabel(line.lineTotal);

    const controls = document.createElement("div");
    controls.className = "line-controls";
    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "remove";
    remove.textContent = "Remove";
    remove.setAttribute("aria-label", `Remove ${line.name}`);
    remove.addEventListener("click", () => removeItem(line.code));
    controls.append(remove);

    row.append(copy, total, controls);
    billLines.append(row);
  }

  subtotalEl.textContent = moneyLabel(bill.subtotal);
  discountEl.textContent = moneyLabel(bill.discount.amount);
  discountNoteEl.textContent = bill.discount.description ? `· ${bill.discount.description}` : "";
  finalAmountEl.textContent = moneyLabel(bill.finalAmount);
  mobileFinalEl.textContent = moneyLabel(bill.finalAmount);
}

function render() {
  renderMenu();
  renderBill();
  billStatus.textContent = state.loading ? "Calculating…" : "";
}

async function loadMenu() {
  menuStatus.textContent = "Loading menu…";
  try {
    const body = await fetchJson("/api/menu");
    state.menu = body.items;
    menuStatus.textContent = "";
    render();
  } catch (error) {
    menuStatus.textContent = error.message;
  }
}

async function updateBill() {
  const requestId = ++billRequestId;
  state.loading = true;
  clearError();
  render();
  try {
    const body = await fetchJson("/api/bills", {
      method: "POST",
      body: JSON.stringify({ items: cartItems() }),
    });
    if (requestId !== billRequestId) {
      return;
    }
    state.bill = body;
  } catch (error) {
    if (requestId !== billRequestId) {
      return;
    }
    showError(error.message);
  } finally {
    if (requestId === billRequestId) {
      state.loading = false;
      render();
    }
  }
}

loadMenu();

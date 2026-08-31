export const DEFAULT_BANK_TRANSFER_DISCOUNT_RATE = 0.1;
const NATIONAL_TAX_FACTOR = 1.105;

export interface PaymentPrice {
  subtotal: number;
  discount: number;
  total: number;
}

export function bankTransferPrice(subtotal: number, discountRate = DEFAULT_BANK_TRANSFER_DISCOUNT_RATE): PaymentPrice {
  const safeSubtotal = roundMoney(Math.max(0, subtotal));
  const subtotalCents = Math.round(safeSubtotal * 100);
  const rateBasisPoints = Math.round(Math.min(1, Math.max(0, discountRate)) * 10_000);
  const discount = discountCents(subtotalCents, rateBasisPoints) / 100;
  return { subtotal: safeSubtotal, discount, total: roundMoney(safeSubtotal - discount) };
}

export function listPriceForTransferMinimum(price: number, discountRate = DEFAULT_BANK_TRANSFER_DISCOUNT_RATE): number {
  return inverseTransferPrice(price, discountRate, true);
}

export function listPriceForTransferMaximum(price: number, discountRate = DEFAULT_BANK_TRANSFER_DISCOUNT_RATE): number {
  return inverseTransferPrice(price, discountRate, false);
}

export function listPrice(subtotal: number): PaymentPrice {
  const safeSubtotal = roundMoney(Math.max(0, subtotal));
  return { subtotal: safeSubtotal, discount: 0, total: safeSubtotal };
}

export function priceWithoutNationalTax(price: number): number {
  return roundMoney(Math.max(0, price) / NATIONAL_TAX_FACTOR);
}

export function roundMoney(value: number): number {
  const correction = Number.EPSILON * Math.max(1, Math.abs(value));
  return Math.round((value + Math.sign(value) * correction) * 100) / 100;
}

function inverseTransferPrice(price: number, discountRate: number, minimum: boolean): number {
  const targetCents = Math.round(roundMoney(Math.max(0, price)) * 100);
  const rateBasisPoints = Math.min(9_999, Math.round(Math.min(1, Math.max(0, discountRate)) * 10_000));
  let low = 0;
  let high = Math.ceil(targetCents * 10_000 / (10_000 - rateBasisPoints)) + 1;

  while (low < high) {
    const middle = Math.floor((low + high) / 2);
    const totalCents = middle - discountCents(middle, rateBasisPoints);
    if (totalCents >= targetCents + (minimum ? 0 : 1)) high = middle;
    else low = middle + 1;
  }

  return Math.max(0, low - (minimum ? 0 : 1)) / 100;
}

function discountCents(subtotalCents: number, rateBasisPoints: number): number {
  return Math.round(subtotalCents * rateBasisPoints / 10_000);
}

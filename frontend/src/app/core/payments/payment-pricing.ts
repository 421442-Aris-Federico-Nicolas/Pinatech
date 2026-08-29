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
  const discount = Math.round(subtotalCents * rateBasisPoints / 10_000) / 100;
  return { subtotal: safeSubtotal, discount, total: roundMoney(safeSubtotal - discount) };
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

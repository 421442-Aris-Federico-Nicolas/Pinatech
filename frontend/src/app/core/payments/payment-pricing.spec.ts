import { bankTransferPrice, listPriceForTransferMaximum, listPriceForTransferMinimum, priceWithoutNationalTax } from './payment-pricing';

describe('payment pricing', () => {
  it('applies the transfer discount with decimal HALF_UP rounding', () => {
    expect(bankTransferPrice(40.15, 0.1)).toEqual({ subtotal: 40.15, discount: 4.02, total: 36.13 });
  });

  it('uses the same monetary rounding for tax-exclusive prices', () => {
    expect(priceWithoutNationalTax(100)).toBe(90.5);
    expect(priceWithoutNationalTax(90)).toBe(81.45);
  });

  it('converts inclusive displayed-price bounds to exact list-price bounds in cents', () => {
    expect(bankTransferPrice(40.13).total).toBe(36.12);
    expect(bankTransferPrice(40.14).total).toBe(36.13);
    expect(bankTransferPrice(40.15).total).toBe(36.13);
    expect(bankTransferPrice(40.16).total).toBe(36.14);
    expect(listPriceForTransferMinimum(36.13)).toBe(40.14);
    expect(listPriceForTransferMaximum(36.13)).toBe(40.15);
  });
});

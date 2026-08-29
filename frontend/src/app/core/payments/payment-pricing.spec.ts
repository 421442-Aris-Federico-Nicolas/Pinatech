import { bankTransferPrice, priceWithoutNationalTax } from './payment-pricing';

describe('payment pricing', () => {
  it('applies the transfer discount with decimal HALF_UP rounding', () => {
    expect(bankTransferPrice(40.15, 0.1)).toEqual({ subtotal: 40.15, discount: 4.02, total: 36.13 });
  });

  it('uses the same monetary rounding for tax-exclusive prices', () => {
    expect(priceWithoutNationalTax(100)).toBe(90.5);
    expect(priceWithoutNationalTax(90)).toBe(81.45);
  });
});

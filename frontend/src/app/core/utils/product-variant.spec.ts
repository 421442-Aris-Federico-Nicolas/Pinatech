import { hasVisibleColorVariants, isDefaultProductVariantName } from './product-variant';

describe('product variant utilities', () => {
  it('recognizes current and historical default variant names', () => {
    expect(isDefaultProductVariantName('Único')).toBe(true);
    expect(isDefaultProductVariantName(' Unico ')).toBe(true);
    expect(isDefaultProductVariantName('Negro')).toBe(false);
  });

  it('only hides color for a single default variant', () => {
    expect(hasVisibleColorVariants([{ colorName: 'Único' }])).toBe(false);
    expect(hasVisibleColorVariants([{ colorName: 'Negro' }])).toBe(true);
    expect(hasVisibleColorVariants([{ colorName: 'Único' }, { colorName: 'Negro' }])).toBe(true);
  });
});

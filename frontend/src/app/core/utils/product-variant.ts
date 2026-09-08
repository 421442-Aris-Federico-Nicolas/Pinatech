export const DEFAULT_PRODUCT_VARIANT_NAME = 'Único';

export function isDefaultProductVariantName(name: string): boolean {
  return name.trim().normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase() === 'unico';
}

export function hasVisibleColorVariants(variants: readonly { colorName: string }[]): boolean {
  return variants.length > 1 || (variants.length === 1 && !isDefaultProductVariantName(variants[0].colorName));
}

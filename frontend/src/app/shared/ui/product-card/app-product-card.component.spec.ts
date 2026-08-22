import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AppProductCardComponent, ProductCardProduct, ProductCardVariant } from './app-product-card.component';

describe('AppProductCardComponent', () => {
  const product: ProductCardProduct = {
    id: 1,
    name: 'Mouse Pro',
    description: 'Mouse profesional',
    price: 100,
    categoryName: 'Periféricos',
    brandName: 'Pinatech',
    images: [],
    variants: [
      { id: 11, colorName: 'Blanco', colorHex: '#ffffff', inStock: false, availableQuantity: 0 },
      { id: 12, colorName: 'Negro', colorHex: '#000000', inStock: true, availableQuantity: 8 },
    ],
  };

  it('selects the first available variant and emits it when adding', async () => {
    await TestBed.configureTestingModule({
      imports: [AppProductCardComponent],
      providers: [provideRouter([])],
    }).compileComponents();
    const fixture = TestBed.createComponent(AppProductCardComponent);
    const added: ProductCardVariant[] = [];
    fixture.componentInstance.addToCart.subscribe((variant) => added.push(variant));
    fixture.componentRef.setInput('product', product);
    fixture.detectChanges();

    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(select.value).toBe('1');
    expect(button.disabled).toBe(false);

    button.click();
    expect(added).toEqual([product.variants[1]]);
  });
});

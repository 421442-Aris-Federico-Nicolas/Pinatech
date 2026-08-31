import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AppProductCardComponent, ProductCardProduct } from './app-product-card.component';

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

  it('keeps the catalog card compact and links to the product detail', async () => {
    await TestBed.configureTestingModule({
      imports: [AppProductCardComponent],
      providers: [provideRouter([])],
    }).compileComponents();
    const fixture = TestBed.createComponent(AppProductCardComponent);
    fixture.componentRef.setInput('product', product);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('select')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Mouse profesional');
    expect(fixture.nativeElement.textContent).not.toContain('Sin impuestos');
    expect(fixture.nativeElement.textContent).not.toContain('Transferencia');
    expect(fixture.nativeElement.textContent).not.toContain('10% menos');
    expect(fixture.nativeElement.textContent).not.toContain('Lista / Mercado Pago');
    expect(fixture.nativeElement.textContent).toContain('Disponible');
    expect(fixture.nativeElement.textContent).toContain('$100.00');
    expect(fixture.nativeElement.querySelector('.product-card__actions a').getAttribute('href')).toBe('/products/1');
  });
});

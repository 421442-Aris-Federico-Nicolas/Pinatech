import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { EMPTY, of } from 'rxjs';
import { CartService } from '../../core/cart/cart.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { CatalogService, Product } from '../catalog/catalog.service';
import { ProductComponent } from './product.component';

describe('ProductComponent', () => {
  const product: Product = {
    id: 1,
    name: 'Mouse Pro',
    slug: 'mouse-pro',
    description: 'Mouse profesional',
    price: 100,
    categoryId: 2,
    categoryName: 'Periféricos',
    brandId: 3,
    brandName: 'Pinatech',
    images: [],
    specifications: [],
    variants: [
      { id: 11, colorName: 'Negro', colorHex: '#000000', inStock: true },
      { id: 12, colorName: 'Blanco', colorHex: '#ffffff', inStock: true },
    ],
  };

  it('moves selection and focus with arrow keys in the color radiogroup', async () => {
    const navigate = vi.fn(() => Promise.resolve(true));
    await TestBed.configureTestingModule({
      imports: [ProductComponent],
      providers: [
        { provide: CatalogService, useValue: { product: () => of(product) } },
        { provide: CartService, useValue: { add: vi.fn() } },
        { provide: Router, useValue: { navigate } },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap({ id: 1 }), queryParamMap: convertToParamMap({}) },
            queryParamMap: of(convertToParamMap({})),
          },
        },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ProductComponent);
    fixture.detectChanges();
    const first = fixture.nativeElement.querySelector('[data-variant-id="11"]') as HTMLButtonElement;
    first.focus();
    first.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    fixture.detectChanges();
    await Promise.resolve();

    const second = fixture.nativeElement.querySelector('[data-variant-id="12"]') as HTMLButtonElement;
    expect(fixture.componentInstance.selectedVariantId()).toBe(12);
    expect(second.getAttribute('aria-checked')).toBe('true');
    expect(document.activeElement).toBe(second);
    expect(navigate).toHaveBeenCalled();
  });

  it('warns with the actual quantity when the cart cap is reached', async () => {
    const warning = vi.fn(() => ({ onAction: () => EMPTY }));
    const add = vi.fn(() => ({ requested: 5, added: 1, quantity: 99, capped: true }));
    await TestBed.configureTestingModule({
      imports: [ProductComponent],
      providers: [
        { provide: CatalogService, useValue: { product: () => of(product) } },
        { provide: CartService, useValue: { add } },
        { provide: NotificationService, useValue: { warning, success: vi.fn() } },
        { provide: Router, useValue: { navigate: vi.fn(), navigateByUrl: vi.fn() } },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap({ id: 1 }), queryParamMap: convertToParamMap({}) },
            queryParamMap: of(convertToParamMap({})),
          },
        },
      ],
    }).compileComponents();
    const component = TestBed.createComponent(ProductComponent).componentInstance;
    component.quantity.set(5);

    component.addToCart();

    expect(add).toHaveBeenCalledWith(product, product.variants[0], 5);
    expect(warning).toHaveBeenCalledWith('Se agregó 1 unidad; el máximo por color es 99.', 'Ver carrito');
  });
});

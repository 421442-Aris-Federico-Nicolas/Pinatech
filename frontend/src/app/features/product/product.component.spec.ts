import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { EMPTY, of } from 'rxjs';
import { CartService } from '../../core/cart/cart.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { CatalogService, Product } from '../catalog/catalog.service';
import { CheckoutService } from '../checkout/checkout.service';
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
      { id: 11, colorName: 'Negro', colorHex: '#000000', inStock: true, availableQuantity: 5 },
      { id: 12, colorName: 'Blanco', colorHex: '#ffffff', inStock: true, availableQuantity: 4 },
    ],
  };

  it('moves selection and focus with arrow keys in the color radiogroup', async () => {
    const navigate = vi.fn(() => Promise.resolve(true));
    await TestBed.configureTestingModule({
      imports: [ProductComponent],
      providers: [
        { provide: CatalogService, useValue: { product: () => of(product) } },
        { provide: CartService, useValue: { add: vi.fn(), items: signal([]), stockLimit: (variant: Product['variants'][number]) => variant.availableQuantity } },
        { provide: CheckoutService, useValue: { capabilities: () => of({ fulfillmentMethods: ['PICKUP'], pickupLocations: [{ name: 'Pinatech Centro', locality: 'Córdoba' }] }) } },
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
    expect(fixture.nativeElement.textContent).toContain('Precio especial por transferencia');
    expect(fixture.nativeElement.textContent).toContain('10% menos');
    expect(fixture.nativeElement.textContent).toContain('Precio de lista / Mercado Pago');
    expect(fixture.nativeElement.textContent).toContain('Retiro sin costo');
    expect(fixture.nativeElement.textContent).not.toContain('recargo');
    expect(fixture.componentInstance.transferPricing().total).toBe(90);
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

  it('falls back to an available color when a deep-linked variant is out of stock', async () => {
    const stockProduct = {
      ...product,
      variants: [
        { ...product.variants[0], inStock: false, availableQuantity: 0 },
        product.variants[1],
      ],
    };
    await TestBed.configureTestingModule({
      imports: [ProductComponent],
      providers: [
        { provide: CatalogService, useValue: { product: () => of(stockProduct) } },
        { provide: CartService, useValue: { add: vi.fn(), items: signal([]), stockLimit: (variant: Product['variants'][number]) => variant.availableQuantity } },
        { provide: CheckoutService, useValue: { capabilities: () => of({ fulfillmentMethods: [], pickupLocations: [] }) } },
        { provide: Router, useValue: { navigate: vi.fn() } },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap({ id: 1 }), queryParamMap: convertToParamMap({ variant: 11 }) },
            queryParamMap: of(convertToParamMap({ variant: 11 })),
          },
        },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ProductComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedVariantId()).toBe(12);
    expect(fixture.nativeElement.querySelector('[data-variant-id="12"]').tabIndex).toBe(0);
  });

  it('shows the image associated with a color without changing color during manual gallery navigation', async () => {
    const productWithImages: Product = {
      ...product,
      images: [
        { id: 101, contentUrl: '/images/front.jpg', altText: 'Vista frontal', displayOrder: 0 },
        { id: 102, contentUrl: '/images/black.jpg', altText: 'Color negro', displayOrder: 1 },
      ],
      variants: [
        { ...product.variants[0], imageId: 102 },
        { ...product.variants[1], imageId: null },
      ],
    };
    await TestBed.configureTestingModule({
      imports: [ProductComponent],
      providers: [
        { provide: CatalogService, useValue: { product: () => of(productWithImages) } },
        { provide: CartService, useValue: { add: vi.fn(), items: signal([]), stockLimit: (variant: Product['variants'][number]) => variant.availableQuantity } },
        { provide: CheckoutService, useValue: { capabilities: () => of({ fulfillmentMethods: [], pickupLocations: [] }) } },
        { provide: Router, useValue: { navigate: vi.fn(() => Promise.resolve(true)) } },
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
    expect(component.selectedVariantId()).toBe(11);
    expect(component.currentImage()?.id).toBe(102);

    component.selectImage(0);
    expect(component.selectedVariantId()).toBe(11);
    component.selectVariant(12);
    expect(component.currentImage()?.id).toBe(101);
  });

  it('warns with the actual quantity when the cart cap is reached', async () => {
    const warning = vi.fn(() => ({ onAction: () => EMPTY }));
    const add = vi.fn(() => ({ requested: 5, added: 1, quantity: 5, limit: 5, capped: true }));
    await TestBed.configureTestingModule({
      imports: [ProductComponent],
      providers: [
        { provide: CatalogService, useValue: { product: () => of(product) } },
        { provide: CartService, useValue: { add, items: signal([]), stockLimit: (variant: Product['variants'][number]) => variant.availableQuantity } },
        { provide: CheckoutService, useValue: { capabilities: () => of({ fulfillmentMethods: [], pickupLocations: [] }) } },
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
    expect(warning).toHaveBeenCalledWith('Se agregó 1 unidad; solo hay 5 disponibles para este color.', 'Ver carrito');
  });
});

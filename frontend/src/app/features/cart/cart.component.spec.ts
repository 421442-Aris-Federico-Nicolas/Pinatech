import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { CartItem, CartService } from '../../core/cart/cart.service';
import { CartComponent } from './cart.component';

describe('CartComponent', () => {
  const item: CartItem = {
    product: { id: 1, name: 'Teclado', slug: 'teclado', description: 'Mecánico', price: 1500, categoryId: 2, categoryName: 'Periféricos', brandId: 3, brandName: 'Marca', images: [], specifications: [], variants: [{ id: 11, colorName: 'Negro', colorHex: '#000000', inStock: true }] },
    variant: { id: 11, colorName: 'Negro', colorHex: '#000000', inStock: true },
    quantity: 2,
  };

  it('offers authenticated customers a link to checkout without creating an order', async () => {
    const cart = {
      items: signal([item]),
      count: signal(2),
      total: signal(3000),
      setQuantity: vi.fn(),
      add: vi.fn(),
      removeItem: vi.fn(),
      clear: vi.fn(),
      checkout: vi.fn(),
      reconcile: vi.fn(() => of(true)),
      legacyCartDiscarded: signal(false),
      dismissLegacyCartWarning: vi.fn(),
      notice: signal(''),
      dismissNotice: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [CartComponent],
      providers: [
        provideRouter([]),
        { provide: CartService, useValue: cart },
        { provide: AuthService, useValue: { isAuthenticated: () => true } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(CartComponent);
    fixture.detectChanges();
    const link = fixture.nativeElement.querySelector('a[href="/checkout"]') as HTMLAnchorElement;

    expect(link?.textContent).toContain('Revisar pedido y pago');
    expect(fixture.nativeElement.textContent).not.toContain('Confirmar pedido');
    expect(cart.checkout).not.toHaveBeenCalled();
  });

  it('offers undo after removing an item and restores its quantity', async () => {
    const cart = {
      items: signal([item]),
      count: signal(2),
      total: signal(3000),
      setQuantity: vi.fn(),
      add: vi.fn(),
      removeItem: vi.fn(),
      clear: vi.fn(),
      reconcile: vi.fn(() => of(true)),
      legacyCartDiscarded: signal(false),
      dismissLegacyCartWarning: vi.fn(),
      notice: signal(''),
      dismissNotice: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [CartComponent],
      providers: [
        provideRouter([]),
        { provide: CartService, useValue: cart },
        { provide: AuthService, useValue: { isAuthenticated: () => true } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(CartComponent);
    fixture.componentInstance.removeItem(item);
    fixture.componentInstance.undo();

    expect(cart.removeItem).toHaveBeenCalledWith(item.variant.id);
    expect(cart.add).toHaveBeenCalledWith(item.product, item.variant, item.quantity);
  });

  it('keeps consecutive removals in the same undo window', async () => {
    const secondItem: CartItem = {
      ...item,
      product: { ...item.product, id: 2, name: 'Mouse' },
      variant: { id: 12, colorName: 'Blanco', colorHex: '#ffffff', inStock: true },
      quantity: 1,
    };
    const cart = {
      items: signal([item, secondItem]), count: signal(3), total: signal(4500),
      setQuantity: vi.fn(), add: vi.fn(), removeItem: vi.fn(), clear: vi.fn(),
      reconcile: vi.fn(() => of(true)), legacyCartDiscarded: signal(false), dismissLegacyCartWarning: vi.fn(),
      notice: signal(''), dismissNotice: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [CartComponent],
      providers: [
        provideRouter([]),
        { provide: CartService, useValue: cart },
        { provide: AuthService, useValue: { isAuthenticated: () => true } },
      ],
    }).compileComponents();

    const component = TestBed.createComponent(CartComponent).componentInstance;
    component.removeItem(item);
    component.removeItem(secondItem);
    component.undo();

    expect(cart.add).toHaveBeenCalledTimes(2);
    expect(cart.add).toHaveBeenCalledWith(item.product, item.variant, item.quantity);
    expect(cart.add).toHaveBeenCalledWith(secondItem.product, secondItem.variant, secondItem.quantity);
  });
});

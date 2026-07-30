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
      removeItem: vi.fn(),
      clear: vi.fn(),
      checkout: vi.fn(),
      reconcile: vi.fn(() => of(undefined)),
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

    expect(link?.textContent).toContain('Continuar');
    expect(fixture.nativeElement.textContent).not.toContain('Confirmar pedido');
    expect(cart.checkout).not.toHaveBeenCalled();
  });
});

import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { CartItem, CartService } from '../../core/cart/cart.service';
import { CartComponent } from './cart.component';

describe('CartComponent', () => {
  const item: CartItem = {
    product: { id: 1, name: 'Teclado', slug: 'teclado', description: 'Mecánico', price: 1500, categoryId: 2, categoryName: 'Periféricos', brandId: 3, brandName: 'Marca', images: [], specifications: [], variants: [{ id: 11, colorName: 'Negro', colorHex: '#000000', inStock: true, availableQuantity: 5 }] },
    variant: { id: 11, colorName: 'Negro', colorHex: '#000000', inStock: true, availableQuantity: 5 },
    quantity: 2,
  };

  afterEach(() => vi.useRealTimers());

  it('always shows discounted cart prices and preselects bank transfer without discount wording', async () => {
    const cart = {
      items: signal([item]),
      count: signal(2),
      total: signal(3000),
      stockLimit: (variant: CartItem['variant']) => variant.availableQuantity,
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
    const link = fixture.nativeElement.querySelector('a[href^="/checkout"]') as HTMLAnchorElement;

    expect(link?.textContent).toContain('Revisar pedido y pago');
    expect(link.classList).toContain('app-button');
    expect(fixture.nativeElement.querySelector('.summary')?.classList).toContain('app-card');
    expect(fixture.nativeElement.textContent).not.toContain('Confirmar pedido');
    expect(fixture.nativeElement.textContent).not.toContain('Descuento por transferencia');
    expect(fixture.nativeElement.textContent).not.toContain('transferencia');
    expect(fixture.nativeElement.textContent).not.toContain('Precio de lista');
    expect(fixture.nativeElement.querySelector('input[type="checkbox"]')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('recargo');
    expect(fixture.componentInstance.displayedTotal()).toBe(2700);
    expect(fixture.componentInstance.unitPrice(item)).toBe(1350);
    expect(fixture.componentInstance.itemTotal(item)).toBe(2700);
    expect(link.getAttribute('href')).toBe('/checkout?paymentMethod=BANK_TRANSFER');
    expect(cart.checkout).not.toHaveBeenCalled();
  });

  it('preserves the bank-transfer checkout selection through login', async () => {
    const cart = {
      items: signal([item]), count: signal(2), total: signal(3000),
      stockLimit: (variant: CartItem['variant']) => variant.availableQuantity,
      setQuantity: vi.fn(), add: vi.fn(), removeItem: vi.fn(), clear: vi.fn(),
      reconcile: vi.fn(() => of(true)), legacyCartDiscarded: signal(false), dismissLegacyCartWarning: vi.fn(),
      notice: signal(''), dismissNotice: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [CartComponent],
      providers: [
        provideRouter([]),
        { provide: CartService, useValue: cart },
        { provide: AuthService, useValue: { isAuthenticated: () => false } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(CartComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.checkoutReturnUrl).toBe('/checkout?paymentMethod=BANK_TRANSFER');
    expect((fixture.nativeElement.querySelector('a[href^="/login"]') as HTMLAnchorElement).getAttribute('href'))
      .toContain('returnUrl=%2Fcheckout%3FpaymentMethod%3DBANK_TRANSFER');
  });

  it('offers undo after removing an item and restores its quantity', async () => {
    const cart = {
      items: signal([item]),
      count: signal(2),
      total: signal(3000),
      stockLimit: (variant: CartItem['variant']) => variant.availableQuantity,
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
    fixture.detectChanges();
    fixture.componentInstance.removeItem(item);
    fixture.detectChanges();
    await Promise.resolve();

    const undoFeedback = fixture.nativeElement.querySelector('.undo') as HTMLElement;
    const undoButton = undoFeedback.querySelector('button') as HTMLButtonElement;
    expect(undoFeedback.querySelector('.app-feedback__body')?.getAttribute('role')).toBe('status');
    expect(undoFeedback.querySelector('.app-feedback__body')?.getAttribute('aria-live')).toBeNull();
    expect(document.activeElement).toBe(undoButton);

    fixture.componentInstance.undo();
    await Promise.resolve();
    fixture.detectChanges();

    expect(cart.removeItem).toHaveBeenCalledWith(item.variant.id);
    expect(cart.add).toHaveBeenCalledWith(item.product, item.variant, item.quantity);
    expect(fixture.nativeElement.querySelector('.undo')).toBeNull();
    expect(fixture.nativeElement.querySelector('.sr-only')?.textContent).toContain('Producto restaurado');
  });

  it('expires the undo action after five seconds and returns focus to the heading', async () => {
    vi.useFakeTimers();
    const cart = {
      items: signal([item]), count: signal(2), total: signal(3000),
      stockLimit: (variant: CartItem['variant']) => variant.availableQuantity,
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

    const fixture = TestBed.createComponent(CartComponent);
    fixture.detectChanges();
    fixture.componentInstance.removeItem(item);
    fixture.detectChanges();
    await Promise.resolve();
    expect(document.activeElement).toBe(fixture.nativeElement.querySelector('.undo button'));

    vi.advanceTimersByTime(5000);
    fixture.detectChanges();
    await Promise.resolve();

    expect(fixture.nativeElement.querySelector('.undo')).toBeNull();
    expect(document.activeElement).toBe(fixture.nativeElement.querySelector('#cart-title'));
  });

  it('keeps consecutive removals in the same undo window', async () => {
    const secondItem: CartItem = {
      ...item,
      product: { ...item.product, id: 2, name: 'Mouse' },
      variant: { id: 12, colorName: 'Blanco', colorHex: '#ffffff', inStock: true, availableQuantity: 5 },
      quantity: 1,
    };
    const cart = {
      items: signal([item, secondItem]), count: signal(3), total: signal(4500),
      stockLimit: (variant: CartItem['variant']) => variant.availableQuantity,
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

  describe('cart continuity motion', () => {
    const secondItem: CartItem = {
      ...item,
      product: { ...item.product, id: 2, name: 'Mouse' },
      variant: { id: 12, colorName: 'Blanco', colorHex: '#ffffff', inStock: true, availableQuantity: 5 },
      quantity: 1,
    };
    let reducedMotion = false;
    let positions: Record<string, { left: number; top: number; width?: number; height?: number }>;
    let animateDescriptor: PropertyDescriptor | undefined;
    let matchMediaDescriptor: PropertyDescriptor | undefined;
    let rectSpy: ReturnType<typeof vi.spyOn>;
    let animations: Array<{
      element: HTMLElement;
      keyframes: Keyframe[];
      options: KeyframeAnimationOptions;
      animation: Animation;
      finish: () => void;
    }>;

    beforeEach(() => {
      positions = {};
      animations = [];
      reducedMotion = false;
      animateDescriptor = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'animate');
      matchMediaDescriptor = Object.getOwnPropertyDescriptor(window, 'matchMedia');
      Object.defineProperty(window, 'matchMedia', {
        configurable: true,
        value: vi.fn(() => ({ matches: reducedMotion })),
      });
      Object.defineProperty(HTMLElement.prototype, 'animate', {
        configurable: true,
        value: function (keyframes: Keyframe[], options: KeyframeAnimationOptions): Animation {
          let resolveFinished!: (animation: Animation) => void;
          let rejectFinished!: (reason: unknown) => void;
          const finished = new Promise<Animation>((resolve, reject) => {
            resolveFinished = resolve;
            rejectFinished = reject;
          });
          const animation = {
            cancel: vi.fn(() => rejectFinished(new Error('cancelled'))),
            finished,
          } as unknown as Animation;
          animations.push({
            element: this,
            keyframes,
            options,
            animation,
            finish: () => resolveFinished(animation),
          });
          return animation;
        },
      });
      rectSpy = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function () {
        const position = positions[this.dataset['cartMotion'] ?? ''] ?? { left: 0, top: 0, width: 100, height: 60 };
        return new DOMRect(position.left, position.top, position.width ?? 100, position.height ?? 60);
      });
    });

    afterEach(() => {
      rectSpy.mockRestore();
      if (animateDescriptor) Object.defineProperty(HTMLElement.prototype, 'animate', animateDescriptor);
      else delete (HTMLElement.prototype as Partial<HTMLElement>).animate;
      if (matchMediaDescriptor) Object.defineProperty(window, 'matchMedia', matchMediaDescriptor);
      else delete (window as Partial<Window>).matchMedia;
      document.querySelectorAll('.cart-motion-ghost').forEach((ghost) => ghost.remove());
    });

    function cartStub(initialItems: CartItem[]) {
      const items = signal(initialItems);
      const count = signal(initialItems.reduce((total, current) => total + current.quantity, 0));
      const total = signal(initialItems.reduce((sum, current) => sum + current.product.price * current.quantity, 0));
      const updateTotals = () => {
        count.set(items().reduce((sum, current) => sum + current.quantity, 0));
        total.set(items().reduce((sum, current) => sum + current.product.price * current.quantity, 0));
      };
      return {
        items,
        count,
        total,
        stockLimit: (variant: CartItem['variant']) => variant.availableQuantity,
        setQuantity: vi.fn(),
        add: vi.fn((product: CartItem['product'], variant: CartItem['variant'], quantity: number) => {
          items.update((current) => [...current, { product, variant, quantity }]);
          updateTotals();
        }),
        removeItem: vi.fn((variantId: number) => {
          items.update((current) => current.filter((currentItem) => currentItem.variant.id !== variantId));
          updateTotals();
        }),
        clear: vi.fn(() => { items.set([]); updateTotals(); }),
        reconcile: vi.fn(() => of(true)),
        legacyCartDiscarded: signal(false),
        dismissLegacyCartWarning: vi.fn(),
        notice: signal(''),
        dismissNotice: vi.fn(),
      };
    }

    async function createCart(initialItems: CartItem[]) {
      const cart = cartStub(initialItems);
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
      return { cart, fixture };
    }

    it('mutates and restores signals immediately and restores undo focus without waiting for motion', async () => {
      const { cart, fixture } = await createCart([item, secondItem]);

      fixture.componentInstance.removeItem(item);

      expect(cart.items()).toEqual([secondItem]);
      expect(cart.count()).toBe(1);
      expect(cart.total()).toBe(1500);
      expect(fixture.componentInstance.pendingUndo()?.items).toEqual([item]);
      fixture.detectChanges();
      await Promise.resolve();
      const undo = fixture.nativeElement.querySelector('.undo button') as HTMLButtonElement;
      expect(document.activeElement).toBe(undo);

      fixture.componentInstance.undo();

      expect(cart.items()).toEqual([secondItem, item]);
      expect(cart.count()).toBe(3);
      expect(cart.total()).toBe(4500);
      expect(fixture.componentInstance.pendingUndo()).toBeNull();
      expect(document.activeElement).toBe(fixture.nativeElement.querySelector('#cart-title'));
      await Promise.resolve();
      expect(fixture.componentInstance.feedback()).toBe('Producto restaurado.');
      fixture.destroy();
    });

    it('animates and cleans an inert, aria-hidden removal ghost', async () => {
      const { fixture } = await createCart([item, secondItem]);

      fixture.componentInstance.removeItem(item);
      const ghost = document.querySelector('.cart-motion-ghost') as HTMLElement;
      const ghostMotion = animations.find((record) => record.element === ghost)!;

      expect(ghost).toBeTruthy();
      expect(ghost.inert).toBe(true);
      expect(ghost.getAttribute('inert')).toBe('');
      expect(ghost.getAttribute('aria-hidden')).toBe('true');
      expect(ghost.style.pointerEvents).toBe('none');
      expect(ghostMotion.options).toMatchObject({ duration: 180, easing: 'cubic-bezier(0.23, 1, 0.32, 1)' });
      expect(ghostMotion.keyframes).toEqual([
        { opacity: 1, transform: 'translateY(0) scale(1)' },
        { opacity: 0, transform: 'translateY(-.35rem) scale(.98)' },
      ]);

      ghostMotion.finish();
      await Promise.resolve();
      expect(ghost.isConnected).toBe(false);
      fixture.destroy();
    });

    it('FLIPs rows, clear and summary without animating their existing layout parent', async () => {
      positions = {
        'row-11': { left: 20, top: 100 },
        'row-12': { left: 20, top: 200 },
        clear: { left: 20, top: 300 },
        layout: { left: 20, top: 80 },
        summary: { left: 500, top: 80 },
      };
      const { fixture } = await createCart([item, secondItem]);

      fixture.componentInstance.removeItem(item);
      positions['row-12'] = { left: 20, top: 100 };
      positions['clear'] = { left: 20, top: 200 };
      positions['layout'] = { left: 20, top: 20 };
      positions['summary'] = { left: 450, top: 40 };
      fixture.detectChanges();
      await fixture.whenStable();

      const survivor = animations.find((record) => record.element.dataset['cartMotion'] === 'row-12')!;
      const clear = animations.find((record) => record.element.dataset['cartMotion'] === 'clear')!;
      const summary = animations.find((record) => record.element.dataset['cartMotion'] === 'summary')!;
      expect(survivor.options).toMatchObject({ duration: 240, easing: 'cubic-bezier(0.77, 0, 0.175, 1)' });
      expect(survivor.keyframes).toEqual([
        { transform: 'translate3d(0px, 100px, 0)' },
        { transform: 'translate3d(0, 0, 0)' },
      ]);
      expect(clear.keyframes).toEqual([
        { transform: 'translate3d(0px, 100px, 0)' },
        { transform: 'translate3d(0, 0, 0)' },
      ]);
      expect(summary.keyframes).toEqual([
        { transform: 'translate3d(50px, 40px, 0)' },
        { transform: 'translate3d(0, 0, 0)' },
      ]);
      expect(animations.some((record) => record.element.dataset['cartMotion'] === 'layout')).toBe(false);
      fixture.destroy();
    });

    it('skips ghosts and all animate calls in reduced-motion mode', async () => {
      reducedMotion = true;
      const { cart, fixture } = await createCart([item]);

      fixture.componentInstance.removeItem(item);
      fixture.detectChanges();
      await fixture.whenStable();

      expect(cart.items()).toEqual([]);
      expect(animations).toHaveLength(0);
      expect(document.querySelector('.cart-motion-ghost')).toBeNull();
      fixture.destroy();
    });

    it('animates the empty state when the last row is removed', async () => {
      positions = { 'row-11': { left: 0, top: 100 }, clear: { left: 0, top: 200 }, layout: { left: 0, top: 80 } };
      const { fixture } = await createCart([item]);

      fixture.componentInstance.removeItem(item);
      fixture.detectChanges();

      const emptyEntry = animations.find((record) => record.element.dataset['cartMotion'] === 'empty')!;
      expect(emptyEntry.options).toMatchObject({ duration: 200, easing: 'cubic-bezier(0.23, 1, 0.32, 1)' });
      expect(emptyEntry.keyframes).toEqual([
        { opacity: 0, transform: 'translateY(-.5rem) scale(.98)' },
        { opacity: 1, transform: 'none' },
      ]);
      fixture.destroy();
    });

    it('animates only the layout entry when restoring a cart from empty', async () => {
      const { fixture } = await createCart([item]);
      fixture.componentInstance.clear();
      fixture.detectChanges();
      await Promise.resolve();

      fixture.componentInstance.undo();
      fixture.detectChanges();
      await fixture.whenStable();

      expect(animations).toHaveLength(1);
      expect(animations[0].element.dataset['cartMotion']).toBe('layout');
      expect(animations[0].keyframes).toEqual([
        { opacity: 0, transform: 'translateY(-.5rem) scale(.98)' },
        { opacity: 1, transform: 'none' },
      ]);
      fixture.destroy();
    });

    it('cancels prior motion and removes every ghost during rapid removals and destroy', async () => {
      const { fixture } = await createCart([item, secondItem]);
      fixture.componentInstance.removeItem(item);
      fixture.detectChanges();
      const firstGhost = document.querySelector('.cart-motion-ghost') as HTMLElement;
      const firstAnimations = [...animations];

      fixture.componentInstance.removeItem(secondItem);

      expect(firstGhost.isConnected).toBe(false);
      expect(firstAnimations.every((record) => record.animation.cancel instanceof Function && (record.animation.cancel as ReturnType<typeof vi.fn>).mock.calls.length === 1)).toBe(true);
      expect(document.querySelectorAll('.cart-motion-ghost')).toHaveLength(1);
      fixture.destroy();
      expect(document.querySelectorAll('.cart-motion-ghost')).toHaveLength(0);
    });
  });
});

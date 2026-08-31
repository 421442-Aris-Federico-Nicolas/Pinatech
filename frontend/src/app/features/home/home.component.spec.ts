import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { Observable, of } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { BannerCarouselComponent } from '../../shared/ui/banner-carousel/banner-carousel.component';
import { CatalogFilters, CatalogService, CatalogSort, Page, Product } from '../catalog/catalog.service';
import { HomeComponent } from './home.component';

describe('HomeComponent', () => {
  type GetProducts = (filters: CatalogFilters, page: number, sort?: CatalogSort, pageSize?: number) => Observable<Page<Product>>;
  const product = (id: number, name: string, categoryId: number, categoryName: string): Product => ({
    id,
    name,
    slug: name.toLowerCase().replaceAll(' ', '-'),
    description: `${name} destacado`,
    price: 1000,
    categoryId,
    categoryName,
    brandId: 1,
    brandName: 'Pinatech',
    images: [],
    specifications: [],
    variants: [{ id: id * 10, colorName: 'Negro', colorHex: '#000000', imageId: null, inStock: true, availableQuantity: 4 }],
  });
  const mouse = product(1, 'Mouse Pro', 5, 'Periféricos');
  const processor = product(2, 'Ryzen Pro', 1, 'Procesadores');

  async function createHome(getProducts: GetProducts = vi.fn(() => of({ content: [mouse, processor], totalPages: 1, totalElements: 2, number: 0, size: 100 } as Page<Product>))) {
    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        provideRouter([]),
        { provide: CatalogService, useValue: {
          categories: () => of([{ id: 1, name: 'Procesadores', slug: 'procesadores' }, { id: 5, name: 'Periféricos', slug: 'perifericos' }]),
          getProducts,
        } },
        { provide: AuthService, useValue: { isAuthenticated: () => false } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('keeps the hero, product categories and technical service as separate sections', async () => {
    const fixture = await createHome();

    const carousels = fixture.debugElement.queryAll(By.directive(BannerCarouselComponent));
    const productSections = fixture.nativeElement.querySelectorAll('.product-showcase') as NodeListOf<HTMLElement>;
    expect(carousels).toHaveLength(1);
    expect(carousels[0].componentInstance.slides().map((slide: { src: string }) => slide.src)).toEqual(['/pinatech-banner-home.jpg', '/pinatech-banner-cart.jpg']);
    expect(productSections).toHaveLength(2);
    expect(productSections[0].textContent).toContain('Mouse Pro');
    expect(productSections[0].textContent).not.toContain('Ryzen Pro');
    expect(productSections[1].textContent).toContain('Ryzen Pro');
    expect(productSections[1].textContent).not.toContain('Mouse Pro');
    const firstHeroCopy = fixture.nativeElement.querySelector('.hero-copy') as HTMLElement;
    expect(firstHeroCopy.textContent).toContain('Elevá tu setup.');

    (carousels[0].componentInstance as BannerCarouselComponent).next();
    fixture.detectChanges();

    const replacementHeroCopy = fixture.nativeElement.querySelector('.hero-copy') as HTMLElement;
    expect(replacementHeroCopy).not.toBe(firstHeroCopy);
    expect(replacementHeroCopy.textContent).toContain('No dejes que tu carrito');
    expect(fixture.nativeElement.querySelector('.hero-actions a').textContent).toContain('Ver mi carrito');
    expect(fixture.nativeElement.querySelector('.hero-actions a').getAttribute('href')).toBe('/cart');
    expect(fixture.nativeElement.querySelector('.service a').getAttribute('href')).toBe('/tickets');
  });

  it('pauses autoplay temporarily while pointer or focus remain inside the hero', async () => {
    const fixture = await createHome();
    const carousel = fixture.debugElement.query(By.directive(BannerCarouselComponent)).componentInstance as BannerCarouselComponent;
    const hero = fixture.nativeElement.querySelector('.hero') as HTMLElement;
    const outsideAction = fixture.nativeElement.querySelector('.paths a') as HTMLElement;

    hero.dispatchEvent(new Event('pointerenter'));
    hero.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
    fixture.detectChanges();
    expect(carousel.paused()).toBe(true);
    expect(carousel.autoplayPaused()).toBe(true);

    hero.dispatchEvent(new Event('pointerleave'));
    fixture.detectChanges();
    expect(carousel.paused()).toBe(true);
    expect(carousel.autoplayPaused()).toBe(true);

    hero.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: outsideAction }));
    fixture.detectChanges();
    expect(carousel.paused()).toBe(false);
    expect(carousel.autoplayPaused()).toBe(false);
    expect(fixture.nativeElement.querySelector('.banner-carousel__autoplay')).toBeNull();
  });

  it('loads every product page in blocks of 100 and renders more than two products per group', async () => {
    const keyboard = product(3, 'Teclado Pro', 5, 'Periféricos');
    const headset = product(4, 'Auriculares Pro', 5, 'Periféricos');
    const getProducts = vi.fn((_filters: CatalogFilters, page: number, _sort: CatalogSort = 'name,asc', size = 12) => of(page === 0
      ? { content: [mouse, keyboard], totalPages: 2, totalElements: 4, number: 0, size }
      : { content: [headset, processor], totalPages: 2, totalElements: 4, number: 1, size }));

    const fixture = await createHome(getProducts);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(getProducts.mock.calls.map((call) => [call[1], call[3]])).toEqual([[0, 100], [1, 100]]);
    const peripheralCards = fixture.nativeElement.querySelectorAll('.product-showcase:first-child app-product-card');
    expect(peripheralCards).toHaveLength(3);
    expect(fixture.nativeElement.querySelector('.product-showcase:first-child').textContent).toContain('Auriculares Pro');
  });

  it('updates control bounds and handles track-only navigation keys without wrapping', async () => {
    const keyboard = product(3, 'Teclado Pro', 5, 'Periféricos');
    const fixture = await createHome(vi.fn(() => of({ content: [mouse, keyboard, processor], totalPages: 1, totalElements: 3, number: 0, size: 100 })));
    await fixture.whenStable();
    fixture.detectChanges();
    const showcase = fixture.nativeElement.querySelector('.product-showcase') as HTMLElement;
    const track = showcase.querySelector('.products') as HTMLElement;
    let scrollLeft = 0;
    Object.defineProperties(track, {
      clientWidth: { configurable: true, value: 300 },
      scrollWidth: { configurable: true, value: 900 },
      scrollLeft: { configurable: true, get: () => scrollLeft, set: (value: number) => { scrollLeft = value; } },
    });
    const scrollTo = vi.fn((options: ScrollToOptions) => {
      scrollLeft = Math.max(0, Math.min(600, Number(options.left)));
      track.dispatchEvent(new Event('scroll'));
    });
    Object.defineProperty(track, 'scrollTo', { configurable: true, value: scrollTo });
    track.dispatchEvent(new Event('scroll'));
    fixture.detectChanges();
    const [previous, next] = [...showcase.querySelectorAll<HTMLButtonElement>('.product-track-controls button')];

    expect(previous.getAttribute('aria-controls')).toBe(track.id);
    expect(next.getAttribute('aria-controls')).toBe(track.id);
    expect(previous.disabled).toBe(true);
    expect(next.disabled).toBe(false);
    next.click();
    fixture.detectChanges();
    expect(scrollLeft).toBe(300);
    expect(previous.disabled).toBe(false);

    track.dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true }));
    fixture.detectChanges();
    expect(scrollLeft).toBe(600);
    expect(next.disabled).toBe(true);

    track.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true }));
    fixture.detectChanges();
    expect(scrollLeft).toBe(0);
    expect(previous.disabled).toBe(true);

    scrollTo.mockClear();
    track.querySelector('a')?.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    expect(scrollTo).not.toHaveBeenCalled();
  });
});

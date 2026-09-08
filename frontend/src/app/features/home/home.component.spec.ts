import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { BannerCarouselComponent } from '../../shared/ui/banner-carousel/banner-carousel.component';
import { ProductListItemResponse as Product } from '../catalog/catalog.service';
import { HomeComponent } from './home.component';
import { HomeSection, HomeSectionsService } from './home-sections.service';

describe('HomeComponent', () => {
  const product = (id: number, name: string, categoryId = 5): Product => ({
    id, name, slug: name.toLowerCase().replaceAll(' ', '-'), price: 1000, categoryId,
    categoryName: 'Periféricos', brandId: 1, brandName: 'Pinatech', images: [], inStock: true,
  });
  const mouse = product(1, 'Mouse Pro');
  const automatic: HomeSection = {
    id: 17, displayOrder: 0, eyebrow: 'Periféricos', title: 'Completá tu setup',
    description: 'Todo para tu escritorio.', buttonLabel: 'Ver periféricos', mode: 'AUTOMATIC',
    productLimit: 12, sort: 'NAME_ASC', bannerDesktopUrl: '/pinatech-banner-perifericos.jpg',
    bannerMobileUrl: '/api/home/sections/17/mobile', categories: [{ id: 5, name: 'Periféricos', slug: 'perifericos' }], products: [mouse],
  };

  async function createHome(response: Observable<HomeSection[]> = of([automatic])) {
    const sections = vi.fn(() => response);
    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        provideRouter([]),
        { provide: HomeSectionsService, useValue: { sections } },
        { provide: AuthService, useValue: { isAuthenticated: () => false } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, sections };
  }

  it('keeps hero, access and service blocks while rendering API sections from one request', async () => {
    const { fixture, sections } = await createHome();
    const carousel = fixture.debugElement.query(By.directive(BannerCarouselComponent)).componentInstance as BannerCarouselComponent;
    const showcase = fixture.nativeElement.querySelector('.product-showcase') as HTMLElement;

    expect(sections).toHaveBeenCalledOnce();
    expect(showcase.textContent).toContain('Mouse Pro');
    expect(showcase.id).toBe('');
    expect(showcase.querySelector('.products')?.id).toBe('product-track-17');
    expect(fixture.nativeElement.querySelectorAll('.paths a')).toHaveLength(2);
    expect(fixture.nativeElement.querySelector('.service a').getAttribute('href')).toBe('/tickets');
    expect(carousel.slides().map((slide) => slide.src)).toEqual(['/pinatech-banner-home.jpg', '/pinatech-banner-cart.jpg']);
  });

  it('uses responsive banner fallback, keeps static assets on the frontend and resolves API paths', async () => {
    const desktopOnly = { ...automatic, id: 18, bannerMobileUrl: null };
    const { fixture } = await createHome(of([automatic, desktopOnly]));
    const [responsive, fallback] = [...fixture.nativeElement.querySelectorAll('.product-showcase')] as HTMLElement[];

    expect(responsive.querySelector('img')?.getAttribute('src')).toBe('/pinatech-banner-perifericos.jpg');
    expect(responsive.querySelector('source')?.getAttribute('srcset')).toContain('/api/home/sections/17/mobile');
    expect(fallback.querySelector('source')?.getAttribute('srcset')).toBe('/pinatech-banner-perifericos.jpg');
  });

  it('shows CTA only for automatic sections with a nonblank label and encodes multiple categories as CSV', async () => {
    const manual = { ...automatic, id: 18, mode: 'MANUAL' as const, title: 'Elegidos', buttonLabel: 'No visible', categories: [] };
    const blank = { ...automatic, id: 19, title: 'Sin CTA', buttonLabel: '  ', categories: [] };
    const multiple = { ...automatic, categories: [...automatic.categories, { id: 8, name: 'Audio', slug: 'audio' }] };
    const { fixture } = await createHome(of([multiple, manual, blank]));
    const showcases = [...fixture.nativeElement.querySelectorAll('.product-showcase')] as HTMLElement[];

    expect(showcases[0].querySelector('.product-promo__copy a')?.getAttribute('href')).toContain('category=5,8');
    expect(showcases[1].querySelector('.product-promo__copy a')).toBeNull();
    expect(showcases[2].querySelector('.product-promo__copy a')).toBeNull();
  });

  it('omits blank optional copy and uses a neutral section when no banner exists', async () => {
    const minimal = { ...automatic, eyebrow: null, description: null, buttonLabel: null, bannerDesktopUrl: null, bannerMobileUrl: null };
    const { fixture } = await createHome(of([minimal]));
    const showcase = fixture.nativeElement.querySelector('.product-showcase') as HTMLElement;
    expect(showcase.classList.contains('no-banner')).toBe(true);
    expect(showcase.querySelector('picture')).toBeNull();
    expect(showcase.querySelector('.product-promo__copy > .eyebrow')).toBeNull();
    expect(showcase.querySelector('.product-promo__copy > p:not(.eyebrow)')).toBeNull();
  });

  it('keeps the rest of Home and omits the showcase area for an empty response', async () => {
    const { fixture } = await createHome(of([]));
    expect(fixture.nativeElement.querySelector('.featured')).toBeNull();
    expect(fixture.nativeElement.querySelector('.hero')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.service')).toBeTruthy();
  });

  it('renders an error with retry without removing stable Home blocks', async () => {
    const { fixture } = await createHome(throwError(() => new Error('offline')));
    expect(fixture.nativeElement.textContent).toContain('No pudimos cargar las secciones del inicio');
    expect(fixture.nativeElement.querySelector('.hero')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.service')).toBeTruthy();
  });

  it('pauses the hero while pointer or focus remain inside it', async () => {
    const { fixture } = await createHome();
    const carousel = fixture.debugElement.query(By.directive(BannerCarouselComponent)).componentInstance as BannerCarouselComponent;
    const hero = fixture.nativeElement.querySelector('.hero') as HTMLElement;
    hero.dispatchEvent(new Event('pointerenter'));
    hero.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
    fixture.detectChanges();
    expect(carousel.paused()).toBe(true);
    hero.dispatchEvent(new Event('pointerleave'));
    hero.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: fixture.nativeElement.querySelector('.paths a') }));
    fixture.detectChanges();
    expect(carousel.paused()).toBe(false);
  });

  it('keeps carousel controls bounded and keyboard accessible', async () => {
    const { fixture } = await createHome(of([{ ...automatic, products: [mouse, product(2, 'Teclado')] }]));
    const showcase = fixture.nativeElement.querySelector('.product-showcase') as HTMLElement;
    const track = showcase.querySelector('.products') as HTMLElement;
    let scrollLeft = 0;
    Object.defineProperties(track, {
      clientWidth: { configurable: true, value: 300 }, scrollWidth: { configurable: true, value: 900 },
      scrollLeft: { configurable: true, get: () => scrollLeft, set: (value: number) => { scrollLeft = value; } },
    });
    Object.defineProperty(track, 'scrollTo', { configurable: true, value: (options: ScrollToOptions) => { scrollLeft = Number(options.left); track.dispatchEvent(new Event('scroll')); } });
    track.dispatchEvent(new Event('scroll')); fixture.detectChanges();
    const [previous, next] = [...showcase.querySelectorAll<HTMLButtonElement>('.product-track-controls button')];
    expect(previous.disabled).toBe(true); expect(next.disabled).toBe(false);
    track.dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true })); fixture.detectChanges();
    expect(scrollLeft).toBe(900); expect(next.disabled).toBe(true);
  });
});

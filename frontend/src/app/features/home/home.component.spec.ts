import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { BannerCarouselComponent } from '../../shared/ui/banner-carousel/banner-carousel.component';
import { CatalogService, Product } from '../catalog/catalog.service';
import { HomeComponent } from './home.component';

describe('HomeComponent', () => {
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

  async function createHome() {
    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        provideRouter([]),
        { provide: CatalogService, useValue: {
          categories: () => of([{ id: 1, name: 'Procesadores', slug: 'procesadores' }, { id: 5, name: 'Periféricos', slug: 'perifericos' }]),
          getProducts: () => of({ content: [mouse, processor], totalPages: 1, totalElements: 2, number: 0, size: 12 }),
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
});

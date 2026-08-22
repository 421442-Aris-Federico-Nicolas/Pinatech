import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { CartService } from '../../core/cart/cart.service';
import { NotificationService } from '../../core/notifications/notification.service';
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
    variants: [{ id: id * 10, colorName: 'Negro', colorHex: '#000000', inStock: true, availableQuantity: 4 }],
  });

  it('keeps the hero, product categories and technical service as separate sections', async () => {
    const mouse = product(1, 'Mouse Pro', 5, 'Periféricos');
    const processor = product(2, 'Ryzen Pro', 1, 'Procesadores');
    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        provideRouter([]),
        { provide: CatalogService, useValue: {
          categories: () => of([{ id: 1, name: 'Procesadores', slug: 'procesadores' }, { id: 5, name: 'Periféricos', slug: 'perifericos' }]),
          getProducts: () => of({ content: [mouse, processor], totalPages: 1, totalElements: 2, number: 0, size: 12 }),
        } },
        { provide: AuthService, useValue: { isAuthenticated: () => false } },
        { provide: CartService, useValue: { add: () => ({ requested: 1, added: 1, quantity: 1, limit: 4, capped: false }) } },
        { provide: NotificationService, useValue: {} },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();

    const carousels = fixture.debugElement.queryAll(By.directive(BannerCarouselComponent));
    const productSections = fixture.nativeElement.querySelectorAll('.product-showcase') as NodeListOf<HTMLElement>;
    expect(carousels).toHaveLength(1);
    expect(carousels[0].componentInstance.slides().map((slide: { src: string }) => slide.src)).toEqual(['/pinatech-banner-home.jpg', '/pinatech-banner-cart.jpg']);
    expect(productSections).toHaveLength(2);
    expect(productSections[0].textContent).toContain('Mouse Pro');
    expect(productSections[0].textContent).not.toContain('Ryzen Pro');
    expect(productSections[1].textContent).toContain('Ryzen Pro');
    expect(productSections[1].textContent).not.toContain('Mouse Pro');

    const hero = fixture.nativeElement.querySelector('.hero') as HTMLElement;
    hero.dispatchEvent(new MouseEvent('mouseenter'));
    fixture.detectChanges();
    expect(carousels[0].componentInstance.paused()).toBe(true);
    hero.dispatchEvent(new MouseEvent('mouseleave'));
    fixture.detectChanges();
    expect(carousels[0].componentInstance.paused()).toBe(false);

    (carousels[0].componentInstance as BannerCarouselComponent).next();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.hero-actions a').textContent).toContain('Ver mi carrito');
    expect(fixture.nativeElement.querySelector('.hero-actions a').getAttribute('href')).toBe('/cart');
    expect(fixture.nativeElement.querySelector('.service a').getAttribute('href')).toBe('/tickets');
  });
});

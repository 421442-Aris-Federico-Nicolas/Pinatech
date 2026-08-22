import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { App } from './app';
import { routes } from './app.routes';
import { customerGuard } from './core/guards/customer.guard';

@Component({ template: '<h1>Catálogo</h1><input aria-label="Buscar">' })
class TestCatalogPage {}

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([
        { path: 'catalog', component: TestCatalogPage },
        { path: 'products/:id', component: TestCatalogPage },
      ])],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('shows the global footer with a safe Instagram link and copyright', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    const footer = fixture.nativeElement.querySelector('.site-footer') as HTMLElement;
    const instagram = footer.querySelector('a[href="https://www.instagram.com/pinatech.cba/"]') as HTMLAnchorElement;

    expect(footer.textContent).toContain('@pinatech.cba');
    expect(footer.textContent).toContain(`© ${new Date().getFullYear()} Pinatech`);
    expect(instagram.target).toBe('_blank');
    expect(instagram.rel).toContain('noopener');
    expect(instagram.querySelector('iconify-icon')?.getAttribute('icon')).toBe('mdi:instagram');
  });

  it('protects customer order and checkout result routes', () => {
    expect(routes.find((route) => route.path === 'orders')?.canActivate).toContain(customerGuard);
    expect(routes.find((route) => route.path === 'checkout/result')?.canActivate).toContain(customerGuard);
  });

  it('skips focus on initial load, preserves it for query changes and focuses new pages', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    fixture.detectChanges();
    await router.navigateByUrl('/catalog');
    fixture.detectChanges();
    await fixture.whenStable();

    const heading = fixture.nativeElement.querySelector('h1') as HTMLHeadingElement;
    expect(document.activeElement).not.toBe(heading);

    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    input.focus();
    await router.navigate([], { queryParams: { search: 'mouse' } });
    fixture.detectChanges();
    await fixture.whenStable();

    expect(document.activeElement).toBe(input);
    await router.navigateByUrl('/products/1');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.componentInstance.catalogActive()).toBe(true);
    expect(document.activeElement).toBe(fixture.nativeElement.querySelector('h1'));
  });
});

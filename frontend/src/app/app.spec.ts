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

  it('protects customer order and checkout result routes', () => {
    expect(routes.find((route) => route.path === 'orders')?.canActivate).toContain(customerGuard);
    expect(routes.find((route) => route.path === 'checkout/result')?.canActivate).toContain(customerGuard);
  });

  it('keeps focus in place for query-only navigation and groups product routes with catalog', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    fixture.detectChanges();
    await router.navigateByUrl('/catalog');
    fixture.detectChanges();
    await fixture.whenStable();

    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    input.focus();
    await router.navigate([], { queryParams: { search: 'mouse' } });
    fixture.detectChanges();
    await fixture.whenStable();

    expect(document.activeElement).toBe(input);
    await router.navigateByUrl('/products/1');
    expect(fixture.componentInstance.catalogActive()).toBe(true);
  });
});

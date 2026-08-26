import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { afterEach, vi } from 'vitest';
import { App } from './app';
import { routes } from './app.routes';
import { customerGuard } from './core/guards/customer.guard';
import { NotificationService } from './core/notifications/notification.service';

@Component({ template: '<h1>Catálogo</h1><input aria-label="Buscar">' })
class TestCatalogPage {}

describe('App', () => {
  afterEach(() => vi.useRealTimers());

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

  it('renders and dismisses global interaction feedback', () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(App);
    const notifications = TestBed.inject(NotificationService);
    fixture.detectChanges();

    notifications.success('Producto agregado al carrito.');
    fixture.detectChanges();

    const feedback = fixture.nativeElement.querySelector('.app-notification') as HTMLElement;
    expect(feedback.textContent).toContain('Producto agregado al carrito.');
    expect(feedback.getAttribute('role')).toBe('status');
    expect(feedback.getAttribute('aria-live')).toBe('polite');
    (feedback.querySelector('.notification-close') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(feedback.classList).toContain('is-exiting');
    vi.advanceTimersByTime(180);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.app-notification')).toBeNull();
  });

  it('keeps contextual actions separate from manual dismissal', () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(App);
    const notifications = TestBed.inject(NotificationService);
    const action = vi.fn();
    fixture.detectChanges();

    notifications.warning('Alcanzaste el máximo.', 'Ver carrito').onAction().subscribe(action);
    fixture.detectChanges();

    const feedback = fixture.nativeElement.querySelector('.app-notification') as HTMLElement;
    expect(feedback.querySelector('.notification-action')?.textContent).toContain('Ver carrito');
    (feedback.querySelector('.notification-close') as HTMLButtonElement).click();
    vi.advanceTimersByTime(180);
    fixture.detectChanges();

    expect(action).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.app-notification')).toBeNull();
  });

  it('keeps an actionable notification visible while the outlet is hovered', () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(App);
    const notifications = TestBed.inject(NotificationService);
    fixture.detectChanges();
    notifications.show('Pedido pendiente', { action: 'Ver pedido', duration: 1000 });
    fixture.detectChanges();

    const feedback = fixture.nativeElement.querySelector('.app-notification') as HTMLElement;
    vi.advanceTimersByTime(400);
    feedback.dispatchEvent(new MouseEvent('mouseenter'));
    vi.advanceTimersByTime(2000);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.app-notification')).toBe(feedback);

    feedback.dispatchEvent(new MouseEvent('mouseleave'));
    vi.advanceTimersByTime(600);
    fixture.detectChanges();
    expect(feedback.classList).toContain('is-exiting');
  });

  it('announces errors assertively', () => {
    const fixture = TestBed.createComponent(App);
    const notifications = TestBed.inject(NotificationService);
    fixture.detectChanges();

    notifications.error('No se pudo completar la operación.');
    fixture.detectChanges();

    const feedback = fixture.nativeElement.querySelector('.app-notification') as HTMLElement;
    expect(feedback.getAttribute('role')).toBe('alert');
    expect(feedback.getAttribute('aria-live')).toBe('assertive');
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

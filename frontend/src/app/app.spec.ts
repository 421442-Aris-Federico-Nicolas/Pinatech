import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { afterEach, vi } from 'vitest';
import { App } from './app';
import { routes } from './app.routes';
import { customerGuard } from './core/guards/customer.guard';
import { DeploymentVersionService } from './core/deployment/deployment-version.service';
import { NotificationService } from './core/notifications/notification.service';

@Component({ template: '<h1>Catálogo</h1><input aria-label="Buscar">' })
class TestCatalogPage {}

describe('App', () => {
  const updateAvailable = signal(false);
  const reload = vi.fn();

  afterEach(() => vi.useRealTimers());

  beforeEach(async () => {
    updateAvailable.set(false);
    reload.mockReset();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([
        { path: 'catalog', component: TestCatalogPage },
        { path: 'products/:id', component: TestCatalogPage },
      ]), { provide: DeploymentVersionService, useValue: { updateAvailable, reload } }],
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
    expect(feedback.querySelector('.notification-announcement')?.getAttribute('role')).toBe('status');
    expect(feedback.querySelector('.notification-announcement')?.getAttribute('aria-live')).toBeNull();
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

  it('keeps a deployment update visible until its explicit action reloads the page', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.deployment-update')).toBeNull();

    updateAvailable.set(true);
    fixture.detectChanges();

    const update = fixture.nativeElement.querySelector('.deployment-update') as HTMLElement;
    expect(update.textContent).toContain('Nueva versión disponible');
    expect(update.querySelector('.app-feedback__body')?.getAttribute('role')).toBe('status');
    (update.querySelector('button') as HTMLButtonElement).click();
    expect(reload).toHaveBeenCalledOnce();
  });

  it('runs a contextual action only from its explicit button', () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(App);
    const notifications = TestBed.inject(NotificationService);
    const action = vi.fn();
    fixture.detectChanges();

    notifications.warning('Alcanzaste el máximo.', 'Ver carrito').onAction().subscribe(action);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.notification-action') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(action).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.querySelector('.app-notification').classList).toContain('is-exiting');
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
    expect(feedback.querySelector('.notification-announcement')?.getAttribute('role')).toBe('alert');
    expect(feedback.querySelector('.notification-announcement')?.getAttribute('aria-live')).toBeNull();
  });

  it.each([
    ['info', 'Información', 'line-md:bell', 'status'],
    ['success', 'Listo', 'line-md:confirm-circle', 'status'],
    ['warning', 'Atención', 'line-md:alert-circle', 'status'],
    ['error', 'Algo salió mal', 'line-md:close-circle', 'alert'],
  ] as const)('renders %s tone semantics and presentation', (tone, title, icon, role) => {
    const fixture = TestBed.createComponent(App);
    const notifications = TestBed.inject(NotificationService);
    fixture.detectChanges();

    notifications.show('Mensaje de prueba', { tone });
    fixture.detectChanges();

    const feedback = fixture.nativeElement.querySelector('.app-notification') as HTMLElement;
    expect(feedback.dataset['tone']).toBe(tone);
    expect(feedback.querySelector('.notification-announcement')?.getAttribute('role')).toBe(role);
    expect(feedback.querySelector('.notification-announcement')?.getAttribute('aria-live')).toBeNull();
    expect(feedback.querySelector('.notification-title')?.textContent).toContain(title);
    expect((feedback.querySelector('.notification-icon iconify-icon') as HTMLElement & { icon: string }).icon).toBe(icon);
  });

  it('renders timer progress, pauses it on focus, and replays entry for a replacement', () => {
    const fixture = TestBed.createComponent(App);
    const notifications = TestBed.inject(NotificationService);
    fixture.detectChanges();
    notifications.show('Primera', { action: 'Abrir', duration: 2400 });
    fixture.detectChanges();

    const first = fixture.nativeElement.querySelector('.app-notification') as HTMLElement;
    const action = first.querySelector('.notification-action') as HTMLButtonElement;
    expect(first.style.getPropertyValue('--notification-duration')).toBe('2400ms');
    expect(first.querySelector('.notification-progress')).not.toBeNull();
    action.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
    fixture.detectChanges();
    expect(first.classList).toContain('is-paused');

    notifications.show('Segunda', { duration: 900 });
    fixture.detectChanges();
    const replacement = fixture.nativeElement.querySelector('.app-notification') as HTMLElement;

    expect(replacement).not.toBe(first);
    expect(replacement.textContent).toContain('Segunda');
    expect(replacement.style.getPropertyValue('--notification-duration')).toBe('900ms');
    expect(replacement.classList).not.toContain('is-paused');

    first.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: null }));
    fixture.detectChanges();
    expect(replacement.classList).not.toContain('is-paused');
  });

  it('stacks a deployment update above a toast without covering either action', () => {
    const fixture = TestBed.createComponent(App);
    const notifications = TestBed.inject(NotificationService);
    updateAvailable.set(true);
    notifications.warning('Hay cambios pendientes.', 'Revisar');
    fixture.detectChanges();

    const overlays = fixture.nativeElement.querySelector('.app-overlays') as HTMLElement;
    expect(overlays.querySelector('.deployment-update button')).not.toBeNull();
    expect(overlays.querySelector('.notification-action')).not.toBeNull();
    expect(overlays.children.length).toBe(2);
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

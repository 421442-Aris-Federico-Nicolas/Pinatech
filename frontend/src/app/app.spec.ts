import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { afterEach, vi } from 'vitest';
import { App } from './app';
import { routes } from './app.routes';
import { customerGuard } from './core/guards/customer.guard';
import { DeploymentVersionService } from './core/deployment/deployment-version.service';
import { NotificationService } from './core/notifications/notification.service';

@Component({ template: '<h1>Catálogo</h1><input aria-label="Buscar">' })
class TestCatalogPage {}

describe('App', () => {
  const mobileNavBreakpoint = '(max-width: 960px)';
  const breakpointState = new BehaviorSubject<BreakpointState>({
    matches: false,
    breakpoints: { [mobileNavBreakpoint]: false },
  });
  const breakpointObserver = { observe: vi.fn(() => breakpointState.asObservable()) };
  const updateAvailable = signal(false);
  const reload = vi.fn();

  afterEach(() => vi.useRealTimers());

  beforeEach(async () => {
    updateAvailable.set(false);
    reload.mockReset();
    breakpointState.next({ matches: false, breakpoints: { [mobileNavBreakpoint]: false } });
    breakpointObserver.observe.mockClear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([
        { path: 'catalog', component: TestCatalogPage },
        { path: 'products/:id', component: TestCatalogPage },
      ]),
      { provide: BreakpointObserver, useValue: breakpointObserver },
      { provide: DeploymentVersionService, useValue: { updateAvailable, reload } }],
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
    expect(footer.textContent).toContain('Córdoba, Argentina');
    expect(footer.textContent).toContain(`© ${new Date().getFullYear()} Pinatech`);
    expect(footer.querySelector('.footer-nav a[href="/"]')?.textContent).toBe('Inicio');
    expect(footer.querySelector('.footer-nav a[href="/catalog"]')?.textContent).toBe('Catálogo');
    expect(footer.querySelector('.footer-nav a[href="/cart"]')?.textContent).toBe('Carrito');
    expect(instagram.target).toBe('_blank');
    expect(instagram.rel).toContain('noopener');
    expect(instagram.querySelector('iconify-icon')?.getAttribute('icon')).toBe('mdi:instagram');
  });

  it('renders a continuous commercial marquee with an inaccessible visual duplicate', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    const marquee = fixture.nativeElement.querySelector('.utility-bar') as HTMLElement;
    const groups = marquee.querySelectorAll('.marquee-group');

    expect(marquee.getAttribute('aria-label')).toBe('Información comercial');
    expect(groups).toHaveLength(2);
    expect(groups[0].textContent).toContain('Catálogo de tecnología');
    expect(groups[0].textContent).toContain('Servicio técnico en Córdoba');
    expect(groups[0].textContent).toContain('Mercado Pago');
    expect(groups[0].hasAttribute('aria-hidden')).toBe(false);
    expect(groups[1].getAttribute('aria-hidden')).toBe('true');
    expect(marquee.querySelector('.marquee-toggle')).toBeNull();
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

  it('keeps the notification shell while replacing announcement content and preserving focus pause', () => {
    const fixture = TestBed.createComponent(App);
    const notifications = TestBed.inject(NotificationService);
    fixture.detectChanges();
    notifications.show('Primera', { action: 'Abrir', duration: 2400 });
    fixture.detectChanges();

    const first = fixture.nativeElement.querySelector('.app-notification') as HTMLElement;
    const firstAnnouncement = first.querySelector('.notification-announcement');
    const action = first.querySelector('.notification-action') as HTMLButtonElement;
    expect(first.querySelector('.notification-progress')).toBeNull();
    action.focus();
    fixture.detectChanges();
    expect(first.classList).toContain('is-paused');

    notifications.show('Segunda', { action: 'Continuar', duration: 900 });
    fixture.detectChanges();
    const replacement = fixture.nativeElement.querySelector('.app-notification') as HTMLElement;

    expect(replacement).toBe(first);
    expect(replacement.querySelector('.notification-announcement')).not.toBe(firstAnnouncement);
    expect(replacement.querySelector('.notification-progress')).toBeNull();
    expect(replacement.textContent).toContain('Segunda');
    expect(replacement.classList).toContain('is-paused');

    replacement.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: null }));
    fixture.detectChanges();
    expect(replacement.classList).not.toContain('is-paused');
  });

  it('clears an orphaned focus pause when an action is replaced and gives the replacement a full timer', () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(App);
    const notifications = TestBed.inject(NotificationService);
    fixture.detectChanges();
    notifications.show('Primera', { action: 'Abrir', duration: 1000 });
    fixture.detectChanges();

    const action = fixture.nativeElement.querySelector('.notification-action') as HTMLButtonElement;
    action.focus();
    fixture.detectChanges();
    expect(notifications.notification()?.paused).toBe(true);

    notifications.show('Segunda', { duration: 1200 });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.notification-action')).toBeNull();
    expect(notifications.notification()).toEqual(expect.objectContaining({ message: 'Segunda', paused: false, exiting: false }));
    vi.advanceTimersByTime(1199);
    expect(notifications.notification()?.exiting).toBe(false);
    vi.advanceTimersByTime(1);
    expect(notifications.notification()?.exiting).toBe(true);
  });

  it('makes only a closed mobile nav inert and hidden from assistive technology', () => {
    breakpointState.next({ matches: true, breakpoints: { [mobileNavBreakpoint]: true } });
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    const nav = fixture.nativeElement.querySelector('#main-nav') as HTMLElement;
    const toggle = fixture.nativeElement.querySelector('#main-nav-toggle') as HTMLButtonElement;
    expect(nav.hasAttribute('inert')).toBe(true);
    expect(nav.getAttribute('aria-hidden')).toBe('true');
    expect(toggle.getAttribute('aria-label')).toBe('Abrir menú');

    toggle.click();
    fixture.detectChanges();
    expect(nav.hasAttribute('inert')).toBe(false);
    expect(nav.hasAttribute('aria-hidden')).toBe(false);
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(toggle.getAttribute('aria-label')).toBe('Cerrar menú');
  });

  it('keeps the desktop nav accessible even while menuOpen is false', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    const nav = fixture.nativeElement.querySelector('#main-nav') as HTMLElement;
    expect(fixture.componentInstance.menuOpen()).toBe(false);
    expect(nav.hasAttribute('inert')).toBe(false);
    expect(nav.hasAttribute('aria-hidden')).toBe(false);
    expect(breakpointObserver.observe).toHaveBeenCalledWith(mobileNavBreakpoint);
  });

  it('closes the mobile nav on Escape and restores focus to its stable toggle', () => {
    breakpointState.next({ matches: true, breakpoints: { [mobileNavBreakpoint]: true } });
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    const toggle = fixture.nativeElement.querySelector('#main-nav-toggle') as HTMLButtonElement;
    toggle.click();
    fixture.detectChanges();
    const nav = fixture.nativeElement.querySelector('#main-nav') as HTMLElement;
    (nav.querySelector('.nav-link') as HTMLAnchorElement).focus();
    nav.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
    fixture.detectChanges();

    expect(fixture.componentInstance.menuOpen()).toBe(false);
    expect(nav.hasAttribute('inert')).toBe(true);
    expect(document.activeElement).toBe(toggle);
  });

  it('closes the mobile nav on Escape while focus remains on the toggle', () => {
    breakpointState.next({ matches: true, breakpoints: { [mobileNavBreakpoint]: true } });
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    const toggle = fixture.nativeElement.querySelector('#main-nav-toggle') as HTMLButtonElement;
    const nav = fixture.nativeElement.querySelector('#main-nav') as HTMLElement;
    toggle.focus();
    toggle.click();
    fixture.detectChanges();
    toggle.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
    fixture.detectChanges();

    expect(fixture.componentInstance.menuOpen()).toBe(false);
    expect(nav.hasAttribute('inert')).toBe(true);
    expect(document.activeElement).toBe(toggle);
  });

  it('returns focus to the toggle when a current-route mobile nav click is skipped', async () => {
    breakpointState.next({ matches: true, breakpoints: { [mobileNavBreakpoint]: true } });
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    fixture.detectChanges();
    await router.navigateByUrl('/catalog');
    fixture.detectChanges();

    const toggle = fixture.nativeElement.querySelector('#main-nav-toggle') as HTMLButtonElement;
    toggle.click();
    fixture.detectChanges();
    const nav = fixture.nativeElement.querySelector('#main-nav') as HTMLElement;
    const currentLink = nav.querySelector('a[href="/catalog"]') as HTMLAnchorElement;
    currentLink.focus();
    currentLink.click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.componentInstance.menuOpen()).toBe(false);
    expect(nav.hasAttribute('inert')).toBe(true);
    expect(document.activeElement).toBe(toggle);
  });

  it('handles rapid toggles and lets navigation close without overriding heading focus', async () => {
    breakpointState.next({ matches: true, breakpoints: { [mobileNavBreakpoint]: true } });
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    fixture.detectChanges();
    await router.navigateByUrl('/catalog');
    fixture.detectChanges();

    const toggle = fixture.nativeElement.querySelector('#main-nav-toggle') as HTMLButtonElement;
    toggle.click();
    toggle.click();
    toggle.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.menuOpen()).toBe(true);

    await router.navigateByUrl('/products/1');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.componentInstance.menuOpen()).toBe(false);
    expect((fixture.nativeElement.querySelector('#main-nav') as HTMLElement).hasAttribute('inert')).toBe(true);
    expect(document.activeElement).toBe(fixture.nativeElement.querySelector('h1'));
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

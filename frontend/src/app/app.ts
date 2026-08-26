import { DOCUMENT } from '@angular/common';
import { Component, CUSTOM_ELEMENTS_SCHEMA, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { NavigationCancel, NavigationEnd, NavigationError, NavigationSkipped, NavigationStart, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from './core/auth/auth.service';
import { CartService } from './core/cart/cart.service';
import { NotificationService } from './core/notifications/notification.service';

@Component({
  selector: 'app-root',
  imports: [MatButtonModule, MatToolbarModule, RouterLink, RouterLinkActive, RouterOutlet],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  readonly auth = inject(AuthService);
  readonly cart = inject(CartService);
  readonly menuOpen = signal(false);
  readonly catalogActive = signal(false);
  readonly currentYear = new Date().getFullYear();
  readonly navigating = signal(false);
  readonly loggingOut = signal(false);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);
  readonly notifications = inject(NotificationService);
  private currentPath: string | null = null;

  constructor() {
    this.router.events.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((event) => {
      if (event instanceof NavigationStart) this.navigating.set(true);
      if (event instanceof NavigationEnd || event instanceof NavigationCancel || event instanceof NavigationError || event instanceof NavigationSkipped) this.navigating.set(false);
      if (!(event instanceof NavigationEnd)) return;
      const path = new URL(event.urlAfterRedirects, this.document.baseURI).pathname;
      const previousPath = this.currentPath;
      this.currentPath = path;
      this.catalogActive.set(path === '/catalog' || path.startsWith('/products/'));
      this.menuOpen.set(false);
      if (previousPath === null || previousPath === path) return;
      queueMicrotask(() => {
        const content = this.document.getElementById('main-content');
        const heading = content?.querySelector<HTMLElement>('h1');
        if (heading) heading.tabIndex = -1;
        (heading ?? content)?.focus();
      });
    });
  }

  logout(): void {
    if (this.loggingOut()) return;
    this.loggingOut.set(true);
    this.auth.logout().pipe(finalize(() => this.loggingOut.set(false)), takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.notifications.success('Sesión cerrada correctamente.');
      void this.router.navigateByUrl('/login');
    });
  }

  notificationFocusOut(event: FocusEvent): void {
    const outlet = event.currentTarget as HTMLElement | null;
    if (!outlet?.contains(event.relatedTarget as Node | null)) this.notifications.resume('focus');
  }
}

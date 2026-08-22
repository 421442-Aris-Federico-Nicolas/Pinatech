import { DOCUMENT } from '@angular/common';
import { Component, CUSTOM_ELEMENTS_SCHEMA, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from './core/auth/auth.service';
import { CartService } from './core/cart/cart.service';

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
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);
  private currentPath: string | null = null;

  constructor() {
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((event) => {
      const path = new URL(event.urlAfterRedirects, this.document.baseURI).pathname;
      const previousPath = this.currentPath;
      this.currentPath = path;
      this.catalogActive.set(path === '/catalog' || path.startsWith('/products/'));
      this.menuOpen.set(false);
      if (previousPath === path) return;
      queueMicrotask(() => {
        const content = this.document.getElementById('main-content');
        const heading = content?.querySelector<HTMLElement>('h1');
        if (heading) heading.tabIndex = -1;
        (heading ?? content)?.focus();
      });
    });
  }

  logout(): void {
    this.auth.logout().subscribe(() => void this.router.navigateByUrl('/login'));
  }
}

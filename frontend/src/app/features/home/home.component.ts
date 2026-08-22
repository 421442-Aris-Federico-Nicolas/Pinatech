import { ChangeDetectionStrategy, Component, CUSTOM_ELEMENTS_SCHEMA, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { CartService } from '../../core/cart/cart.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';
import { AppProductCardComponent } from '../../shared/ui/product-card/app-product-card.component';
import { CatalogService, Product, ProductVariant } from '../catalog/catalog.service';

@Component({
  selector: 'app-home',
  imports: [AppButtonDirective, AppProductCardComponent, RouterLink],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent {
  private readonly catalog = inject(CatalogService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);
  protected readonly cart = inject(CartService);

  protected readonly featured = signal<Product[]>([]);
  protected readonly isLoading = signal(true);
  protected readonly error = signal(false);

  constructor() {
    this.loadFeatured();
  }

  protected loadFeatured(): void {
    this.isLoading.set(true);
    this.error.set(false);

    this.catalog
      .getProducts({ search: '', categoryId: null, brandId: null, minPrice: null, maxPrice: null }, 0)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isLoading.set(false)),
      )
      .subscribe({
        next: (page) => {
          this.featured.set(page.content.slice(0, 6));
        },
        error: () => this.error.set(true),
      });
  }

  protected add(product: Product, variant: ProductVariant): void {
    if (!variant.inStock) { this.notifications.warning('El color seleccionado no tiene stock disponible.'); return; }
    const result = this.cart.add(product, variant);
    const notification = result.added === 0
      ? this.notifications.warning(`Ya tenés las ${result.limit} ${result.limit === 1 ? 'unidad disponible' : 'unidades disponibles'} para este color en el carrito.`, 'Ver carrito')
      : this.notifications.success(`${product.name} en color ${variant.colorName} se agregó al carrito.`, 'Ver carrito');
    notification.onAction().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => void this.router.navigateByUrl('/cart'));
  }
}

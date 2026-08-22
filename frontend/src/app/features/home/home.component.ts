import { ChangeDetectionStrategy, Component, CUSTOM_ELEMENTS_SCHEMA, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { CartService } from '../../core/cart/cart.service';
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
  protected readonly auth = inject(AuthService);
  protected readonly cart = inject(CartService);

  protected readonly featured = signal<Product[]>([]);
  protected readonly isLoading = signal(true);
  protected readonly error = signal(false);
  protected readonly feedback = signal('');

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
    if (!variant.inStock) { this.announce('El color seleccionado no tiene stock disponible.'); return; }
    this.cart.add(product, variant);
    this.announce(`${product.name} en color ${variant.colorName} se agregó al carrito.`);
  }

  private announce(message: string): void {
    this.feedback.set('');
    queueMicrotask(() => this.feedback.set(message));
  }
}

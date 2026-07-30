import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, CUSTOM_ELEMENTS_SCHEMA, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { CartService } from '../../core/cart/cart.service';
import { resolveApiContentUrl } from '../../core/utils/api-content-url';
import { CatalogService, Product, ProductVariant } from '../catalog/catalog.service';

@Component({
  selector: 'app-home',
  imports: [DecimalPipe, FormsModule, MatButtonModule, MatCardModule, RouterLink],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent {
  protected readonly imageUrl = resolveApiContentUrl;
  private readonly catalog = inject(CatalogService);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);
  protected readonly cart = inject(CartService);

  protected readonly featured = signal<Product[]>([]);
  protected readonly isLoading = signal(true);
  protected readonly error = signal(false);
  protected readonly feedback = signal('');
  protected readonly selectedVariants = signal<Record<number, number>>({});

  constructor() {
    this.loadFeatured();
  }

  protected loadFeatured(): void {
    this.isLoading.set(true);
    this.error.set(false);

    this.catalog
      .getProducts({ search: '', categoryId: null, brandId: null, minPrice: null, maxPrice: null }, 0)
      .pipe(
        takeUntilDestroyed(),
        finalize(() => this.isLoading.set(false)),
      )
      .subscribe({
        next: (page) => {
          const products = page.content.slice(0, 6);
          this.featured.set(products);
          this.selectedVariants.set(Object.fromEntries(products.map((product) => [product.id, product.variants.find((variant) => variant.inStock)?.id ?? product.variants[0]?.id])));
        },
        error: () => this.error.set(true),
      });
  }

  protected add(product: Product): void {
    const variant = this.selectedVariant(product);
    if (!variant?.inStock) { this.feedback.set('El color seleccionado no tiene stock disponible.'); return; }
    this.cart.add(product, variant);
    this.feedback.set(`${product.name} en color ${variant.colorName} se agregó al carrito.`);
  }

  protected selectedVariant(product: Product): ProductVariant | undefined {
    const selectedId = this.selectedVariants()[product.id];
    return product.variants.find((variant) => variant.id === selectedId) ?? product.variants.find((variant) => variant.inStock) ?? product.variants[0];
  }

  protected selectVariant(productId: number, variantId: number): void { this.selectedVariants.update((selected) => ({ ...selected, [productId]: variantId })); }
  protected openProduct(productId: number): void { void this.router.navigate(['/products', productId]); }
}

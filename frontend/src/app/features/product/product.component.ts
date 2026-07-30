import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { CartService } from '../../core/cart/cart.service';
import { resolveApiContentUrl } from '../../core/utils/api-content-url';
import { CatalogService, Product } from '../catalog/catalog.service';

@Component({
  imports: [DecimalPipe, MatButtonModule, RouterLink],
  templateUrl: './product.component.html',
  styleUrl: './product.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductComponent {
  readonly imageUrl = resolveApiContentUrl;
  private readonly route = inject(ActivatedRoute);
  private readonly catalog = inject(CatalogService);
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly destroyRef = inject(DestroyRef);
  readonly cart = inject(CartService);
  readonly product = signal<Product | null>(null);
  readonly quantity = signal(1);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly feedback = signal('');

  constructor() {
    this.destroyRef.onDestroy(() => this.meta.updateTag({ name: 'description', content: 'Catálogo de hardware y tecnología de Pinatech.' }));
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isInteger(id) || id <= 0) {
      this.loading.set(false);
      this.error.set(true);
      return;
    }
    this.catalog.product(id).pipe(takeUntilDestroyed()).subscribe({
      next: (product) => {
        this.product.set(product);
        this.loading.set(false);
        this.title.setTitle(`${product.name} | Pinatech`);
        this.meta.updateTag({ name: 'description', content: product.description.slice(0, 155) || `${product.name} en Pinatech.` });
      },
      error: () => { this.loading.set(false); this.error.set(true); },
    });
  }

  changeQuantity(change: number): void { this.quantity.update((quantity) => Math.min(99, Math.max(1, quantity + change))); }

  addToCart(): void {
    const product = this.product();
    if (!product) return;
    this.cart.add(product, this.quantity());
    this.feedback.set(`${this.quantity()} ${this.quantity() === 1 ? 'unidad agregada' : 'unidades agregadas'} al carrito.`);
  }
}

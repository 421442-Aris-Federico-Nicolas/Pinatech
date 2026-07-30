import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { CartService } from '../../core/cart/cart.service';
import { resolveApiContentUrl } from '../../core/utils/api-content-url';
import { CatalogService, Product, ProductVariant } from '../catalog/catalog.service';

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
  readonly imageIndex = signal(0);
  readonly currentImage = computed(() => this.product()?.images[this.imageIndex()] ?? null);
  readonly quantity = signal(1);
  readonly selectedVariantId = signal<number | null>(null);
  readonly selectedVariant = computed<ProductVariant | null>(() => this.product()?.variants.find((variant) => variant.id === this.selectedVariantId()) ?? null);
  readonly priceWithoutNationalTax = computed(() => (this.product()?.price ?? 0) / 1.105);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly feedback = signal('');
  readonly highlightedSpecifications = computed(() => this.product()?.specifications.filter((item) => item.highlighted) ?? []);
  readonly specificationGroups = computed(() => {
    const groups = new Map<string, NonNullable<Product['specifications']>>();
    for (const specification of this.product()?.specifications ?? []) {
      const group = groups.get(specification.groupName) ?? [];
      group.push(specification);
      groups.set(specification.groupName, group);
    }
    return Array.from(groups, ([name, specifications]) => ({ name, specifications }));
  });

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
        this.imageIndex.set(0);
        this.selectedVariantId.set(product.variants.find((variant) => variant.inStock)?.id ?? product.variants[0]?.id ?? null);
        this.loading.set(false);
        this.title.setTitle(`${product.name} | Pinatech`);
        this.meta.updateTag({ name: 'description', content: product.description.slice(0, 155) || `${product.name} en Pinatech.` });
      },
      error: () => { this.loading.set(false); this.error.set(true); },
    });
  }

  changeQuantity(change: number): void { this.quantity.update((quantity) => Math.min(99, Math.max(1, quantity + change))); }

  changeImage(change: number): void {
    const total = this.product()?.images.length ?? 0;
    if (total < 2) return;
    this.imageIndex.update((index) => (index + change + total) % total);
  }

  selectImage(index: number): void { this.imageIndex.set(index); }
  selectVariant(variantId: number): void { this.selectedVariantId.set(variantId); this.feedback.set(''); }

  addToCart(): void {
    const product = this.product();
    const variant = this.selectedVariant();
    if (!product || !variant?.inStock) return;
    this.cart.add(product, variant, this.quantity());
    this.feedback.set(`${this.quantity()} ${this.quantity() === 1 ? 'unidad agregada' : 'unidades agregadas'} en color ${variant.colorName}.`);
  }
}

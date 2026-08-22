import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CartService } from '../../core/cart/cart.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { resolveApiContentUrl } from '../../core/utils/api-content-url';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';
import { CatalogService, Product, ProductVariant } from '../catalog/catalog.service';

@Component({
  imports: [AppButtonDirective, CurrencyPipe, RouterLink],
  templateUrl: './product.component.html',
  styleUrl: './product.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductComponent {
  readonly imageUrl = resolveApiContentUrl;
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly catalog = inject(CatalogService);
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly destroyRef = inject(DestroyRef);
  private readonly notifications = inject(NotificationService);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);
  private readonly productId = Number(this.route.snapshot.paramMap.get('id'));
  readonly cart = inject(CartService);
  readonly product = signal<Product | null>(null);
  readonly imageIndex = signal(0);
  readonly currentImage = computed(() => this.product()?.images[this.imageIndex()] ?? null);
  readonly quantity = signal(1);
  readonly selectedVariantId = signal<number | null>(null);
  readonly selectedVariant = computed<ProductVariant | null>(() => this.product()?.variants.find((variant) => variant.id === this.selectedVariantId()) ?? null);
  readonly quantityInCart = computed(() => this.cart.items().find((item) => item.variant.id === this.selectedVariantId())?.quantity ?? 0);
  readonly maxAddable = computed(() => {
    const variant = this.selectedVariant();
    return variant ? Math.max(0, this.cart.stockLimit(variant) - this.quantityInCart()) : 0;
  });
  readonly priceWithoutNationalTax = computed(() => (this.product()?.price ?? 0) / 1.105);
  readonly loading = signal(true);
  readonly error = signal<'not-found' | 'request' | null>(null);
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
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const product = this.product();
      if (!product) return;
      const variantId = this.positiveNumber(params.get('variant'));
      this.selectedVariantId.set(product.variants.find((variant) => variant.id === variantId)?.id ?? product.variants.find((variant) => variant.inStock)?.id ?? product.variants[0]?.id ?? null);
    });

    if (!Number.isInteger(this.productId) || this.productId <= 0) {
      this.loading.set(false);
      this.showError('not-found');
      return;
    }
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.catalog.product(this.productId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (product) => {
        this.product.set(product);
        this.imageIndex.set(0);
        const requestedVariant = this.positiveNumber(this.route.snapshot.queryParamMap.get('variant'));
        this.selectedVariantId.set(product.variants.find((variant) => variant.id === requestedVariant)?.id ?? product.variants.find((variant) => variant.inStock)?.id ?? product.variants[0]?.id ?? null);
        this.loading.set(false);
        this.title.setTitle(`${product.name} | Pinatech`);
        this.meta.updateTag({ name: 'description', content: product.description.slice(0, 155) || `${product.name} en Pinatech.` });
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        this.showError(error.status === 404 ? 'not-found' : 'request');
      },
    });
  }

  changeQuantity(change: number): void { this.quantity.update((quantity) => Math.min(Math.max(1, this.maxAddable()), Math.max(1, quantity + change))); }

  changeImage(change: number): void {
    const total = this.product()?.images.length ?? 0;
    if (total < 2) return;
    this.imageIndex.update((index) => (index + change + total) % total);
  }

  selectImage(index: number): void { this.imageIndex.set(index); }
  selectVariant(variantId: number): void {
    this.selectedVariantId.set(variantId);
    this.quantity.set(1);
    void this.router.navigate([], { relativeTo: this.route, queryParams: { variant: variantId }, queryParamsHandling: 'merge' });
  }

  selectAdjacentVariant(currentId: number, direction: -1 | 1): void {
    const variants = this.product()?.variants.filter((variant) => variant.inStock) ?? [];
    const currentIndex = variants.findIndex((variant) => variant.id === currentId);
    if (currentIndex < 0 || variants.length < 2) return;
    const target = variants[(currentIndex + direction + variants.length) % variants.length];
    this.selectVariant(target.id);
    queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>(`[data-variant-id="${target.id}"]`)?.focus());
  }

  addToCart(): void {
    const product = this.product();
    const variant = this.selectedVariant();
    if (!product || !variant?.inStock) return;
    const result = this.cart.add(product, variant, this.quantity());
    const message = result.added === 0
      ? `Ya tenés todas las unidades disponibles para este color en el carrito (${result.limit}).`
      : result.capped
        ? `Se ${result.added === 1 ? 'agregó 1 unidad' : `agregaron ${result.added} unidades`}; solo hay ${result.limit} disponibles para este color.`
        : `${result.added === 1 ? '1 unidad agregada' : `${result.added} unidades agregadas`} en color ${variant.colorName}.`;
    const notification = result.capped
      ? this.notifications.warning(message, 'Ver carrito')
      : this.notifications.success(message, 'Ver carrito');
    notification.onAction().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => void this.router.navigateByUrl('/cart'));
    this.quantity.set(1);
  }

  private positiveNumber(value: string | null): number | null {
    const parsed = Number(value);
    return value !== null && Number.isInteger(parsed) && parsed > 0 ? parsed : null;
  }

  private showError(type: 'not-found' | 'request'): void {
    this.error.set(type);
    const title = type === 'not-found' ? 'Producto no encontrado | Pinatech' : 'No pudimos cargar el producto | Pinatech';
    const description = type === 'not-found' ? 'El producto no existe o ya no está publicado.' : 'No pudimos cargar el producto. Intentá nuevamente.';
    this.title.setTitle(title);
    this.meta.updateTag({ name: 'description', content: description });
  }
}

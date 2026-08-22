import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, CUSTOM_ELEMENTS_SCHEMA, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, finalize, forkJoin, Subject, Subscription } from 'rxjs';
import { CartService } from '../../core/cart/cart.service';
import { resolveApiContentUrl } from '../../core/utils/api-content-url';
import { Brand, CatalogFilters, CatalogService, CatalogSort, Category, Page, Product, ProductVariant } from './catalog.service';

const SORTS: CatalogSort[] = ['name,asc', 'name,desc', 'price,asc', 'price,desc'];

@Component({
  imports: [CurrencyPipe, FormsModule, MatButtonModule, MatCardModule, RouterLink],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './catalog.component.html',
  styleUrl: './catalog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CatalogComponent {
  readonly imageUrl = resolveApiContentUrl;
  private readonly service = inject(CatalogService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly searchChanges = new Subject<string>();
  private request?: Subscription;

  readonly cart = inject(CartService);
  readonly filters: CatalogFilters = { search: '', categoryId: null, brandId: null, minPrice: null, maxPrice: null };
  readonly page = signal<Page<Product> | null>(null);
  readonly categories = signal<Category[]>([]);
  readonly brands = signal<Brand[]>([]);
  readonly sort = signal<CatalogSort>('name,asc');
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly optionsError = signal(false);
  readonly filtersOpen = signal(false);
  readonly feedback = signal('');
  readonly priceError = signal('');
  readonly selectedVariants = signal<Record<number, number>>({});

  constructor() {
    this.loadOptions();

    this.searchChanges.pipe(debounceTime(350), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.applyFilters());

    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.readParams(params);
      this.loadPage(this.pageNumber(params));
    });
  }

  searchChanged(value: string): void { this.searchChanges.next(value); }

  applyFilters(page = 1): void {
    if (!this.validatePrices()) return;
    const queryParams: Record<string, string | number> = {};
    const search = this.filters.search.trim();
    if (search) queryParams['search'] = search;
    if (this.filters.categoryId !== null) queryParams['category'] = this.filters.categoryId;
    if (this.filters.brandId !== null) queryParams['brand'] = this.filters.brandId;
    if (this.filters.minPrice !== null && this.filters.minPrice >= 0) queryParams['minPrice'] = this.filters.minPrice;
    if (this.filters.maxPrice !== null && this.filters.maxPrice >= 0) queryParams['maxPrice'] = this.filters.maxPrice;
    if (this.sort() !== 'name,asc') queryParams['sort'] = this.sort();
    if (page > 1) queryParams['page'] = page;
    void this.router.navigate([], { relativeTo: this.route, queryParams });
  }

  clearFilters(): void {
    Object.assign(this.filters, { search: '', categoryId: null, brandId: null, minPrice: null, maxPrice: null });
    this.sort.set('name,asc');
    this.priceError.set('');
    this.applyFilters();
  }

  add(product: Product): void {
    const variant = this.selectedVariant(product);
    if (!variant?.inStock) { this.announce('El color seleccionado no tiene stock disponible.'); return; }
    this.cart.add(product, variant);
    this.announce(`${product.name} en color ${variant.colorName} se agregó al carrito.`);
  }

  selectedVariant(product: Product): ProductVariant | undefined {
    const selectedId = this.selectedVariants()[product.id];
    return product.variants.find((variant) => variant.id === selectedId) ?? product.variants.find((variant) => variant.inStock) ?? product.variants[0];
  }

  selectVariant(productId: number, variantId: number): void { this.selectedVariants.update((selected) => ({ ...selected, [productId]: variantId })); }

  retry(): void { this.loadPage(this.page()?.number ?? 0); }

  retryOptions(): void { this.loadOptions(); }

  private loadOptions(): void {
    this.optionsError.set(false);
    forkJoin({ categories: this.service.categories(), brands: this.service.brands() })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ categories, brands }) => { this.categories.set(categories); this.brands.set(brands); },
        error: () => this.optionsError.set(true),
      });
  }

  private loadPage(page: number): void {
    this.request?.unsubscribe();
    this.loading.set(true);
    this.error.set(false);
    this.page.set(null);
    this.request = this.service.getProducts(this.filters, page, this.sort())
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (result) => { this.page.set(result); this.selectedVariants.update((selected) => ({ ...Object.fromEntries(result.content.map((product) => [product.id, product.variants.find((variant) => variant.inStock)?.id ?? product.variants[0]?.id])), ...selected })); }, error: () => { this.page.set(null); this.error.set(true); } });
  }

  private readParams(params: ParamMap): void {
    this.filters.search = params.get('search') ?? '';
    this.filters.categoryId = this.positiveNumber(params.get('category'));
    this.filters.brandId = this.positiveNumber(params.get('brand'));
    this.filters.minPrice = this.nonNegativeNumber(params.get('minPrice'));
    this.filters.maxPrice = this.nonNegativeNumber(params.get('maxPrice'));
    const sort = params.get('sort');
    this.sort.set(SORTS.includes(sort as CatalogSort) ? sort as CatalogSort : 'name,asc');
  }

  private pageNumber(params: ParamMap): number {
    const page = Number(params.get('page'));
    return Number.isInteger(page) && page > 0 ? page - 1 : 0;
  }

  private positiveNumber(value: string | null): number | null {
    const parsed = Number(value);
    return value !== null && Number.isInteger(parsed) && parsed > 0 ? parsed : null;
  }

  private nonNegativeNumber(value: string | null): number | null {
    const parsed = Number(value);
    return value !== null && Number.isFinite(parsed) && parsed >= 0 ? parsed : null;
  }

  private announce(message: string): void {
    this.feedback.set('');
    queueMicrotask(() => this.feedback.set(message));
  }

  private validatePrices(): boolean {
    const minimum = this.filters.minPrice;
    const maximum = this.filters.maxPrice;
    const message = minimum !== null && minimum < 0 || maximum !== null && maximum < 0
      ? 'Los precios deben ser mayores o iguales a cero.'
      : minimum !== null && maximum !== null && minimum > maximum
        ? 'El precio desde no puede superar el precio hasta.'
        : '';
    this.priceError.set(message);
    return !message;
  }
}

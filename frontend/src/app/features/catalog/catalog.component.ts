import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, forkJoin, Subject } from 'rxjs';
import { CartService } from '../../core/cart/cart.service';
import { Brand, CatalogFilters, CatalogService, Category, Page, Product } from './catalog.service';

@Component({ imports: [DecimalPipe, FormsModule, MatButtonModule, MatCardModule, MatSelectModule, RouterLink], templateUrl: './catalog.component.html', styleUrl: './catalog.component.scss', changeDetection: ChangeDetectionStrategy.OnPush })
export class CatalogComponent {
  private readonly service = inject(CatalogService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly searchChanges = new Subject<string>();
  readonly cart = inject(CartService);
  readonly filters: CatalogFilters = { search: '', categoryId: null, brandId: null, minPrice: null, maxPrice: null };
  readonly page = signal<Page<Product> | null>(null);
  readonly categories = signal<Category[]>([]);
  readonly brands = signal<Brand[]>([]);
  constructor() { forkJoin({ categories: this.service.categories(), brands: this.service.brands() }).subscribe(({ categories, brands }) => { this.categories.set(categories); this.brands.set(brands); }); this.searchChanges.pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.load()); this.load(); }
  load(page = 0): void { this.service.getProducts(this.filters, page).subscribe(result => this.page.set(result)); }
  searchChanged(value: string): void { this.searchChanges.next(value); }
  clearFilters(): void { Object.assign(this.filters, { search: '', categoryId: null, brandId: null, minPrice: null, maxPrice: null }); this.load(); }
}

import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, CUSTOM_ELEMENTS_SCHEMA, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { CatalogService, Product } from '../catalog/catalog.service';

@Component({
  selector: 'app-home',
  imports: [DecimalPipe, MatButtonModule, MatCardModule, RouterLink],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent {
  private readonly catalog = inject(CatalogService);
  protected readonly auth = inject(AuthService);

  protected readonly featured = signal<Product[]>([]);
  protected readonly isLoading = signal(true);
  protected readonly error = signal(false);
  protected readonly categoriesOpen = signal(false);

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
        next: (page) => this.featured.set(page.content.slice(0, 6)),
        error: () => this.error.set(true),
      });
  }

  protected toggleCategories(): void {
    this.categoriesOpen.update((open) => !open);
  }
}

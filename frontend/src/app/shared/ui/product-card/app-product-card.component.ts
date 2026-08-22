import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, linkedSignal, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { resolveApiContentUrl } from '../../../core/utils/api-content-url';
import { AppButtonDirective } from '../app-button.directive';
import { AppCardDirective } from '../app-card.directive';
import { AppSelectComponent, AppSelectOption } from '../select/app-select.component';

export interface ProductCardImage {
  readonly contentUrl: string;
  readonly altText: string;
}

export interface ProductCardVariant {
  readonly id: number;
  readonly colorName: string;
  readonly colorHex: string | null;
  readonly inStock: boolean;
}

export interface ProductCardProduct {
  readonly id: number;
  readonly name: string;
  readonly description: string;
  readonly price: number;
  readonly categoryName: string;
  readonly brandName: string;
  readonly images: readonly ProductCardImage[];
  readonly variants: readonly ProductCardVariant[];
}

export type ProductCardMode = 'catalog' | 'featured';

@Component({
  selector: 'app-product-card',
  imports: [AppButtonDirective, AppCardDirective, AppSelectComponent, CurrencyPipe, FormsModule, RouterLink],
  templateUrl: './app-product-card.component.html',
  styleUrl: './app-product-card.component.scss',
  host: {
    '[class.app-product-card--featured]': 'mode() === "featured"',
    '[class.app-product-card--catalog]': 'mode() === "catalog"',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppProductCardComponent {
  readonly product = input.required<ProductCardProduct>();
  readonly mode = input<ProductCardMode>('catalog');
  readonly imagePriority = input(false);
  readonly addToCart = output<ProductCardVariant>();
  protected readonly imageUrl = resolveApiContentUrl;
  protected readonly selectedVariantId = linkedSignal(() => this.initialVariant()?.id ?? null);
  protected readonly selectedVariant = computed(() => {
    const product = this.product();
    return product.variants.find((variant) => variant.id === this.selectedVariantId()) ?? this.initialVariant();
  });
  protected readonly variantOptions = computed<readonly AppSelectOption[]>(() => this.product().variants.map((variant) => ({
    value: variant.id,
    label: `${variant.colorName}${variant.inStock ? '' : ' · sin stock'}`,
    disabled: !variant.inStock,
  })));

  protected add(): void {
    const variant = this.selectedVariant();
    if (variant?.inStock) this.addToCart.emit(variant);
  }

  private initialVariant(): ProductCardVariant | undefined {
    return this.product().variants.find((variant) => variant.inStock) ?? this.product().variants[0];
  }
}

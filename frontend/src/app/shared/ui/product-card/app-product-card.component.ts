import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { bankTransferPrice } from '../../../core/payments/payment-pricing';
import { resolveApiContentUrl } from '../../../core/utils/api-content-url';
import { AppButtonDirective } from '../app-button.directive';
import { AppCardDirective } from '../app-card.directive';

export interface ProductCardImage {
  readonly contentUrl: string;
  readonly altText: string;
}

export interface ProductCardProduct {
  readonly id: number;
  readonly name: string;
  readonly price: number;
  readonly categoryName: string;
  readonly brandName: string;
  readonly images: readonly ProductCardImage[];
  readonly inStock: boolean;
}

export type ProductCardMode = 'catalog' | 'featured';

@Component({
  selector: 'app-product-card',
  imports: [AppButtonDirective, AppCardDirective, CurrencyPipe, RouterLink],
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
  protected readonly imageUrl = resolveApiContentUrl;
  protected readonly hasStock = computed(() => this.product().inStock);
  protected readonly displayedPrice = computed(() => bankTransferPrice(this.product().price).total);
}

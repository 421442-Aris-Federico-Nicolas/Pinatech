import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, CUSTOM_ELEMENTS_SCHEMA, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { CatalogService, Product } from '../catalog/catalog.service';
import { CartService } from '../../core/cart/cart.service';

@Component({ imports: [DecimalPipe, MatButtonModule, RouterLink], schemas: [CUSTOM_ELEMENTS_SCHEMA], templateUrl: './product.component.html', styleUrl: './product.component.scss', changeDetection: ChangeDetectionStrategy.OnPush })
export class ProductComponent {
  private readonly route = inject(ActivatedRoute); private readonly catalog = inject(CatalogService); readonly cart = inject(CartService);
  readonly product = signal<Product | null>(null); readonly quantity = signal(1); readonly error = signal(false);
  constructor() { const id = Number(this.route.snapshot.paramMap.get('id')); if (id) this.catalog.product(id).subscribe({ next: product => this.product.set(product), error: () => this.error.set(true) }); else this.error.set(true); }
  changeQuantity(change: number): void { this.quantity.update(quantity => Math.max(1, quantity + change)); }
  addToCart(): void { const product = this.product(); if (!product) return; for (let index = 0; index < this.quantity(); index++) this.cart.add(product); }
}

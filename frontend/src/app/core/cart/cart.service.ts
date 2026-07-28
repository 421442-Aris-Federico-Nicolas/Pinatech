import { HttpClient } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';
import { Product } from '../../features/catalog/catalog.service';

export interface CartItem { product: Product; quantity: number; }

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private activeKey = this.storageKey(null);
  private readonly itemsState = signal<CartItem[]>(this.restore(this.activeKey));
  readonly items = this.itemsState.asReadonly();
  readonly count = computed(() => this.itemsState().reduce((total, item) => total + item.quantity, 0));
  readonly total = computed(() => this.itemsState().reduce((total, item) => total + item.product.price * item.quantity, 0));

  constructor() {
    effect(() => {
      const key = this.storageKey(this.auth.user()?.id ?? null);
      if (key === this.activeKey) return;
      this.persist(this.activeKey, this.itemsState());
      this.activeKey = key;
      this.itemsState.set(this.restore(key));
    });
  }

  add(product: Product): void {
    const current = this.itemsState();
    const found = current.find((item) => item.product.id === product.id);
    this.update(found ? current.map((item) => item === found ? { ...item, quantity: item.quantity + 1 } : item) : [...current, { product, quantity: 1 }]);
  }

  setQuantity(id: number, quantity: number): void {
    this.update(this.itemsState().flatMap((item) => item.product.id === id && quantity <= 0 ? [] : item.product.id === id ? [{ ...item, quantity }] : [item]));
  }

  clear(): void { this.update([]); }

  checkout() {
    return this.http.post(`${environment.apiBaseUrl}/orders`, { items: this.itemsState().map((item) => ({ productId: item.product.id, quantity: item.quantity })) });
  }

  private update(items: CartItem[]): void {
    this.itemsState.set(items);
    this.persist(this.activeKey, items);
  }

  private storageKey(userId: number | null): string {
    return userId === null ? 'pinatech-cart-guest' : `pinatech-cart-user-${userId}`;
  }

  private persist(key: string, items: CartItem[]): void {
    localStorage.setItem(key, JSON.stringify(items));
  }

  private restore(key: string): CartItem[] {
    try { return JSON.parse(localStorage.getItem(key) ?? '[]'); } catch { return []; }
  }
}

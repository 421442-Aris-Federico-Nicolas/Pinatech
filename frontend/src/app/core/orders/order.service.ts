import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';

export interface OrderItem {
  productId: number;
  variantId: number;
  productName: string;
  colorName: string;
  colorHex: string | null;
  unitPrice: number;
  quantity: number;
  subtotal: number;
}

export interface Order {
  id: number;
  status: string;
  paymentStatus: string;
  fulfillmentStatus: string;
  currency: string;
  paymentMethod: string | null;
  deliveryMethod: string | null;
  total: number;
  createdAt: string;
  reservationExpiresAt: string;
  customerName: string;
  customerEmail: string;
  items: OrderItem[];
}

@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly http = inject(HttpClient);

  mine() {
    return this.http.get<Order[]>(`${environment.apiBaseUrl}/orders/me`);
  }

  get(id: number) {
    return this.http.get<Order>(`${environment.apiBaseUrl}/orders/${id}`);
  }
}

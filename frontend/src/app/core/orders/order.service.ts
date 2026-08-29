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

export interface PickupLocation {
  code: string;
  version: string;
  name: string;
  addressLines: string[];
  locality: string;
  provinceCode: string;
  postalCode: string;
  instructions: string;
  hours: string;
}

export type PaymentMethod = 'BANK_TRANSFER' | 'MERCADO_PAGO';

export interface Order {
  id: number;
  status: string;
  paymentStatus: string;
  fulfillmentStatus: string;
  currency: string;
  paymentMethod: PaymentMethod;
  deliveryMethod: string | null;
  fulfillmentMethod: string | null;
  pickupLocation: PickupLocation | null;
  subtotal: number;
  paymentDiscount: number;
  paymentSurcharge: number;
  total: number;
  createdAt: string;
  reservationExpiresAt: string | null;
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

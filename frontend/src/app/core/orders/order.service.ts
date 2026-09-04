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
export type FulfillmentMethod = 'PICKUP' | 'DELIVERY';

export interface DeliveryAddress {
  recipientName: string;
  street: string;
  streetNumber: string;
  floorApartment: string | null;
  locality: string;
  province: string;
  provinceCode: string;
  postalCode: string;
  countryCode: string;
  reference: string | null;
}

export interface ShipmentSummary {
  status: string;
  providerStatus: string | null;
  providerSubstatus: string | null;
  carrier: string | null;
  trackingCode: string | null;
  trackingUrl: string | null;
  estimatedDeliveryAt: string | null;
  incident: boolean;
}

export interface ShipmentTracking extends ShipmentSummary {
  history: ShipmentTrackingEvent[];
}

export interface ShipmentTrackingEvent {
  status: string;
  substatus: string | null;
  occurredAt: string;
}

export interface Order {
  id: number;
  status: string;
  paymentStatus: string;
  fulfillmentStatus: string;
  currency: string;
  paymentMethod: PaymentMethod;
  deliveryMethod: string | null;
  fulfillmentMethod: FulfillmentMethod | null;
  pickupLocation: PickupLocation | null;
  subtotal: number;
  shippingCost: number;
  paymentDiscount: number;
  paymentSurcharge: number;
  total: number;
  createdAt: string;
  reservationExpiresAt: string | null;
  customerName: string;
  customerEmail: string;
  items: OrderItem[];
  deliveryAddress: DeliveryAddress | null;
  shipment: ShipmentSummary | null;
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

  tracking(orderId: number) {
    return this.http.get<ShipmentTracking>(`${environment.apiBaseUrl}/shipping/orders/${orderId}/tracking`);
  }
}

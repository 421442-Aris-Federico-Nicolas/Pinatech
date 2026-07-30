import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';

export interface CheckoutCapabilities {
  currency: string;
  orderRequestsEnabled: boolean;
  onlinePaymentsEnabled: boolean;
  deliveryQuotesEnabled: boolean;
  paymentMethods: string[];
  deliveryMethods: string[];
}

@Injectable({ providedIn: 'root' })
export class CheckoutService {
  private readonly http = inject(HttpClient);

  capabilities() {
    return this.http.get<CheckoutCapabilities>(`${environment.apiBaseUrl}/checkout/capabilities`);
  }
}

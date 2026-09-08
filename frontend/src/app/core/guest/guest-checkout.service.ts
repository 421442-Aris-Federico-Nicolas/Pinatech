import { HttpClient, HttpContext, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, of, shareReplay, switchMap, tap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MercadoPagoCheckout, ShippingQuoteItem, ShippingQuoteResponse } from '../../features/checkout/checkout.service';
import { Order, PaymentMethod, FulfillmentMethod, ShipmentTracking } from '../orders/order.service';
import { BankTransferDetails } from '../orders/bank-transfer.service';
import { GUEST_REQUEST } from '../interceptors/auth.interceptor';

export interface GuestSession {
  csrfToken: string;
  expiresAt: string;
  emailVerified: boolean;
  verifiedEmail: string | null;
}

export interface GuestCustomer {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  documentNumber: string;
}

export interface GuestDeliveryAddress {
  street: string;
  streetNumber: string;
  floorApartment: string;
  locality: string;
  province: string;
  postalCode: string;
  reference: string;
  countryCode: 'AR';
}

export interface GuestCreateOrderRequest {
  items: ShippingQuoteItem[];
  paymentMethod: PaymentMethod;
  fulfillmentMethod: FulfillmentMethod;
  pickupLocationCode: string | null;
  pickupLocationVersion: string | null;
  shippingQuoteId: string | null;
  customer: GuestCustomer;
  deliveryAddress: GuestDeliveryAddress | null;
}

export interface GuestOrderEnvelope {
  publicId: string;
  order: Order;
  accessToken: string | null;
}

const ORDER_PUBLIC_ID_PREFIX = 'pinatech-guest-order-public-id-';
const ORDER_TOKEN_PREFIX = 'pinatech-guest-order-token-';

@Injectable({ providedIn: 'root' })
export class GuestCheckoutService {
  private readonly http = inject(HttpClient);
  private readonly guestContext = new HttpContext().set(GUEST_REQUEST, true);
  private sessionRequest?: Observable<GuestSession>;
  readonly session = signal<GuestSession | null>(null);

  initialize(): Observable<GuestSession> {
    if (this.session()) return of(this.session()!);
    if (!this.sessionRequest) {
      this.sessionRequest = this.http.get<GuestSession>(`${environment.apiBaseUrl}/guest-checkout/session`, {
        context: this.guestContext,
      }).pipe(
        catchError(() => this.http.post<GuestSession>(`${environment.apiBaseUrl}/guest-checkout/sessions`, null, {
          context: this.guestContext,
        })),
        tap((session) => this.session.set(session)),
        finalize(() => this.sessionRequest = undefined),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    }
    return this.sessionRequest;
  }

  requestEmailVerification(email: string, firstName: string) {
    return this.withCsrfRetry(() => this.http.post<{ message: string }>(
      `${environment.apiBaseUrl}/guest-checkout/email-verification/request`, { email, firstName }, this.mutationOptions()));
  }

  checkEmailEligibility(email: string) {
    return this.withCsrfRetry(() => this.http.post<void>(
      `${environment.apiBaseUrl}/guest-checkout/email-eligibility`, { email }, this.mutationOptions()));
  }

  confirmEmailVerification(email: string, code: string) {
    return this.withCsrfRetry(() => this.http.post<GuestSession>(
      `${environment.apiBaseUrl}/guest-checkout/email-verification/confirm`, { email, code }, this.mutationOptions()))
      .pipe(tap((session) => this.session.set(session)));
  }

  shippingQuotes(items: ShippingQuoteItem[], customer: GuestCustomer, deliveryAddress: GuestDeliveryAddress) {
    return this.withCsrfRetry(() => this.http.post<ShippingQuoteResponse>(
      `${environment.apiBaseUrl}/guest-checkout/shipping-quotes`, { items, customer, deliveryAddress }, this.mutationOptions()));
  }

  createOrder(request: GuestCreateOrderRequest) {
    const key = this.idempotencyKey('order', this.fingerprint(request));
    return this.withCsrfRetry(() => this.http.post<GuestOrderEnvelope>(
      `${environment.apiBaseUrl}/guest-checkout/orders`, request, {
        ...this.mutationOptions(),
        headers: this.csrfHeaders().set('Idempotency-Key', key),
      })).pipe(tap((response) => this.rememberOrder(response)));
  }

  mercadoPago(publicId: string, paymentStatus: string) {
    const key = this.idempotencyKey(`payment-${publicId}`, paymentStatus);
    return this.withCsrfRetry(() => this.http.post<MercadoPagoCheckout>(
      `${environment.apiBaseUrl}/guest-orders/${encodeURIComponent(publicId)}/payments/mercado-pago`, {}, {
        ...this.mutationOptions(publicId),
        headers: this.accessHeaders(publicId).set('X-Guest-CSRF', this.csrfToken())
          .set('Idempotency-Key', key),
      }));
  }

  getOrder(publicId: string) {
    return this.http.get<GuestOrderEnvelope>(`${environment.apiBaseUrl}/guest-orders/${encodeURIComponent(publicId)}`, {
      context: this.guestContext,
      headers: this.accessHeaders(publicId),
    }).pipe(tap((response) => this.rememberOrder(response)));
  }

  bankTransfer(publicId: string) {
    return this.http.get<BankTransferDetails>(`${environment.apiBaseUrl}/guest-orders/${encodeURIComponent(publicId)}/bank-transfer`, {
      context: this.guestContext,
      headers: this.accessHeaders(publicId),
    });
  }

  uploadProof(publicId: string, file: File) {
    const body = new FormData();
    body.append('file', file);
    const fingerprint = `${file.name}|${file.type}|${file.size}|${file.lastModified}`;
    const key = this.idempotencyKey(`proof-${publicId}`, fingerprint);
    return this.withCsrfRetry(() => this.http.post<BankTransferDetails>(
      `${environment.apiBaseUrl}/guest-orders/${encodeURIComponent(publicId)}/bank-transfer/proof`, body, {
        context: this.guestContext,
        headers: this.accessHeaders(publicId).set('X-Guest-CSRF', this.csrfToken())
          .set('Idempotency-Key', key),
      }));
  }

  tracking(publicId: string) {
    return this.http.get<ShipmentTracking>(`${environment.apiBaseUrl}/guest-orders/${encodeURIComponent(publicId)}/tracking`, {
      context: this.guestContext,
      headers: this.accessHeaders(publicId),
    });
  }

  claim(publicId: string) {
    return this.withCsrfRetry(() => this.http.post<GuestOrderEnvelope>(
      `${environment.apiBaseUrl}/guest-orders/${encodeURIComponent(publicId)}/claim`, {}, {
        headers: this.accessHeaders(publicId).set('X-Guest-CSRF', this.csrfToken()),
      })).pipe(tap(() => this.clearOrderAccess(publicId)));
  }

  currentPublicId(orderId: number | null): string | null {
    if (orderId === null || !Number.isSafeInteger(orderId) || orderId <= 0) return null;
    try {
      const publicId = sessionStorage.getItem(`${ORDER_PUBLIC_ID_PREFIX}${orderId}`);
      return publicId && this.validPublicId(publicId) ? publicId : null;
    } catch { return null; }
  }

  returnPublicId(orderId: number | null, suppliedPublicId: string | null): string | null {
    if (orderId !== null && suppliedPublicId && this.validPublicId(suppliedPublicId)) {
      try { sessionStorage.setItem(`${ORDER_PUBLIC_ID_PREFIX}${orderId}`, suppliedPublicId); } catch { /* The cookie can still authorize this return. */ }
      return suppliedPublicId;
    }
    return this.currentPublicId(orderId);
  }

  captureAccessTokenFromFragment(publicId: string): void {
    const location = globalThis.location;
    const history = globalThis.history;
    if (!location?.hash || !history || !new URLSearchParams(location.hash.slice(1)).has('token')) return;
    const token = new URLSearchParams(location.hash.slice(1)).get('token');
    history.replaceState(history.state, '', `${location.pathname}${location.search}`);
    if (!this.validPublicId(publicId) || !token || !/^[A-Za-z0-9_-]{43}$/.test(token)) return;
    try { sessionStorage.setItem(`${ORDER_TOKEN_PREFIX}${publicId}`, token); } catch { /* The URL was still scrubbed. */ }
  }

  private mutationOptions(publicId?: string) {
    return {
      context: this.guestContext,
      headers: publicId
        ? this.accessHeaders(publicId).set('X-Guest-CSRF', this.csrfToken())
        : this.csrfHeaders(),
    };
  }

  private csrfHeaders(): HttpHeaders { return new HttpHeaders({ 'X-Guest-CSRF': this.csrfToken() }); }
  private csrfToken(): string { return this.session()?.csrfToken ?? ''; }

  private accessHeaders(publicId: string): HttpHeaders {
    const token = this.accessToken(publicId);
    return token ? new HttpHeaders({ 'X-Order-Access-Token': token }) : new HttpHeaders();
  }

  private accessToken(publicId: string): string | null {
    try { return sessionStorage.getItem(`${ORDER_TOKEN_PREFIX}${publicId}`); } catch { return null; }
  }

  private rememberOrder(response: GuestOrderEnvelope): void {
    if (!this.validPublicId(response.publicId) || !Number.isSafeInteger(response.order.id) || response.order.id <= 0) return;
    try {
      sessionStorage.setItem(`${ORDER_PUBLIC_ID_PREFIX}${response.order.id}`, response.publicId);
      if (response.accessToken) sessionStorage.setItem(`${ORDER_TOKEN_PREFIX}${response.publicId}`, response.accessToken);
    } catch { /* The owner cookie remains the preferred recovery mechanism. */ }
  }

  private clearOrderAccess(publicId: string): void {
    try {
      sessionStorage.removeItem(`${ORDER_TOKEN_PREFIX}${publicId}`);
      for (let index = sessionStorage.length - 1; index >= 0; index--) {
        const key = sessionStorage.key(index);
        if (key?.startsWith(ORDER_PUBLIC_ID_PREFIX) && sessionStorage.getItem(key) === publicId) sessionStorage.removeItem(key);
      }
    } catch { /* Storage can be unavailable. */ }
  }

  private withCsrfRetry<T>(request: () => Observable<T>): Observable<T> {
    return request().pipe(catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 404) return throwError(() => error);
      return this.refreshCsrf().pipe(switchMap(() => request()));
    }));
  }

  private refreshCsrf(): Observable<GuestSession> {
    return this.http.get<GuestSession>(`${environment.apiBaseUrl}/guest-checkout/session`, {
      context: this.guestContext,
    }).pipe(tap((session) => this.session.set(session)));
  }

  private idempotencyKey(scope: string, fingerprint: string): string {
    const storageKey = `pinatech-guest-${scope}-attempt`;
    try {
      const stored = JSON.parse(sessionStorage.getItem(storageKey) ?? 'null') as unknown;
      if (stored && typeof stored === 'object') {
        const value = stored as Record<string, unknown>;
        if (value['fingerprint'] === fingerprint && typeof value['key'] === 'string') return value['key'];
      }
    } catch { /* Replace malformed or unavailable storage below. */ }
    const key = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    try { sessionStorage.setItem(storageKey, JSON.stringify({ fingerprint, key })); } catch { /* In-memory request remains valid. */ }
    return key;
  }

  private fingerprint(value: unknown): string {
    const text = JSON.stringify(value);
    let hash = 2166136261;
    for (let index = 0; index < text.length; index++) hash = Math.imul(hash ^ text.charCodeAt(index), 16777619);
    return (hash >>> 0).toString(36);
  }

  private validPublicId(value: string): boolean {
    return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
  }
}

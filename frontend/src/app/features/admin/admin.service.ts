import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { Page, Product, ProductImage } from '../catalog/catalog.service';
import { CancellationReasonCode, Order } from '../../core/orders/order.service';

export interface Category { id: number; name: string; slug: string; }
export interface Brand { id: number; name: string; }
export interface Inventory { productId: number; variantId: number; colorName: string; colorHex: string | null; availableQuantity: number; reservedQuantity: number; }
export type InventoryListItem = Inventory & { productName: string; brandName: string };
export type ProductListItem = Pick<Product, 'id' | 'name' | 'slug' | 'price' | 'categoryId' | 'categoryName' | 'brandId' | 'brandName' | 'images'>;
export interface OrdersSummary {
  soldOrders: number; revenue: number; averageTicket: number; activeOrders: number;
  statusCounts: Record<string, number>;
  salesChart: Array<{ label: string; total: number; height: number }>;
  recentOrders: AdminOrder[];
}
export interface InventorySummary { lowStock: number; availableUnits: number; }
export interface ProductSpecificationPayload { groupName: string; name: string; value: string; highlighted: boolean; }
export interface ProductVariantPayload { id?: number; colorName: string; colorHex: string | null; imageId: number | null; }
export interface ProductPayload {
  name: string;
  slug: string;
  description: string;
  price: number;
  categoryId: number;
  brandId: number;
  shippingWeightGrams: number;
  shippingHeightCm: number;
  shippingWidthCm: number;
  shippingLengthCm: number;
  shippingClassificationId: number;
  mustKeepVertical: boolean;
  specifications: ProductSpecificationPayload[];
  variants: ProductVariantPayload[];
}
export type AdminOrder = Order;
export type CancellationScope = 'SHIPMENT_ONLY' | 'ORDER';
export interface CancelShipmentPayload {
  scope: CancellationScope;
  reasonCode?: CancellationReasonCode;
  internalDetail?: string;
}
export interface PendingBankTransferProof {
  id: string;
  status: 'PENDING_REVIEW';
  orderId: number;
  customerName: string;
  customerEmail: string;
  total: number;
  currency: string;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  submittedAt: string;
  reviewedAt: string | null;
  rejectionReason: string | null;
  previewCount: number;
}

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/admin/catalog`;

  products(search = '', page = 0) {
    let params = new HttpParams().set('page', page).set('size', 20).set('sort', 'name,asc');
    if (search.trim()) params = params.set('search', search.trim());
    return this.http.get<Page<ProductListItem>>(`${environment.apiBaseUrl}/products/cards`, { params });
  }

  product(id: number) { return this.http.get<Product>(`${environment.apiBaseUrl}/products/${id}`); }
  ordersPage(page = 0, status = 'ALL') {
    let params = new HttpParams().set('page', page).set('size', 20);
    if (status !== 'ALL') params = params.set('status', status);
    return this.http.get<Page<AdminOrder>>(`${environment.apiBaseUrl}/admin/orders/page`, { params });
  }
  ordersSummary() { return this.http.get<OrdersSummary>(`${environment.apiBaseUrl}/admin/orders/summary`); }
  inventoryPage(search = '', page = 0) {
    const params = new HttpParams().set('search', search.trim()).set('page', page).set('size', 20);
    return this.http.get<Page<InventoryListItem>>(`${environment.apiBaseUrl}/inventory/page`, { params });
  }
  inventorySummary() { return this.http.get<InventorySummary>(`${environment.apiBaseUrl}/inventory/summary`); }

  categories() { return this.http.get<Category[]>(`${this.baseUrl}/categories`); }
  brands() { return this.http.get<Brand[]>(`${this.baseUrl}/brands`); }
  createProduct(payload: ProductPayload) { return this.http.post<Product>(`${this.baseUrl}/products`, payload); }
  updateProduct(id: number, payload: ProductPayload) { return this.http.put<Product>(`${this.baseUrl}/products/${id}`, payload); }
  deleteProduct(id: number) { return this.http.delete<void>(`${this.baseUrl}/products/${id}`); }
  uploadProductImage(productId: number, file: File, altText?: string) {
    const body = new FormData();
    body.append('file', file);
    if (altText?.trim()) body.append('altText', altText.trim());
    return this.http.post<ProductImage>(`${this.baseUrl}/products/${productId}/images`, body);
  }
  deleteProductImage(productId: number, imageId: number) { return this.http.delete<void>(`${this.baseUrl}/products/${productId}/images/${imageId}`); }
  createCategory(payload: Omit<Category, 'id'>) { return this.http.post<Category>(`${this.baseUrl}/categories`, payload); }
  updateCategory(id: number, payload: Omit<Category, 'id'>) { return this.http.put<Category>(`${this.baseUrl}/categories/${id}`, payload); }
  deleteCategory(id: number) { return this.http.delete<void>(`${this.baseUrl}/categories/${id}`); }
  createBrand(name: string) { return this.http.post<Brand>(`${this.baseUrl}/brands`, { name }); }
  updateBrand(id: number, name: string) { return this.http.put<Brand>(`${this.baseUrl}/brands/${id}`, { name }); }
  deleteBrand(id: number) { return this.http.delete<void>(`${this.baseUrl}/brands/${id}`); }
  inventory(variantId: number) { return this.http.get<Inventory>(`${environment.apiBaseUrl}/inventory/${variantId}`); }
  inventories() { return this.http.get<Inventory[]>(`${environment.apiBaseUrl}/inventory`); }
  adjustInventory(variantId: number, quantity: number, reason: string) {
    return this.http.post<Inventory>(`${environment.apiBaseUrl}/inventory/adjustments`, { variantId, quantity, reason });
  }
  order(id: number) { return this.http.get<AdminOrder>(`${environment.apiBaseUrl}/admin/orders/${id}`); }
  updateOrderStatus(id: number, status: string) { return this.http.patch<AdminOrder>(`${environment.apiBaseUrl}/admin/orders/${id}/status`, { status }); }
  retryShipment(orderId: number) { return this.http.post<void>(`${environment.apiBaseUrl}/admin/shipping/orders/${orderId}/retry`, null); }
  cancelShipment(orderId: number, payload: CancelShipmentPayload) {
    return this.http.post<AdminOrder>(`${environment.apiBaseUrl}/admin/shipping/orders/${orderId}/cancel`, payload);
  }
  shipmentLabel(orderId: number) { return this.http.get(`${environment.apiBaseUrl}/admin/shipping/orders/${orderId}/label`, { responseType: 'blob' }); }
  shipmentDocument(orderId: number) { return this.http.get(`${environment.apiBaseUrl}/admin/shipping/orders/${orderId}/document`, { responseType: 'blob' }); }
  pendingBankTransferProofs() {
    return this.http.get<PendingBankTransferProof[]>(`${environment.apiBaseUrl}/admin/bank-transfer-proofs`, {
      params: { status: 'PENDING_REVIEW' },
    });
  }
  bankTransferProofPreview(proofId: string, index: number) {
    return this.http.get(`${environment.apiBaseUrl}/admin/bank-transfer-proofs/${proofId}/previews/${index}`, { responseType: 'blob' });
  }
  approveBankTransferProof(proofId: string, amount: number, reference: string) {
    return this.http.post<void>(`${environment.apiBaseUrl}/admin/bank-transfer-proofs/${proofId}/approve`, { amount, reference });
  }
  rejectBankTransferProof(proofId: string, reason: string) {
    return this.http.post<void>(`${environment.apiBaseUrl}/admin/bank-transfer-proofs/${proofId}/reject`, { reason });
  }
  confirmBankTransferRefund(orderId: number, reference?: string) {
    return this.http.post<AdminOrder>(`${environment.apiBaseUrl}/admin/orders/${orderId}/bank-transfer-refund/confirm`, reference ? { reference } : {});
  }
}

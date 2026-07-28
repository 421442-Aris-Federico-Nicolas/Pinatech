import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { Page, Product } from '../catalog/catalog.service';
import { Order } from '../../core/orders/order.service';

export interface Category { id: number; name: string; slug: string; }
export interface Brand { id: number; name: string; }
export interface Inventory { productId: number; availableQuantity: number; reservedQuantity: number; }
export interface ProductPayload { name: string; slug: string; description: string; price: number; categoryId: number; brandId: number; }

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/admin/catalog`;

  products(search = '') {
    let params = new HttpParams().set('size', 100).set('sort', 'name');
    if (search.trim()) params = params.set('search', search.trim());
    return this.http.get<Page<Product>>(`${environment.apiBaseUrl}/products`, { params });
  }

  categories() { return this.http.get<Category[]>(`${this.baseUrl}/categories`); }
  brands() { return this.http.get<Brand[]>(`${this.baseUrl}/brands`); }
  createProduct(payload: ProductPayload) { return this.http.post<Product>(`${this.baseUrl}/products`, payload); }
  updateProduct(id: number, payload: ProductPayload) { return this.http.put<Product>(`${this.baseUrl}/products/${id}`, payload); }
  deleteProduct(id: number) { return this.http.delete<void>(`${this.baseUrl}/products/${id}`); }
  createCategory(payload: Omit<Category, 'id'>) { return this.http.post<Category>(`${this.baseUrl}/categories`, payload); }
  deleteCategory(id: number) { return this.http.delete<void>(`${this.baseUrl}/categories/${id}`); }
  createBrand(name: string) { return this.http.post<Brand>(`${this.baseUrl}/brands`, { name }); }
  deleteBrand(id: number) { return this.http.delete<void>(`${this.baseUrl}/brands/${id}`); }
  inventory(productId: number) { return this.http.get<Inventory>(`${environment.apiBaseUrl}/inventory/${productId}`); }
  inventories() { return this.http.get<Inventory[]>(`${environment.apiBaseUrl}/inventory`); }
  adjustInventory(productId: number, quantity: number, reason: string) {
    return this.http.post<Inventory>(`${environment.apiBaseUrl}/inventory/adjustments`, { productId, quantity, reason });
  }
  orders() { return this.http.get<Order[]>(`${environment.apiBaseUrl}/admin/orders`); }
  updateOrderStatus(id: number, status: string) { return this.http.patch<Order>(`${environment.apiBaseUrl}/admin/orders/${id}/status`, { status }); }
}

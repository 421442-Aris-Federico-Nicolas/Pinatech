import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';
export interface Product { id: number; name: string; slug: string; description: string; price: number; categoryId: number; categoryName: string; brandId: number; brandName: string; }
export interface Page<T> { content: T[]; totalPages: number; number: number; }
@Injectable({ providedIn: 'root' }) export class CatalogService { private readonly http = inject(HttpClient); getProducts(search: string, page: number) { let params = new HttpParams().set('page', page).set('size', 12); if (search.trim()) params = params.set('search', search.trim()); return this.http.get<Page<Product>>(`${environment.apiBaseUrl}/products`, { params }); } }

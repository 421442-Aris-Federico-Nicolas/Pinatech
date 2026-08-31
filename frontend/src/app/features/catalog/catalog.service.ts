import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';

export interface ProductImage { id: number; contentUrl: string; altText: string; originalFilename?: string | null; displayOrder: number; }
export interface ProductSpecification { id: number; groupName: string; name: string; value: string; highlighted: boolean; displayOrder: number; }
export interface ProductVariant { id: number; colorName: string; colorHex: string | null; imageId?: number | null; inStock: boolean; availableQuantity: number; }
export interface Product { id: number; name: string; slug: string; description: string; price: number; categoryId: number; categoryName: string; brandId: number; brandName: string; images: ProductImage[]; specifications: ProductSpecification[]; variants: ProductVariant[]; }
export interface Page<T> { content: T[]; totalPages: number; totalElements: number; number: number; size: number; }
export interface Category { id: number; name: string; slug: string; }
export interface Brand { id: number; name: string; }
export interface CatalogFilters { search: string; categoryId: number | null; brandId: number | null; minPrice: number | null; maxPrice: number | null; }
export type CatalogSort = 'name,asc' | 'name,desc' | 'price,asc' | 'price,desc';

@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly http = inject(HttpClient);

  getProducts(filters: CatalogFilters, page: number, sort: CatalogSort = 'name,asc') {
    let params = new HttpParams().set('page', page).set('size', 12).set('sort', sort);
    if (filters.search.trim()) params = params.set('search', filters.search.trim());
    if (filters.categoryId !== null) params = params.set('categoryId', filters.categoryId);
    if (filters.brandId !== null) params = params.set('brandId', filters.brandId);
    if (filters.minPrice !== null) params = params.set('minPrice', filters.minPrice);
    if (filters.maxPrice !== null) params = params.set('maxPrice', filters.maxPrice);
    return this.http.get<Page<Product>>(`${environment.apiBaseUrl}/products`, { params });
  }

  product(id: number) { return this.http.get<Product>(`${environment.apiBaseUrl}/products/${id}`); }
  categories() { return this.http.get<Category[]>(`${environment.apiBaseUrl}/categories`); }
  brands() { return this.http.get<Brand[]>(`${environment.apiBaseUrl}/brands`); }
}

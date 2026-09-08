import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../../environments/environment';
import { resolveApiContentUrl } from '../../../core/utils/api-content-url';
import { ProductListItemResponse, Page } from '../../catalog/catalog.service';
import { Category } from '../admin.service';
import { HomeSectionCategory, HomeSectionMode, HomeSectionSort } from '../../home/home-sections.service';

export interface AdminHomeSection {
  readonly id: number;
  readonly displayOrder: number;
  readonly eyebrow: string | null;
  readonly title: string;
  readonly description: string | null;
  readonly buttonLabel: string | null;
  readonly mode: HomeSectionMode;
  readonly productLimit: number;
  readonly sort: HomeSectionSort | null;
  readonly categoryIds: readonly number[];
  readonly productIds: readonly number[];
  readonly active: boolean;
  readonly bannerDesktopUrl: string | null;
  readonly bannerMobileUrl: string | null;
  readonly categories?: readonly HomeSectionCategory[];
  readonly products?: readonly ProductListItemResponse[];
}

export interface HomeSectionPayload {
  eyebrow: string;
  title: string;
  description: string;
  buttonLabel: string;
  mode: HomeSectionMode;
  productLimit: number;
  sort: HomeSectionSort;
  categoryIds: number[];
  productIds: number[];
  active: boolean;
}

export type HomeBannerDevice = 'DESKTOP' | 'MOBILE';
export interface HomeBannerResponse { readonly id: number; readonly device: HomeBannerDevice; readonly url: string; }

@Injectable({ providedIn: 'root' })
export class HomeSectionsAdminService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/admin/home/sections`;

  sections() { return this.http.get<AdminHomeSection[]>(this.baseUrl); }
  categories() { return this.http.get<Category[]>(`${environment.apiBaseUrl}/admin/catalog/categories`); }
  productCandidates(search = '', page = 0) {
    let params = new HttpParams().set('page', page).set('size', 24).set('sort', 'name,asc');
    if (search.trim()) params = params.set('search', search.trim());
    return this.http.get<Page<ProductListItemResponse>>(`${environment.apiBaseUrl}/products/cards`, { params });
  }
  create(payload: HomeSectionPayload) { return this.http.post<AdminHomeSection>(this.baseUrl, payload); }
  update(id: number, payload: HomeSectionPayload) { return this.http.put<AdminHomeSection>(`${this.baseUrl}/${id}`, payload); }
  reorder(sectionIds: number[]) { return this.http.put<void>(`${this.baseUrl}/order`, { sectionIds }); }
  delete(id: number) { return this.http.delete<void>(`${this.baseUrl}/${id}`); }
  uploadBanner(id: number, device: HomeBannerDevice, file: File) {
    const body = new FormData();
    body.append('file', file);
    return this.http.put<HomeBannerResponse>(`${this.baseUrl}/${id}/banners/${device}`, body);
  }
  deleteBanner(id: number, device: HomeBannerDevice) {
    return this.http.delete<void>(`${this.baseUrl}/${id}/banners/${device}`);
  }
  fetchBanner(url: string) {
    return this.http.get(resolveApiContentUrl(url), { responseType: 'blob' });
  }
}

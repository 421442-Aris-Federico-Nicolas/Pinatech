import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { resolveApiContentUrl } from '../../core/utils/api-content-url';
import { ProductListItemResponse } from '../catalog/catalog.service';

export type HomeSectionMode = 'MANUAL' | 'AUTOMATIC';
export type HomeSectionSort = 'NAME_ASC' | 'NAME_DESC' | 'PRICE_ASC' | 'PRICE_DESC' | 'NEWEST';

export interface HomeSectionCategory {
  readonly id: number;
  readonly name: string;
  readonly slug: string;
}

export interface HomeSection {
  readonly id: number;
  readonly displayOrder: number;
  readonly eyebrow: string | null;
  readonly title: string;
  readonly description: string | null;
  readonly buttonLabel: string | null;
  readonly mode: HomeSectionMode;
  readonly productLimit: number;
  readonly sort: HomeSectionSort | null;
  readonly bannerDesktopUrl: string | null;
  readonly bannerMobileUrl: string | null;
  readonly categories: readonly HomeSectionCategory[];
  readonly products: readonly ProductListItemResponse[];
}

export function resolveHomeBannerUrl(url: string | null | undefined): string {
  if (!url) return '';
  return url.startsWith('/api/') || url === '/api' ? resolveApiContentUrl(url) : url;
}

export interface HomeHeroImage {
  readonly id: number;
  readonly url: string;
  readonly width: number;
  readonly height: number;
}

export interface HomeHeroSlide {
  readonly id: number;
  readonly displayOrder: number;
  readonly eyebrow: string;
  readonly title: string;
  readonly accent: string;
  readonly description: string;
  readonly link: string;
  readonly linkLabel: string;
  readonly showLoginLink: boolean;
  readonly altText: string;
  readonly desktopImage: HomeHeroImage | null;
  readonly mobileImage: HomeHeroImage | null;
}

@Injectable({ providedIn: 'root' })
export class HomeSectionsService {
  private readonly http = inject(HttpClient);

  sections() {
    return this.http.get<HomeSection[]>(`${environment.apiBaseUrl}/home/sections`);
  }

  heroSlides() {
    return this.http.get<HomeHeroSlide[]>(`${environment.apiBaseUrl}/home/hero`);
  }
}

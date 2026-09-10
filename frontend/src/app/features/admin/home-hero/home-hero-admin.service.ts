import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../../environments/environment';
import { resolveApiContentUrl } from '../../../core/utils/api-content-url';

export type HomeHeroImageDevice = 'DESKTOP' | 'MOBILE';

export interface AdminHomeHeroImage {
  readonly id: number;
  readonly device: HomeHeroImageDevice;
  readonly url: string;
  readonly width: number;
  readonly height: number;
  readonly originalFilename: string;
}

export interface AdminHomeHeroSlide {
  readonly id: number;
  readonly displayOrder: number;
  readonly active: boolean;
  readonly eyebrow: string;
  readonly title: string;
  readonly accent: string;
  readonly description: string;
  readonly link: string;
  readonly linkLabel: string;
  readonly showLoginLink: boolean;
  readonly altText: string;
  readonly images: readonly AdminHomeHeroImage[];
}

export interface HomeHeroSlidePayload {
  eyebrow: string;
  title: string;
  accent: string;
  description: string;
  link: string;
  linkLabel: string;
  showLoginLink: boolean;
  altText: string;
  active: boolean;
}

@Injectable({ providedIn: 'root' })
export class HomeHeroAdminService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/admin/home/hero`;

  slides() { return this.http.get<AdminHomeHeroSlide[]>(this.baseUrl); }
  create(payload: HomeHeroSlidePayload) { return this.http.post<AdminHomeHeroSlide>(this.baseUrl, payload); }
  update(id: number, payload: HomeHeroSlidePayload) { return this.http.put<AdminHomeHeroSlide>(`${this.baseUrl}/${id}`, payload); }
  reorder(slideIds: number[]) { return this.http.put<void>(`${this.baseUrl}/order`, { slideIds }); }
  delete(id: number) { return this.http.delete<void>(`${this.baseUrl}/${id}`); }
  uploadImage(id: number, device: HomeHeroImageDevice, file: File) {
    const body = new FormData();
    body.append('file', file);
    return this.http.put<AdminHomeHeroImage>(`${this.baseUrl}/${id}/images/${device}`, body);
  }
  deleteImage(id: number, device: HomeHeroImageDevice) {
    return this.http.delete<void>(`${this.baseUrl}/${id}/images/${device}`);
  }
  fetchImage(url: string) {
    return this.http.get(resolveApiContentUrl(url), { responseType: 'blob' });
  }
}

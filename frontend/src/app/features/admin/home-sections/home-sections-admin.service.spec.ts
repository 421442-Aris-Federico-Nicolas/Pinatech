import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/auth/auth.service';
import { authInterceptor } from '../../../core/interceptors/auth.interceptor';
import { resolveApiContentUrl } from '../../../core/utils/api-content-url';
import { HomeSectionPayload, HomeSectionsAdminService } from './home-sections-admin.service';

describe('HomeSectionsAdminService', () => {
  const base = `${environment.apiBaseUrl}/admin/home/sections`;
  const payload: HomeSectionPayload = {
    eyebrow: '', title: 'Hardware', description: '', buttonLabel: '', mode: 'MANUAL',
    productLimit: 8, sort: 'NEWEST', categoryIds: [1, 2], productIds: [5], active: true,
  };

  beforeEach(() => TestBed.configureTestingModule({ providers: [
    provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting(),
    { provide: AuthService, useValue: { getAccessToken: () => 'admin-token' } },
    { provide: Router, useValue: { url: '/admin', navigate: vi.fn() } },
  ] }));
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('uses the CRUD and ordering contract', () => {
    const service = TestBed.inject(HomeSectionsAdminService);
    const http = TestBed.inject(HttpTestingController);
    service.sections().subscribe();
    const list = http.expectOne(base); expect(list.request.method).toBe('GET'); list.flush([]);
    service.create(payload).subscribe();
    const creation = http.expectOne(base); expect(creation.request.body).toEqual(payload); creation.flush({});
    service.update(4, payload).subscribe();
    const update = http.expectOne(`${base}/4`); expect(update.request.method).toBe('PUT'); update.flush({});
    service.reorder([4, 2]).subscribe();
    const order = http.expectOne(`${base}/order`); expect(order.request.body).toEqual({ sectionIds: [4, 2] }); order.flush(null);
    service.delete(4).subscribe();
    const deletion = http.expectOne(`${base}/4`); expect(deletion.request.method).toBe('DELETE'); deletion.flush(null);
  });

  it('loads candidates in pages of 24 and uploads each banner as multipart', () => {
    const service = TestBed.inject(HomeSectionsAdminService);
    const http = TestBed.inject(HttpTestingController);
    service.productCandidates(' mouse ', 2).subscribe();
    const cards = http.expectOne((request) => request.url.endsWith('/products/cards'));
    expect(cards.request.params.get('search')).toBe('mouse');
    expect(cards.request.params.get('page')).toBe('2');
    expect(cards.request.params.get('size')).toBe('24');
    cards.flush({ content: [], number: 2, size: 24, totalPages: 0, totalElements: 0 });

    const file = new File(['banner'], 'desktop.jpg', { type: 'image/jpeg' });
    service.uploadBanner(3, 'DESKTOP', file).subscribe();
    const upload = http.expectOne(`${base}/3/banners/DESKTOP`);
    expect(upload.request.method).toBe('PUT');
    expect((upload.request.body as FormData).get('file')).toBe(file);
    upload.flush({});
    service.deleteBanner(3, 'MOBILE').subscribe();
    const deletion = http.expectOne(`${base}/3/banners/MOBILE`); expect(deletion.request.method).toBe('DELETE'); deletion.flush(null);
  });

  it('fetches protected banners as authenticated blobs', () => {
    const service = TestBed.inject(HomeSectionsAdminService);
    const http = TestBed.inject(HttpTestingController);
    const url = '/api/admin/home/sections/3/banners/DESKTOP/content';
    const blob = new Blob(['banner'], { type: 'image/webp' });

    let response: Blob | undefined;
    service.fetchBanner(url).subscribe((value) => response = value);

    const request = http.expectOne(resolveApiContentUrl(url));
    expect(request.request.method).toBe('GET');
    expect(request.request.responseType).toBe('blob');
    expect(request.request.headers.get('Authorization')).toBe('Bearer admin-token');
    expect(request.request.withCredentials).toBe(true);
    request.flush(blob);
    expect(response).toBe(blob);
  });
});

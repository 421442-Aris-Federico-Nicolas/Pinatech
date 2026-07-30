import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { AdminService } from './admin.service';

describe('AdminService product images', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }));
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('uploads multipart images with optional alt text', () => {
    const service = TestBed.inject(AdminService);
    const http = TestBed.inject(HttpTestingController);
    const file = new File(['image'], 'keyboard.jpg', { type: 'image/jpeg' });

    service.uploadProductImage(3, file, 'Teclado visto de frente').subscribe();

    const request = http.expectOne(`${environment.apiBaseUrl}/admin/catalog/products/3/images`);
    const body = request.request.body as FormData;
    expect(body.get('file')).toBe(file);
    expect(body.get('altText')).toBe('Teclado visto de frente');
    request.flush({ id: 8, contentUrl: '/api/products/images/8/content', altText: 'Teclado visto de frente', displayOrder: 0 });
  });

  it('deletes an image from its product resource', () => {
    const service = TestBed.inject(AdminService);
    service.deleteProductImage(3, 8).subscribe();

    const request = TestBed.inject(HttpTestingController).expectOne(`${environment.apiBaseUrl}/admin/catalog/products/3/images/8`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });

  it('updates and deletes categories through admin catalog endpoints', () => {
    const service = TestBed.inject(AdminService);
    const http = TestBed.inject(HttpTestingController);

    service.updateCategory(4, { name: 'Notebooks', slug: 'notebooks' }).subscribe();
    const update = http.expectOne(`${environment.apiBaseUrl}/admin/catalog/categories/4`);
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual({ name: 'Notebooks', slug: 'notebooks' });
    update.flush({ id: 4, name: 'Notebooks', slug: 'notebooks' });

    service.deleteCategory(4).subscribe();
    const deletion = http.expectOne(`${environment.apiBaseUrl}/admin/catalog/categories/4`);
    expect(deletion.request.method).toBe('DELETE');
    deletion.flush(null);
  });

  it('updates and deletes brands through admin catalog endpoints', () => {
    const service = TestBed.inject(AdminService);
    const http = TestBed.inject(HttpTestingController);

    service.updateBrand(5, 'Lenovo').subscribe();
    const update = http.expectOne(`${environment.apiBaseUrl}/admin/catalog/brands/5`);
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual({ name: 'Lenovo' });
    update.flush({ id: 5, name: 'Lenovo' });

    service.deleteBrand(5).subscribe();
    const deletion = http.expectOne(`${environment.apiBaseUrl}/admin/catalog/brands/5`);
    expect(deletion.request.method).toBe('DELETE');
    deletion.flush(null);
  });
});

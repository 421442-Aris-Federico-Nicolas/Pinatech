import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { CatalogService } from './catalog.service';

describe('CatalogService', () => {
  let service: CatalogService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(CatalogService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('sends active filters, page and supported sorting to the public API', () => {
    service.getProducts({ search: ' teclado ', categoryId: 2, brandId: 3, minPrice: 100, maxPrice: 5000 }, 1, 'price,desc').subscribe();

    const request = httpTesting.expectOne((candidate) => candidate.url === `${environment.apiBaseUrl}/products`);
    expect(request.request.params.get('search')).toBe('teclado');
    expect(request.request.params.get('categoryId')).toBe('2');
    expect(request.request.params.get('brandId')).toBe('3');
    expect(request.request.params.get('minPrice')).toBe('100');
    expect(request.request.params.get('maxPrice')).toBe('5000');
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('size')).toBe('12');
    expect(request.request.params.get('sort')).toBe('price,desc');
    request.flush({ content: [], totalPages: 0, totalElements: 0, number: 1, size: 12 });
  });
});

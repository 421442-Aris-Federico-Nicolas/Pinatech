import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { HomeSectionsService, resolveHomeBannerUrl } from './home-sections.service';

describe('HomeSectionsService', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }));
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('loads all public sections in one request', () => {
    TestBed.inject(HomeSectionsService).sections().subscribe((sections) => expect(sections).toEqual([]));
    const request = TestBed.inject(HttpTestingController).expectOne(`${environment.apiBaseUrl}/home/sections`);
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('keeps frontend assets local and resolves API content URLs', () => {
    expect(resolveHomeBannerUrl('/pinatech-banner-hardware.jpg')).toBe('/pinatech-banner-hardware.jpg');
    expect(resolveHomeBannerUrl('/api/home/sections/1/banner')).toContain('/api/home/sections/1/banner');
    expect(resolveHomeBannerUrl(null)).toBe('');
  });
});

import { TestBed } from '@angular/core/testing';
import { Meta, Title } from '@angular/platform-browser';
import { SeoService } from './seo.service';

describe('SeoService', () => {
  let seo: SeoService;
  let meta: Meta;
  let title: Title;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    seo = TestBed.inject(SeoService);
    meta = TestBed.inject(Meta);
    title = TestBed.inject(Title);
    document.head.querySelector('link[rel="canonical"]')?.remove();
    document.getElementById('product-structured-data')?.remove();
  });

  it('indexes clean catalog pages and excludes filtered duplicates', () => {
    seo.applyRoute('/catalog?page=2');

    expect(title.getTitle()).toBe('Catálogo de tecnología - Página 2 | Pinatech');
    expect(meta.getTag('name="robots"')?.content).toContain('index, follow');
    expect(document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]')?.href)
      .toBe('https://pinatech.com.ar/catalog?page=2');

    seo.applyRoute('/catalog?search=mouse&sort=price,asc');

    expect(meta.getTag('name="robots"')?.content).toBe('noindex, follow');
    expect(document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]')?.href)
      .toBe('https://pinatech.com.ar/catalog');
  });

  it('publishes product social metadata and valid product structured data', () => {
    seo.setProduct({
      id: 7,
      name: 'Mouse Pro',
      slug: 'mouse-pro',
      description: 'Mouse profesional para gaming.',
      price: 125000,
      brandName: 'HyperX',
      categoryName: 'Periféricos',
      images: [{ contentUrl: '/image.jpg' }],
      inStock: true,
    }, (url) => `https://api.pinatech.com.ar${url}`);

    expect(title.getTitle()).toBe('Mouse Pro | Pinatech');
    expect(meta.getTag('property="og:type"')?.content).toBe('product');
    expect(meta.getTag('property="og:image"')?.content).toBe('https://api.pinatech.com.ar/image.jpg');
    expect(document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]')?.href)
      .toBe('https://pinatech.com.ar/products/7/mouse-pro');

    const schema = JSON.parse(document.getElementById('product-structured-data')?.textContent ?? '{}');
    expect(schema['@graph'][0]).toMatchObject({
      '@type': 'Product',
      name: 'Mouse Pro',
      brand: { '@type': 'Brand', name: 'HyperX' },
      offers: {
        '@type': 'Offer',
        priceCurrency: 'ARS',
        price: '125000.00',
        availability: 'https://schema.org/InStock',
      },
    });
    expect(schema['@graph'][1]['@type']).toBe('BreadcrumbList');
  });

  it('marks private and missing pages as noindex', () => {
    seo.applyRoute('/checkout');
    expect(meta.getTag('name="robots"')?.content).toBe('noindex, follow');

    seo.setProductError(true);
    expect(title.getTitle()).toBe('Producto no encontrado | Pinatech');
    expect(meta.getTag('name="robots"')?.content).toBe('noindex, follow');
    expect(document.getElementById('product-structured-data')).toBeNull();
  });
});

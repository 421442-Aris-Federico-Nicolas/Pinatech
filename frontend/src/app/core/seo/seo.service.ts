import { DOCUMENT } from '@angular/common';
import { inject, Injectable } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';

const SITE_URL = 'https://pinatech.com.ar';
const DEFAULT_IMAGE = `${SITE_URL}/pinatech-banner-home.jpg`;
const INDEX_ROBOTS = 'index, follow, max-image-preview:large, max-snippet:-1, max-video-preview:-1';
const NOINDEX_ROBOTS = 'noindex, follow';

interface ProductSeoData {
  id: number;
  name: string;
  slug: string;
  description: string;
  price: number;
  brandName: string;
  categoryName: string;
  images: { contentUrl: string }[];
  inStock: boolean;
}

@Injectable({ providedIn: 'root' })
export class SeoService {
  private readonly document = inject(DOCUMENT);
  private readonly meta = inject(Meta);
  private readonly title = inject(Title);

  applyRoute(url: string): void {
    const parsed = new URL(url, SITE_URL);

    if (parsed.pathname === '/') {
      this.removeProductStructuredData();
      this.setPage(
        'Pinatech | Tecnología y hardware en Córdoba',
        'Tienda de hardware, periféricos y tecnología en Córdoba. Comprá online en Pinatech con transferencia o Mercado Pago.',
        '/',
        true,
      );
      return;
    }

    if (parsed.pathname === '/catalog') {
      this.removeProductStructuredData();
      const page = this.catalogPage(parsed.searchParams);
      const filtered = [...parsed.searchParams.keys()].some((key) => key !== 'page') || page === null;
      const suffix = page && page > 1 ? ` - Página ${page}` : '';
      this.setPage(
        `Catálogo de tecnología${suffix} | Pinatech`,
        'Explorá hardware, periféricos y productos tecnológicos disponibles en Pinatech Córdoba.',
        !filtered && page && page > 1 ? `/catalog?page=${page}` : '/catalog',
        !filtered,
      );
      return;
    }

    if (/^\/products\/\d+(?:\/[^/]+)?$/.test(parsed.pathname)) {
      if (this.document.getElementById('product-structured-data')) return;
      this.setPage(
        'Producto de tecnología | Pinatech',
        'Consultá precio, disponibilidad y características de este producto en Pinatech.',
        parsed.pathname,
        true,
      );
      return;
    }

    this.removeProductStructuredData();
    this.setPage(
      this.title.getTitle() || 'Pinatech',
      'Acceso a servicios y gestiones de Pinatech.',
      parsed.pathname,
      false,
    );
  }

  setProduct(product: ProductSeoData, resolveImage: (url: string) => string): void {
    const canonicalPath = `/products/${product.id}/${encodeURIComponent(product.slug)}`;
    const canonicalUrl = `${SITE_URL}${canonicalPath}`;
    const description = this.description(product.description, `${product.name} disponible en Pinatech.`);
    const images = product.images.map((image) => resolveImage(image.contentUrl)).filter(Boolean);
    const image = images[0] || DEFAULT_IMAGE;

    this.setPage(`${product.name} | Pinatech`, description, canonicalPath, true, image, 'product');
    this.setProductStructuredData({
      '@context': 'https://schema.org',
      '@graph': [
        {
          '@type': 'Product',
          '@id': `${canonicalUrl}#product`,
          name: product.name,
          description,
          sku: product.id.toString(),
          ...(images.length ? { image: images } : {}),
          brand: { '@type': 'Brand', name: product.brandName },
          category: product.categoryName,
          offers: {
            '@type': 'Offer',
            url: canonicalUrl,
            priceCurrency: 'ARS',
            price: product.price.toFixed(2),
            itemCondition: 'https://schema.org/NewCondition',
            availability: product.inStock ? 'https://schema.org/InStock' : 'https://schema.org/OutOfStock',
          },
        },
        {
          '@type': 'BreadcrumbList',
          itemListElement: [
            { '@type': 'ListItem', position: 1, name: 'Inicio', item: SITE_URL },
            { '@type': 'ListItem', position: 2, name: 'Catálogo', item: `${SITE_URL}/catalog` },
            { '@type': 'ListItem', position: 3, name: product.name, item: canonicalUrl },
          ],
        },
      ],
    });
  }

  setProductError(notFound: boolean): void {
    this.removeProductStructuredData();
    this.setPage(
      notFound ? 'Producto no encontrado | Pinatech' : 'No pudimos cargar el producto | Pinatech',
      notFound ? 'El producto no existe o ya no está publicado.' : 'No pudimos cargar la información del producto.',
      '/catalog',
      false,
    );
  }

  private setPage(title: string, description: string, path: string, indexable: boolean, image = DEFAULT_IMAGE, type = 'website'): void {
    const canonicalUrl = `${SITE_URL}${path === '/' ? '' : path}`;
    this.title.setTitle(title);
    this.meta.updateTag({ name: 'description', content: description });
    this.meta.updateTag({ name: 'robots', content: indexable ? INDEX_ROBOTS : NOINDEX_ROBOTS });
    this.meta.updateTag({ property: 'og:title', content: title });
    this.meta.updateTag({ property: 'og:description', content: description });
    this.meta.updateTag({ property: 'og:type', content: type });
    this.meta.updateTag({ property: 'og:url', content: canonicalUrl });
    this.meta.updateTag({ property: 'og:image', content: image });
    this.meta.updateTag({ name: 'twitter:title', content: title });
    this.meta.updateTag({ name: 'twitter:description', content: description });
    this.meta.updateTag({ name: 'twitter:image', content: image });
    this.canonicalLink().href = canonicalUrl;
  }

  private canonicalLink(): HTMLLinkElement {
    const existing = this.document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    if (existing) return existing;
    const link = this.document.createElement('link');
    link.rel = 'canonical';
    this.document.head.appendChild(link);
    return link;
  }

  private catalogPage(params: URLSearchParams): number | null {
    const value = params.get('page');
    if (value === null) return 1;
    return /^\d+$/.test(value) && Number(value) > 0 ? Number(value) : null;
  }

  private description(value: string, fallback: string): string {
    const normalized = value.replace(/\s+/g, ' ').trim() || fallback;
    return normalized.length <= 155 ? normalized : `${normalized.slice(0, 152).trimEnd()}...`;
  }

  private setProductStructuredData(value: object): void {
    let script = this.document.getElementById('product-structured-data') as HTMLScriptElement | null;
    if (!script) {
      script = this.document.createElement('script');
      script.id = 'product-structured-data';
      script.type = 'application/ld+json';
      this.document.head.appendChild(script);
    }
    script.textContent = JSON.stringify(value);
  }

  private removeProductStructuredData(): void {
    this.document.getElementById('product-structured-data')?.remove();
  }
}

import { ChangeDetectionStrategy, Component, CUSTOM_ELEMENTS_SCHEMA, DestroyRef, ElementRef, HostListener, Injector, afterNextRender, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { EMPTY, expand, finalize, forkJoin, reduce } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';
import { AppFeedbackComponent } from '../../shared/ui/feedback/app-feedback.component';
import { BannerCarouselComponent, BannerSlide } from '../../shared/ui/banner-carousel/banner-carousel.component';
import { AppProductCardComponent } from '../../shared/ui/product-card/app-product-card.component';
import { CatalogService, Product } from '../catalog/catalog.service';

interface ProductShowcaseGroup {
  readonly eyebrow: string;
  readonly title: string;
  readonly description: string;
  readonly banner: BannerSlide;
  readonly products: readonly Product[];
  readonly linkLabel: string;
  readonly queryParams: Record<string, number> | null;
}

interface HeroPanel {
  readonly eyebrow: string;
  readonly title: string;
  readonly accent: string;
  readonly description: string;
  readonly link: string;
  readonly linkLabel: string;
}

interface ProductTrackPosition {
  readonly atStart: boolean;
  readonly atEnd: boolean;
}

@Component({
  selector: 'app-home',
  imports: [AppButtonDirective, AppFeedbackComponent, AppProductCardComponent, BannerCarouselComponent, RouterLink],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent {
  private readonly catalog = inject(CatalogService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  protected readonly auth = inject(AuthService);

  protected readonly featured = signal<Product[]>([]);
  protected readonly peripheralCategoryId = signal<number | null>(null);
  protected readonly heroIndex = signal(0);
  protected readonly heroPointerPaused = signal(false);
  protected readonly heroFocusPaused = signal(false);
  protected readonly isLoading = signal(true);
  protected readonly error = signal(false);
  protected readonly productTrackPositions = signal<Record<number, ProductTrackPosition>>({});
  protected readonly heroSlides: readonly BannerSlide[] = [
    { src: '/pinatech-banner-home.jpg', mobileSrc: '/pinatech-banner-home-mobile.jpg', alt: 'Pinatech, tecnología a tu alcance, junto a componentes de hardware', width: 2000, height: 848 },
    { src: '/pinatech-banner-cart.jpg', mobileSrc: '/pinatech-banner-cart-mobile.jpg', alt: 'Carrito de compras Pinatech cargado con componentes de hardware', width: 2000, height: 848 },
  ];
  protected readonly heroPanels: readonly HeroPanel[] = [
    {
      eyebrow: 'Pinatech tecnología',
      title: 'Elevá tu setup.',
      accent: 'Elegí con claridad.',
      description: 'Hardware y periféricos con disponibilidad real para armar o actualizar tu equipo.',
      link: '/catalog',
      linkLabel: 'Explorar catálogo',
    },
    {
      eyebrow: 'Tu selección te espera',
      title: 'No dejes que tu carrito',
      accent: 'se pierda.',
      description: 'Revisá tus productos, ajustá las cantidades y continuá cuando estés listo.',
      link: '/cart',
      linkLabel: 'Ver mi carrito',
    },
  ];
  protected readonly activeHeroPanel = computed<HeroPanel>(() => this.heroPanels[this.heroIndex()] ?? this.heroPanels[0]!);
  protected readonly productGroups = computed<readonly ProductShowcaseGroup[]>(() => {
    const products = this.featured();
    const peripheralCategoryId = this.peripheralCategoryId();
    const peripheralProducts = peripheralCategoryId === null ? [] : products.filter((product) => product.categoryId === peripheralCategoryId);
    const hardwareProducts = peripheralCategoryId === null ? products : products.filter((product) => product.categoryId !== peripheralCategoryId);

    return [
      {
        eyebrow: 'Periféricos',
        title: 'Completá tu setup',
        description: 'Teclados, mouse, auriculares y accesorios para jugar, trabajar y crear con comodidad.',
        banner: { src: '/pinatech-banner-perifericos.jpg', alt: 'Periféricos Pinatech: teclado, auriculares y mouse', width: 2000, height: 848 },
        products: peripheralProducts,
        linkLabel: 'Ver todos los periféricos',
        queryParams: peripheralCategoryId === null ? null : { category: peripheralCategoryId },
      },
      {
        eyebrow: 'Hardware',
        title: 'Potencia para tu equipo',
        description: 'Procesadores, placas de video, memorias y almacenamiento para tu próxima actualización.',
        banner: { src: '/pinatech-banner-hardware.jpg', alt: 'Hardware Pinatech: computadora de escritorio y periféricos', width: 2000, height: 848 },
        products: hardwareProducts,
        linkLabel: 'Ver catálogo de hardware',
        queryParams: null,
      },
    ];
  });

  constructor() {
    this.loadFeatured();
  }

  protected loadFeatured(): void {
    this.isLoading.set(true);
    this.error.set(false);

    forkJoin({
      categories: this.catalog.categories(),
      products: this.loadAllProducts(),
    })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isLoading.set(false)),
      )
      .subscribe({
        next: ({ categories, products }) => {
          this.peripheralCategoryId.set(categories.find((category) => category.slug === 'perifericos')?.id ?? null);
          this.featured.set(products);
          afterNextRender({ read: () => this.refreshProductTracks() }, { injector: this.injector });
        },
        error: () => this.error.set(true),
      });
  }

  protected selectHero(index: number): void {
    this.heroIndex.set(index);
  }

  protected resumeHeroAfterFocus(event: FocusEvent): void {
    const hero = event.currentTarget as HTMLElement | null;
    if (!hero?.contains(event.relatedTarget as Node | null)) this.heroFocusPaused.set(false);
  }

  protected productTrackPosition(index: number): ProductTrackPosition {
    return this.productTrackPositions()[index] ?? { atStart: true, atEnd: true };
  }

  protected scrollProductTrack(track: HTMLElement, direction: -1 | 1): void {
    track.scrollTo({
      left: track.scrollLeft + direction * track.clientWidth,
      behavior: this.reducedMotion() ? 'auto' : 'smooth',
    });
  }

  protected productTrackKeydown(event: KeyboardEvent, track: HTMLElement): void {
    if (event.target !== track) return;
    if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
      event.preventDefault();
      this.scrollProductTrack(track, event.key === 'ArrowLeft' ? -1 : 1);
    } else if (event.key === 'Home' || event.key === 'End') {
      event.preventDefault();
      track.scrollTo({ left: event.key === 'Home' ? 0 : track.scrollWidth, behavior: this.reducedMotion() ? 'auto' : 'smooth' });
    }
  }

  protected updateProductTrackPosition(index: number, track: HTMLElement): void {
    const next = {
      atStart: track.scrollLeft <= 1,
      atEnd: track.scrollWidth - track.clientWidth - track.scrollLeft <= 1,
    };
    const current = this.productTrackPositions()[index];
    if (current?.atStart === next.atStart && current.atEnd === next.atEnd) return;
    this.productTrackPositions.update((positions) => ({ ...positions, [index]: next }));
  }

  @HostListener('window:resize')
  protected refreshProductTracks(): void {
    for (const track of this.host.nativeElement.querySelectorAll<HTMLElement>('[data-product-track]')) {
      this.updateProductTrackPosition(Number(track.dataset['productTrack']), track);
    }
  }

  private loadAllProducts() {
    const filters = { search: '', categoryId: null, brandId: null, minPrice: null, maxPrice: null };
    return this.catalog.getProducts(filters, 0, 'name,asc', 100).pipe(
      expand((page) => page.number + 1 < page.totalPages
        ? this.catalog.getProducts(filters, page.number + 1, 'name,asc', 100)
        : EMPTY),
      reduce((products, page) => [...products, ...page.content], [] as Product[]),
    );
  }

  private reducedMotion(): boolean {
    return globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
  }
}

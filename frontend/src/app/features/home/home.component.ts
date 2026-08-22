import { ChangeDetectionStrategy, Component, CUSTOM_ELEMENTS_SCHEMA, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { CartService } from '../../core/cart/cart.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';
import { BannerCarouselComponent, BannerSlide } from '../../shared/ui/banner-carousel/banner-carousel.component';
import { AppProductCardComponent } from '../../shared/ui/product-card/app-product-card.component';
import { CatalogService, Product, ProductVariant } from '../catalog/catalog.service';

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

@Component({
  selector: 'app-home',
  imports: [AppButtonDirective, AppProductCardComponent, BannerCarouselComponent, RouterLink],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent {
  private readonly catalog = inject(CatalogService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);
  protected readonly cart = inject(CartService);

  protected readonly featured = signal<Product[]>([]);
  protected readonly peripheralCategoryId = signal<number | null>(null);
  protected readonly heroIndex = signal(0);
  protected readonly heroPaused = signal(false);
  protected readonly isLoading = signal(true);
  protected readonly error = signal(false);
  protected readonly heroSlides: readonly BannerSlide[] = [
    { src: '/pinatech-banner-home.jpg', alt: 'Pinatech, tecnología a tu alcance, junto a componentes de hardware', width: 2000, height: 848 },
    { src: '/pinatech-banner-cart.jpg', alt: 'Carrito de compras Pinatech cargado con componentes de hardware', width: 2000, height: 848 },
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
  protected readonly activeHeroPanel = computed(() => this.heroPanels[this.heroIndex()] ?? this.heroPanels[0]);
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
        products: peripheralProducts.slice(0, 2),
        linkLabel: 'Ver todos los periféricos',
        queryParams: peripheralCategoryId === null ? null : { category: peripheralCategoryId },
      },
      {
        eyebrow: 'Hardware',
        title: 'Potencia para tu equipo',
        description: 'Procesadores, placas de video, memorias y almacenamiento para tu próxima actualización.',
        banner: { src: '/pinatech-banner-hardware.jpg', alt: 'Hardware Pinatech: computadora de escritorio y periféricos', width: 2000, height: 848 },
        products: hardwareProducts.slice(0, 2),
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
      products: this.catalog.getProducts({ search: '', categoryId: null, brandId: null, minPrice: null, maxPrice: null }, 0),
    })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isLoading.set(false)),
      )
      .subscribe({
        next: ({ categories, products }) => {
          this.peripheralCategoryId.set(categories.find((category) => category.slug === 'perifericos')?.id ?? null);
          this.featured.set(products.content);
        },
        error: () => this.error.set(true),
      });
  }

  protected add(product: Product, variant: ProductVariant): void {
    if (!variant.inStock) { this.notifications.warning('El color seleccionado no tiene stock disponible.'); return; }
    const result = this.cart.add(product, variant);
    const notification = result.added === 0
      ? this.notifications.warning(`Ya tenés las ${result.limit} ${result.limit === 1 ? 'unidad disponible' : 'unidades disponibles'} para este color en el carrito.`, 'Ver carrito')
      : this.notifications.success(`${product.name} en color ${variant.colorName} se agregó al carrito.`, 'Ver carrito');
    notification.onAction().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => void this.router.navigateByUrl('/cart'));
  }

  protected selectHero(index: number): void {
    this.heroIndex.set(index);
  }
}

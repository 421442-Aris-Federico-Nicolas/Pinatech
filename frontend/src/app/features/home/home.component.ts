import { ChangeDetectionStrategy, Component, CUSTOM_ELEMENTS_SCHEMA, DestroyRef, ElementRef, HostListener, Injector, afterNextRender, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';
import { AppFeedbackComponent } from '../../shared/ui/feedback/app-feedback.component';
import { BannerCarouselComponent, BannerSlide } from '../../shared/ui/banner-carousel/banner-carousel.component';
import { AppProductCardComponent } from '../../shared/ui/product-card/app-product-card.component';
import { HomeHeroSlide, HomeSection, HomeSectionsService, resolveHomeBannerUrl } from './home-sections.service';

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

interface RenderedHeroSlide {
  readonly slide: BannerSlide;
  readonly panel: HeroPanel;
  readonly showLoginLink: boolean;
}

const FALLBACK_SLIDES: readonly RenderedHeroSlide[] = [
  {
    slide: { src: '/pinatech-banner-home.jpg', mobileSrc: '/pinatech-banner-home-mobile.jpg', alt: 'Pinatech, tecnología a tu alcance, junto a componentes de hardware', width: 2000, height: 848 },
    panel: {
      eyebrow: 'Pinatech tecnología',
      title: 'Elevá tu setup.',
      accent: 'Elegí con claridad.',
      description: 'Hardware y periféricos con disponibilidad real para armar o actualizar tu equipo.',
      link: '/catalog',
      linkLabel: 'Explorar catálogo',
    },
    showLoginLink: true,
  },
  {
    slide: { src: '/pinatech-banner-cart.jpg', mobileSrc: '/pinatech-banner-cart-mobile.jpg', alt: 'Carrito de compras Pinatech cargado con componentes de hardware', width: 2000, height: 848 },
    panel: {
      eyebrow: 'Tu selección te espera',
      title: 'No dejes que tu carrito',
      accent: 'se pierda.',
      description: 'Revisá tus productos, ajustá las cantidades y continuá cuando estés listo.',
      link: '/cart',
      linkLabel: 'Ver mi carrito',
    },
    showLoginLink: false,
  },
];

@Component({
  selector: 'app-home',
  imports: [AppButtonDirective, AppFeedbackComponent, AppProductCardComponent, BannerCarouselComponent, RouterLink],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent {
  private readonly sectionsService = inject(HomeSectionsService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  protected readonly auth = inject(AuthService);

  protected readonly sections = signal<HomeSection[]>([]);
  protected readonly heroIndex = signal(0);
  protected readonly heroPointerPaused = signal(false);
  protected readonly heroFocusPaused = signal(false);
  protected readonly isLoading = signal(true);
  protected readonly error = signal(false);
  protected readonly productTrackPositions = signal<Record<number, ProductTrackPosition>>({});
  protected readonly renderedHero = signal<readonly RenderedHeroSlide[]>([]);
  protected readonly heroSlides = computed<readonly BannerSlide[]>(() => this.renderedHero().map((item) => item.slide));
  protected readonly heroPanels = computed<readonly HeroPanel[]>(() => this.renderedHero().map((item) => item.panel));
  protected readonly activeHeroPanel = computed<HeroPanel | null>(() => this.heroPanels()[this.heroIndex()] ?? this.heroPanels()[0] ?? null);
  protected readonly activeSlideLogin = computed<boolean>(() => this.renderedHero()[this.heroIndex()]?.showLoginLink ?? false);
  protected readonly bannerUrl = resolveHomeBannerUrl;

  constructor() {
    this.loadSections();
    this.loadHero();
  }

  protected loadSections(): void {
    this.isLoading.set(true);
    this.error.set(false);

    this.sectionsService.sections()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isLoading.set(false)),
      )
      .subscribe({
        next: (sections) => {
          this.sections.set(sections);
          afterNextRender({ read: () => this.refreshProductTracks() }, { injector: this.injector });
        },
        error: () => this.error.set(true),
      });
  }

  protected loadHero(): void {
    this.sectionsService.heroSlides()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (slides) => {
          const rendered = slides.map((slide) => this.renderHero(slide)).filter((item): item is RenderedHeroSlide => item !== null);
          this.renderedHero.set(rendered.length ? rendered : FALLBACK_SLIDES);
        },
        error: () => this.renderedHero.set(FALLBACK_SLIDES),
      });
  }

  private renderHero(slide: HomeHeroSlide): RenderedHeroSlide | null {
    const desktop = slide.desktopImage;
    const mobile = slide.mobileImage;
    const primary = desktop ?? mobile;
    if (!primary) return null;
    return {
      slide: {
        src: resolveHomeBannerUrl(primary.url),
        mobileSrc: desktop && mobile ? resolveHomeBannerUrl(mobile.url) : undefined,
        alt: slide.altText,
        width: primary.width,
        height: primary.height,
      },
      panel: {
        eyebrow: slide.eyebrow,
        title: slide.title,
        accent: slide.accent,
        description: slide.description,
        link: slide.link,
        linkLabel: slide.linkLabel,
      },
      showLoginLink: slide.showLoginLink,
    };
  }

  protected selectHero(index: number): void {
    this.heroIndex.set(index);
  }

  protected resumeHeroAfterFocus(event: FocusEvent): void {
    const hero = event.currentTarget as HTMLElement | null;
    if (!hero?.contains(event.relatedTarget as Node | null)) this.heroFocusPaused.set(false);
  }

  protected productTrackPosition(sectionId: number): ProductTrackPosition {
    return this.productTrackPositions()[sectionId] ?? { atStart: true, atEnd: true };
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

  protected updateProductTrackPosition(sectionId: number, track: HTMLElement): void {
    const next = {
      atStart: track.scrollLeft <= 1,
      atEnd: track.scrollWidth - track.clientWidth - track.scrollLeft <= 1,
    };
    const current = this.productTrackPositions()[sectionId];
    if (current?.atStart === next.atStart && current.atEnd === next.atEnd) return;
    this.productTrackPositions.update((positions) => ({ ...positions, [sectionId]: next }));
  }

  @HostListener('window:resize')
  protected refreshProductTracks(): void {
    for (const track of this.host.nativeElement.querySelectorAll<HTMLElement>('[data-product-track]')) {
      this.updateProductTrackPosition(Number(track.dataset['productTrack']), track);
    }
  }

  protected catalogQuery(section: HomeSection): Record<string, string> | null {
    const categoryIds = section.categories.map((category) => category.id);
    return categoryIds.length ? { category: categoryIds.join(',') } : null;
  }

  private reducedMotion(): boolean {
    return globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
  }
}

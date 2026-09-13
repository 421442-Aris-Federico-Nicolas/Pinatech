import { ChangeDetectionStrategy, Component, CUSTOM_ELEMENTS_SCHEMA, DestroyRef, ElementRef, HostListener, Injector, afterNextRender, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';
import { AppFeedbackComponent } from '../../shared/ui/feedback/app-feedback.component';
import { BannerCarouselComponent, BannerSlide } from '../../shared/ui/banner-carousel/banner-carousel.component';
import { AppProductCardComponent } from '../../shared/ui/product-card/app-product-card.component';
import { HomeHeroImage, HomeHeroSlide, HomeSection, HomeSectionsService, resolveCurrentHomeHeroVariantUrl, resolveHomeBannerUrl, resolveHomeHeroVariantUrl } from './home-sections.service';

const HERO_IMAGE_WIDTHS = [480, 720, 1280, 1920] as const;

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
          const rendered: RenderedHeroSlide[] = [];
          for (const slide of slides) {
            const item = this.renderHero(slide, rendered.length === 0);
            if (item) rendered.push(item);
          }
          this.renderedHero.set(rendered);
        },
        error: () => this.renderedHero.set([]),
      });
  }

  private renderHero(slide: HomeHeroSlide, current: boolean): RenderedHeroSlide | null {
    const desktop = slide.desktopImage;
    const mobile = slide.mobileImage;
    const primary = desktop ?? mobile;
    if (!primary) return null;
    const desktopSources = current ? this.currentHeroSources('DESKTOP') : this.heroSources(primary);
    const mobileSources = current
      ? this.currentHeroSources('MOBILE')
      : desktop && mobile ? this.heroSources(mobile) : null;
    const mobileDimensions = mobile ?? primary;
    return {
      slide: {
        src: desktopSources.src,
        srcset: desktopSources.srcset,
        mobileSrc: mobileSources?.src,
        mobileSrcset: mobileSources?.srcset,
        mobileWidth: mobileSources ? mobileDimensions.width : undefined,
        mobileHeight: mobileSources ? mobileDimensions.height : undefined,
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

  private heroSources(image: HomeHeroImage): { src: string; srcset: string } {
    const widths = HERO_IMAGE_WIDTHS.filter((width) => width <= image.width);
    if (!widths.length) widths.push(HERO_IMAGE_WIDTHS[0]);
    return {
      src: resolveHomeHeroVariantUrl(image.id, widths.at(-1)!),
      srcset: widths.map((width) => `${resolveHomeHeroVariantUrl(image.id, width)} ${width}w`).join(', '),
    };
  }

  private currentHeroSources(device: 'DESKTOP' | 'MOBILE'): { src: string; srcset?: string } {
    return { src: resolveCurrentHomeHeroVariantUrl(device, device === 'MOBILE' ? 720 : 1920) };
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

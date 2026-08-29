import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, output, signal } from '@angular/core';

export interface BannerSlide {
  readonly src: string;
  readonly alt: string;
  readonly width: number;
  readonly height: number;
}

@Component({
  selector: 'app-banner-carousel',
  templateUrl: './banner-carousel.component.html',
  styleUrl: './banner-carousel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BannerCarouselComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);
  private readonly documentHidden = signal(this.document.hidden);
  private readonly timerReset = signal(0);
  private pointerAutoplayValue: boolean | null = null;

  readonly slides = input.required<readonly BannerSlide[]>();
  readonly ariaLabel = input('Banners destacados');
  readonly imagePriority = input(false);
  readonly paused = input(false);
  readonly autoplayDelay = input(7000);
  readonly indexChange = output<number>();
  readonly activeIndex = signal(0);
  readonly reducedMotion = signal(false);
  readonly userPaused = signal(false);
  readonly autoplayPaused = computed(() => this.paused() || this.userPaused());
  readonly driftDuration = computed(() => `${Math.max(this.autoplayDelay(), 6000)}ms`);

  constructor() {
    const view = this.document.defaultView;
    const motionQuery = view?.matchMedia?.('(prefers-reduced-motion: reduce)');
    const updateVisibility = () => this.documentHidden.set(this.document.hidden);
    this.document.addEventListener('visibilitychange', updateVisibility);
    this.destroyRef.onDestroy(() => this.document.removeEventListener('visibilitychange', updateVisibility));

    if (motionQuery) {
      const updateReducedMotion = (event: MediaQueryListEvent) => this.reducedMotion.set(event.matches);
      this.reducedMotion.set(motionQuery.matches);
      motionQuery.addEventListener('change', updateReducedMotion);
      this.destroyRef.onDestroy(() => motionQuery.removeEventListener('change', updateReducedMotion));
    }

    effect((onCleanup) => {
      const slides = this.slides();
      const delay = this.autoplayDelay();
      this.timerReset();
      if (!view || slides.length < 2 || delay <= 0 || this.autoplayPaused() || this.reducedMotion() || this.documentHidden()) return;

      const timer = view.setInterval(() => this.move(1, false), delay);
      onCleanup(() => view.clearInterval(timer));
    });
  }

  previous(): void {
    this.move(-1);
  }

  next(): void {
    this.move(1);
  }

  select(index: number): void {
    const slides = this.slides();
    if (!slides.length || index < 0 || index >= slides.length || index === this.activeIndex()) return;
    this.activate(index, true);
  }

  prepareAutoplayToggle(): void {
    this.pointerAutoplayValue = !this.userPaused();
  }

  toggleAutoplay(event?: MouseEvent): void {
    const nextValue = event && event.detail > 0 && this.pointerAutoplayValue !== null
      ? this.pointerAutoplayValue
      : !this.userPaused();
    this.pointerAutoplayValue = null;
    this.userPaused.set(nextValue);
  }

  pauseAutoplay(): void {
    this.userPaused.set(true);
  }

  private move(change: number, manual = true): void {
    const total = this.slides().length;
    if (total < 2) return;
    const current = this.activeIndex();
    const next = (current + change + total) % total;
    this.activate(next, manual);
  }

  private activate(index: number, manual: boolean): void {
    this.activeIndex.set(index);
    this.indexChange.emit(index);
    if (manual) this.timerReset.update((value) => value + 1);
  }
}

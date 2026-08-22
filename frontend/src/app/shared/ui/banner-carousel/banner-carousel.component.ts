import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, input, output, signal } from '@angular/core';

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

  readonly slides = input.required<readonly BannerSlide[]>();
  readonly ariaLabel = input('Banners destacados');
  readonly imagePriority = input(false);
  readonly paused = input(false);
  readonly autoplayDelay = input(7000);
  readonly indexChange = output<number>();
  readonly activeIndex = signal(0);
  readonly enteringIndex = signal<number | null>(null);
  readonly leavingIndex = signal<number | null>(null);
  readonly direction = signal<1 | -1>(1);

  constructor() {
    const view = this.document.defaultView;
    const updateVisibility = () => this.documentHidden.set(this.document.hidden);
    this.document.addEventListener('visibilitychange', updateVisibility);
    this.destroyRef.onDestroy(() => this.document.removeEventListener('visibilitychange', updateVisibility));

    effect((onCleanup) => {
      const slides = this.slides();
      const delay = this.autoplayDelay();
      this.timerReset();
      if (!view || slides.length < 2 || delay <= 0 || this.paused() || this.documentHidden()) return;

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
    this.startTransition(index, index > this.activeIndex() ? 1 : -1);
    this.activate(index, true);
  }

  finishTransition(index: number): void {
    if (index !== this.enteringIndex()) return;
    this.enteringIndex.set(null);
    this.leavingIndex.set(null);
  }

  private move(change: number, manual = true): void {
    const total = this.slides().length;
    if (total < 2) return;
    const current = this.activeIndex();
    const next = (current + change + total) % total;
    this.startTransition(next, change > 0 ? 1 : -1);
    this.activate(next, manual);
  }

  private startTransition(next: number, direction: 1 | -1): void {
    this.direction.set(direction);
    this.leavingIndex.set(this.activeIndex());
    this.enteringIndex.set(next);
  }

  private activate(index: number, manual: boolean): void {
    this.activeIndex.set(index);
    this.indexChange.emit(index);
    if (manual) this.timerReset.update((value) => value + 1);
  }
}

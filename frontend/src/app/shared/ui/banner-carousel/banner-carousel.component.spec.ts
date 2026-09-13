import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, vi } from 'vitest';
import { BannerCarouselComponent, BannerSlide } from './banner-carousel.component';

interface ControlledMediaQuery {
  readonly query: MediaQueryList;
  readonly addEventListener: ReturnType<typeof vi.fn>;
  readonly removeEventListener: ReturnType<typeof vi.fn>;
  change(matches: boolean): void;
}

describe('BannerCarouselComponent', () => {
  const slides: readonly BannerSlide[] = [
    { src: '/first.jpg', srcset: '/first-480.jpg 480w, /first-1920.jpg 1920w', mobileSrc: '/first-mobile.jpg', mobileSrcset: '/first-mobile-480.jpg 480w, /first-mobile-720.jpg 720w', mobileWidth: 720, mobileHeight: 512, alt: 'Primer banner', width: 2000, height: 848 },
    { src: '/second.jpg', alt: 'Segundo banner', width: 2000, height: 848 },
    { src: '/third.jpg', alt: 'Tercer banner', width: 2000, height: 848 },
  ];
  const originalMatchMedia = Object.getOwnPropertyDescriptor(window, 'matchMedia');
  let motionQuery: ControlledMediaQuery;

  function installMatchMedia(initialMatches: boolean): ControlledMediaQuery {
    let matches = initialMatches;
    const listeners = new Set<(event: MediaQueryListEvent) => void>();
    const addEventListener = vi.fn((type: string, listener: EventListenerOrEventListenerObject) => {
      if (type === 'change' && typeof listener === 'function') listeners.add(listener as (event: MediaQueryListEvent) => void);
    });
    const removeEventListener = vi.fn((type: string, listener: EventListenerOrEventListenerObject) => {
      if (type === 'change' && typeof listener === 'function') listeners.delete(listener as (event: MediaQueryListEvent) => void);
    });
    const query = {
      get matches() { return matches; },
      media: '(prefers-reduced-motion: reduce)',
      onchange: null,
      addEventListener,
      removeEventListener,
    } as unknown as MediaQueryList;

    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn(() => query),
    });

    return {
      query,
      addEventListener,
      removeEventListener,
      change(nextMatches: boolean) {
        matches = nextMatches;
        const event = { matches, media: query.media } as MediaQueryListEvent;
        listeners.forEach((listener) => listener(event));
      },
    };
  }

  function createCarousel(
    carouselSlides: readonly BannerSlide[] = slides,
    autoplayDelay = 7000,
    imagePriority = false,
  ): ComponentFixture<BannerCarouselComponent> {
    const fixture = TestBed.createComponent(BannerCarouselComponent);
    fixture.componentRef.setInput('slides', carouselSlides);
    fixture.componentRef.setInput('autoplayDelay', autoplayDelay);
    fixture.componentRef.setInput('imagePriority', imagePriority);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(async () => {
    motionQuery = installMatchMedia(false);
    await TestBed.configureTestingModule({ imports: [BannerCarouselComponent] }).compileComponents();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    if (originalMatchMedia) Object.defineProperty(window, 'matchMedia', originalMatchMedia);
    else delete (window as Window & { matchMedia?: typeof window.matchMedia }).matchMedia;
  });

  it('updates immediately during rapid navigation and preserves wrapping, output and dot semantics', () => {
    const fixture = createCarousel(slides, 0, true);
    const changes: number[] = [];
    fixture.componentInstance.indexChange.subscribe((index) => changes.push(index));
    const carousel = fixture.nativeElement.querySelector('.banner-carousel') as HTMLElement;
    const next = fixture.nativeElement.querySelector('.banner-carousel__control.next') as HTMLButtonElement;
    const initialImage = fixture.nativeElement.querySelector('img') as HTMLImageElement;

    expect(initialImage.getAttribute('src')).toBe('/first.jpg');
    expect(initialImage.getAttribute('loading')).toBe('eager');
    expect(initialImage.getAttribute('fetchpriority')).toBe('high');
    expect(initialImage.getAttribute('srcset')).toContain('/first-1920.jpg 1920w');
    const mobileSource = fixture.nativeElement.querySelector('source');
    expect(mobileSource.getAttribute('srcset')).toContain('/first-mobile-720.jpg 720w');
    expect(mobileSource.getAttribute('media')).toBe('(max-width: 620px)');
    expect(mobileSource.getAttribute('sizes')).toBe('100vw');
    expect(mobileSource.getAttribute('width')).toBe('720');
    expect(mobileSource.getAttribute('height')).toBe('512');
    expect(initialImage.getAttribute('sizes')).toBe('100vw');

    next.click();
    next.click();
    expect(fixture.componentInstance.activeIndex()).toBe(2);

    carousel.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    expect(fixture.componentInstance.activeIndex()).toBe(0);
    fixture.componentInstance.previous();
    expect(fixture.componentInstance.activeIndex()).toBe(2);
    fixture.detectChanges();

    const dots = fixture.nativeElement.querySelectorAll('.banner-carousel__dots button') as NodeListOf<HTMLButtonElement>;
    dots[1].click();
    fixture.detectChanges();

    expect(fixture.componentInstance.activeIndex()).toBe(1);
    expect(changes).toEqual([1, 2, 0, 2, 1]);
    expect(fixture.nativeElement.querySelectorAll('img.active')).toHaveLength(1);
    expect(fixture.nativeElement.querySelector('img.active').getAttribute('src')).toBe('/second.jpg');
    expect(fixture.nativeElement.querySelector('img.entering')).toBeNull();
    expect(fixture.nativeElement.querySelector('img.leaving')).toBeNull();
    expect(dots[1].getAttribute('aria-current')).toBe('true');
    expect(dots[1].getAttribute('aria-label')).toBe('Ver banner 2 de 3');

    const images = fixture.nativeElement.querySelectorAll('img') as NodeListOf<HTMLImageElement>;
    expect(images).toHaveLength(1);
    expect(images[0].getAttribute('loading')).toBe('lazy');
    expect(images[0].getAttribute('fetchpriority')).toBeNull();
    expect(images[0].getAttribute('aria-hidden')).toBeNull();
    expect(fixture.nativeElement.querySelector('source')).toBeNull();
    expect(fixture.nativeElement.querySelector('.banner-carousel__label')).toBeNull();
  });

  it('autoplays, honors the paused input and restarts with a full delay', async () => {
    vi.useFakeTimers();
    const fixture = createCarousel(slides, 1000);
    TestBed.tick();

    await vi.advanceTimersByTimeAsync(1000);
    expect(fixture.componentInstance.activeIndex()).toBe(1);

    fixture.componentRef.setInput('paused', true);
    fixture.detectChanges();
    TestBed.tick();
    await vi.advanceTimersByTimeAsync(3000);
    expect(fixture.componentInstance.activeIndex()).toBe(1);

    fixture.componentRef.setInput('paused', false);
    fixture.detectChanges();
    TestBed.tick();
    await vi.advanceTimersByTimeAsync(999);
    expect(fixture.componentInstance.activeIndex()).toBe(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(fixture.componentInstance.activeIndex()).toBe(2);
  });

  it('binds the drift duration and visual pause state to the carousel root', () => {
    const fixture = createCarousel(slides, 4000);
    const carousel = fixture.nativeElement.querySelector('.banner-carousel') as HTMLElement;

    expect(carousel.style.getPropertyValue('--carousel-drift-duration')).toBe('6000ms');
    expect(carousel.classList).toContain('has-drift');
    expect(carousel.classList).not.toContain('is-paused');

    fixture.componentRef.setInput('paused', true);
    fixture.componentRef.setInput('autoplayDelay', 8000);
    fixture.detectChanges();

    expect(carousel.style.getPropertyValue('--carousel-drift-duration')).toBe('8000ms');
    expect(carousel.classList).toContain('is-paused');
  });

  it('resets the full autoplay delay after manual navigation', async () => {
    vi.useFakeTimers();
    const fixture = createCarousel(slides, 1000);
    TestBed.tick();

    await vi.advanceTimersByTimeAsync(600);
    fixture.componentInstance.next();
    TestBed.tick();
    await vi.advanceTimersByTimeAsync(999);
    expect(fixture.componentInstance.activeIndex()).toBe(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(fixture.componentInstance.activeIndex()).toBe(2);
  });

  it('pauses while the document is hidden and restarts after it becomes visible', async () => {
    vi.useFakeTimers();
    let hidden = false;
    vi.spyOn(document, 'hidden', 'get').mockImplementation(() => hidden);
    const fixture = createCarousel(slides, 1000);
    TestBed.tick();

    hidden = true;
    document.dispatchEvent(new Event('visibilitychange'));
    TestBed.tick();
    await vi.advanceTimersByTimeAsync(2000);
    expect(fixture.componentInstance.activeIndex()).toBe(0);

    hidden = false;
    document.dispatchEvent(new Event('visibilitychange'));
    TestBed.tick();
    await vi.advanceTimersByTimeAsync(999);
    expect(fixture.componentInstance.activeIndex()).toBe(0);
    await vi.advanceTimersByTimeAsync(1);
    expect(fixture.componentInstance.activeIndex()).toBe(1);
  });

  it('tracks reduced motion changes, disables autoplay and cleans up its listener', async () => {
    vi.useFakeTimers();
    motionQuery = installMatchMedia(true);
    const fixture = createCarousel(slides, 1000);
    TestBed.tick();

    expect(fixture.componentInstance.reducedMotion()).toBe(true);
    expect(fixture.componentInstance.autoplayPaused()).toBe(false);
    await vi.advanceTimersByTimeAsync(2000);
    expect(fixture.componentInstance.activeIndex()).toBe(0);

    motionQuery.change(false);
    fixture.detectChanges();
    TestBed.tick();
    expect(fixture.componentInstance.reducedMotion()).toBe(false);
    expect(fixture.nativeElement.querySelector('.banner-carousel__autoplay')).toBeNull();
    await vi.advanceTimersByTimeAsync(1000);
    expect(fixture.componentInstance.activeIndex()).toBe(1);

    motionQuery.change(true);
    fixture.detectChanges();
    TestBed.tick();
    await vi.advanceTimersByTimeAsync(2000);
    expect(fixture.componentInstance.activeIndex()).toBe(1);
    expect(fixture.nativeElement.querySelector('.banner-carousel__autoplay')).toBeNull();

    const listener = motionQuery.addEventListener.mock.calls[0][1];
    fixture.destroy();
    expect(motionQuery.removeEventListener).toHaveBeenCalledWith('change', listener);
  });

  it('never renders a persistent pause control', () => {
    const oneSlide = createCarousel([slides[0]]);
    const disabledAutoplay = createCarousel(slides, 0);

    expect(oneSlide.nativeElement.querySelector('.banner-carousel__autoplay')).toBeNull();
    expect(disabledAutoplay.nativeElement.querySelector('.banner-carousel__autoplay')).toBeNull();
    expect((oneSlide.nativeElement.querySelector('.banner-carousel') as HTMLElement).classList).not.toContain('has-drift');
    expect((disabledAutoplay.nativeElement.querySelector('.banner-carousel') as HTMLElement).classList).not.toContain('has-drift');
  });
});

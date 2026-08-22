import { TestBed } from '@angular/core/testing';
import { afterEach, vi } from 'vitest';
import { BannerCarouselComponent, BannerSlide } from './banner-carousel.component';

describe('BannerCarouselComponent', () => {
  const slides: readonly BannerSlide[] = [
    { src: '/first.jpg', alt: 'Primer banner', width: 2000, height: 848 },
    { src: '/second.jpg', alt: 'Segundo banner', width: 2000, height: 848 },
  ];

  afterEach(() => vi.useRealTimers());

  it('changes slides with controls and keyboard navigation', async () => {
    await TestBed.configureTestingModule({ imports: [BannerCarouselComponent] }).compileComponents();
    const fixture = TestBed.createComponent(BannerCarouselComponent);
    const changes: number[] = [];
    fixture.componentRef.setInput('slides', slides);
    fixture.componentInstance.indexChange.subscribe((index) => changes.push(index));
    fixture.detectChanges();

    const carousel = fixture.nativeElement.querySelector('.banner-carousel') as HTMLElement;
    const next = fixture.nativeElement.querySelector('.banner-carousel__control.next') as HTMLButtonElement;
    next.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.activeIndex()).toBe(1);
    expect(changes).toEqual([1]);
    expect(fixture.nativeElement.querySelector('img.active').getAttribute('src')).toBe('/second.jpg');
    expect(fixture.nativeElement.querySelector('img.entering').getAttribute('src')).toBe('/second.jpg');
    expect(fixture.nativeElement.querySelector('img.leaving').getAttribute('src')).toBe('/first.jpg');

    carousel.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    fixture.detectChanges();
    expect(fixture.componentInstance.activeIndex()).toBe(0);

    (fixture.nativeElement.querySelector('img.entering') as HTMLElement).dispatchEvent(new Event('animationend'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('img.entering')).toBeNull();
    expect(fixture.nativeElement.querySelector('img.leaving')).toBeNull();
  });

  it('autoplays continuously and honors its paused input', async () => {
    vi.useFakeTimers();
    await TestBed.configureTestingModule({ imports: [BannerCarouselComponent] }).compileComponents();
    const fixture = TestBed.createComponent(BannerCarouselComponent);
    fixture.componentRef.setInput('slides', slides);
    fixture.detectChanges();
    TestBed.tick();
    expect(vi.getTimerCount()).toBeGreaterThan(0);

    await vi.advanceTimersByTimeAsync(6999);
    fixture.detectChanges();
    expect(fixture.componentInstance.activeIndex()).toBe(0);
    await vi.advanceTimersByTimeAsync(1);
    fixture.detectChanges();
    expect(fixture.componentInstance.activeIndex()).toBe(1);

    fixture.componentRef.setInput('paused', true);
    fixture.detectChanges();
    TestBed.tick();
    await vi.advanceTimersByTimeAsync(14000);
    fixture.detectChanges();
    expect(fixture.componentInstance.activeIndex()).toBe(1);

    fixture.componentRef.setInput('paused', false);
    fixture.detectChanges();
    TestBed.tick();
    await vi.advanceTimersByTimeAsync(7000);
    fixture.detectChanges();
    expect(fixture.componentInstance.activeIndex()).toBe(0);
  });
});

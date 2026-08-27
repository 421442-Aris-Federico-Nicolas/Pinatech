import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, vi } from 'vitest';
import { PinatechEmptyStateComponent } from './pinatech-empty-state.component';

@Component({
  imports: [PinatechEmptyStateComponent],
  template: `<app-pinatech-empty-state [title]="title()" [message]="message()"><button type="button" (click)="cleared.set(true)">Limpiar filtros</button></app-pinatech-empty-state>`,
})
class EmptyStateHostComponent {
  readonly title = signal('Sin coincidencias');
  readonly message = signal('Cambiá la búsqueda.');
  readonly cleared = signal(false);
}

describe('PinatechEmptyStateComponent', () => {
  afterEach(() => vi.restoreAllMocks());

  it('renders a polite atomic status with signal input copy and a projected action', async () => {
    await TestBed.configureTestingModule({ imports: [EmptyStateHostComponent] }).compileComponents();
    const fixture = TestBed.createComponent(EmptyStateHostComponent);
    fixture.detectChanges();

    const status = fixture.nativeElement.querySelector('.empty-state') as HTMLElement;
    const button = status.querySelector('button') as HTMLButtonElement;

    expect(status.getAttribute('role')).toBe('status');
    expect(status.getAttribute('aria-live')).toBe('polite');
    expect(status.getAttribute('aria-atomic')).toBe('true');
    expect(status.querySelector('h2')?.textContent).toBe('Sin coincidencias');
    const stage = status.querySelector('.mascot-stage') as HTMLElement;
    const sprite = stage.querySelector('.sprite-preload') as HTMLImageElement;
    expect(stage.getAttribute('aria-hidden')).toBe('true');
    expect(stage.classList).not.toContain('is-ready');
    expect(sprite.getAttribute('src')).toBe('/pinatech-not-found-sprite.png');
    sprite.dispatchEvent(new Event('load'));
    fixture.detectChanges();
    expect(stage.classList).toContain('is-ready');
    button.click();
    expect(fixture.componentInstance.cleared()).toBe(true);
  });

  it('provides the approved default copy', async () => {
    await TestBed.configureTestingModule({ imports: [PinatechEmptyStateComponent] }).compileComponents();
    const fixture = TestBed.createComponent(PinatechEmptyStateComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('h2')?.textContent).toBe('No encontramos productos');
    expect(fixture.nativeElement.querySelector('p')?.textContent).toContain('Probá con otros términos');
  });

  it('starts the animation when the sprite is already cached', async () => {
    vi.spyOn(HTMLImageElement.prototype, 'complete', 'get').mockReturnValue(true);
    vi.spyOn(HTMLImageElement.prototype, 'naturalWidth', 'get').mockReturnValue(2176);
    await TestBed.configureTestingModule({ imports: [PinatechEmptyStateComponent] }).compileComponents();
    const fixture = TestBed.createComponent(PinatechEmptyStateComponent);

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.mascot-stage')?.classList).toContain('is-ready');
  });
});

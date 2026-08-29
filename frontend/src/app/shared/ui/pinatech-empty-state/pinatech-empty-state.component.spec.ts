import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
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
    const image = status.querySelector('.mascot-image') as HTMLImageElement;
    expect(image.getAttribute('aria-hidden')).toBe('true');
    expect(image.getAttribute('src')).toBe('/pinatech-not-found.png?v=transparent-1');
    expect(image.getAttribute('width')).toBe('1254');
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
});

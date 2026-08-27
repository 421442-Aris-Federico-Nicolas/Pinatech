import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, vi } from 'vitest';
import { PinatechSaleSuccessComponent } from './pinatech-sale-success.component';

@Component({
  imports: [PinatechSaleSuccessComponent],
  template: `<app-pinatech-sale-success [title]="title()" [message]="message()" [orderNumber]="orderNumber()"><a href="/orders">Ver pedido</a></app-pinatech-sale-success>`,
})
class SaleSuccessHostComponent {
  readonly title = signal('Pago aprobado');
  readonly message = signal('El servidor confirmó el pago.');
  readonly orderNumber = signal<string | number | null>(42);
}

describe('PinatechSaleSuccessComponent', () => {
  afterEach(() => vi.restoreAllMocks());

  it('renders the final approval copy and order as a polite atomic status', async () => {
    await TestBed.configureTestingModule({ imports: [SaleSuccessHostComponent] }).compileComponents();
    const fixture = TestBed.createComponent(SaleSuccessHostComponent);
    fixture.detectChanges();

    const status = fixture.nativeElement.querySelector('.success-state') as HTMLElement;
    expect(status.getAttribute('role')).toBe('status');
    expect(status.getAttribute('aria-live')).toBe('polite');
    expect(status.getAttribute('aria-atomic')).toBe('true');
    expect(status.querySelector('h1')?.textContent).toBe('Pago aprobado');
    expect(status.querySelector('.order-number')?.textContent).toContain('Pedido #42');
    const celebration = status.querySelector('.celebration') as HTMLElement;
    const sprite = celebration.querySelector('.sprite-preload') as HTMLImageElement;
    expect(celebration.getAttribute('aria-hidden')).toBe('true');
    expect(celebration.classList).not.toContain('is-ready');
    expect(sprite.getAttribute('src')).toBe('/pinatech-success-sale-sprite.png');
    sprite.dispatchEvent(new Event('load'));
    fixture.detectChanges();
    expect(celebration.classList).toContain('is-ready');
    expect(status.querySelector('a')?.textContent).toBe('Ver pedido');
  });

  it('omits the order label when no order number is provided', async () => {
    await TestBed.configureTestingModule({ imports: [PinatechSaleSuccessComponent] }).compileComponents();
    const fixture = TestBed.createComponent(PinatechSaleSuccessComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.order-number')).toBeNull();
    expect(fixture.nativeElement.querySelector('h1')?.textContent).toBe('¡Venta concretada!');
  });

  it('starts the animation when the sprite is already cached', async () => {
    vi.spyOn(HTMLImageElement.prototype, 'complete', 'get').mockReturnValue(true);
    vi.spyOn(HTMLImageElement.prototype, 'naturalWidth', 'get').mockReturnValue(2176);
    await TestBed.configureTestingModule({ imports: [PinatechSaleSuccessComponent] }).compileComponents();
    const fixture = TestBed.createComponent(PinatechSaleSuccessComponent);

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.celebration')?.classList).toContain('is-ready');
  });
});

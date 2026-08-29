import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
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
    const image = status.querySelector('.success-image') as HTMLImageElement;
    expect(image.getAttribute('aria-hidden')).toBe('true');
    expect(image.getAttribute('src')).toBe('/pinatech-success-sale.png');
    expect(status.querySelector('a')?.textContent).toBe('Ver pedido');
  });

  it('omits the order label when no order number is provided', async () => {
    await TestBed.configureTestingModule({ imports: [PinatechSaleSuccessComponent] }).compileComponents();
    const fixture = TestBed.createComponent(PinatechSaleSuccessComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.order-number')).toBeNull();
    expect(fixture.nativeElement.querySelector('h1')?.textContent).toBe('¡Venta concretada!');
  });
});

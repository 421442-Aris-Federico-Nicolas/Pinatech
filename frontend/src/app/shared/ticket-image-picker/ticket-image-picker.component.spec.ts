import { TestBed } from '@angular/core/testing';
import { MAT_BOTTOM_SHEET_DATA, MatBottomSheet, MatBottomSheetRef } from '@angular/material/bottom-sheet';
import { of } from 'rxjs';
import { TicketImagePickerComponent, TicketImageSourceSheetComponent } from './ticket-image-picker.component';

describe('TicketImagePickerComponent', () => {
  it('keeps the desktop picker and returns files selected from the mobile sheet', async () => {
    const file = new File(['image'], 'equipo.jpg', { type: 'image/jpeg' });
    const open = vi.fn(() => ({ afterDismissed: () => of([file]) }));
    await TestBed.configureTestingModule({
      imports: [TicketImagePickerComponent],
      providers: [{ provide: MatBottomSheet, useValue: { open } }],
    }).compileComponents();
    const fixture = TestBed.createComponent(TicketImagePickerComponent);
    fixture.componentRef.setInput('label', 'Agregar imágenes');
    fixture.componentRef.setInput('inputName', 'newTicketImages');
    fixture.componentRef.setInput('multiple', true);
    const selected = vi.fn();
    fixture.componentInstance.filesSelected.subscribe(selected);
    fixture.detectChanges();

    const desktop = fixture.nativeElement.querySelector('[name="newTicketImages"]') as HTMLInputElement;
    expect(desktop.multiple).toBe(true);
    expect(desktop.accept).toContain('image/heic');

    (fixture.nativeElement.querySelector('.mobile-picker') as HTMLButtonElement).click();

    expect(open).toHaveBeenCalledWith(TicketImageSourceSheetComponent, expect.objectContaining({ data: { multiple: true }, ariaModal: true }));
    expect(selected).toHaveBeenCalledWith([file]);
  });
});

describe('TicketImageSourceSheetComponent', () => {
  it('offers the rear camera and a multiple gallery picker', async () => {
    const dismiss = vi.fn();
    await TestBed.configureTestingModule({
      imports: [TicketImageSourceSheetComponent],
      providers: [
        { provide: MAT_BOTTOM_SHEET_DATA, useValue: { multiple: true } },
        { provide: MatBottomSheetRef, useValue: { dismiss } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(TicketImageSourceSheetComponent);
    fixture.detectChanges();
    const inputs = fixture.nativeElement.querySelectorAll('input[type="file"]') as NodeListOf<HTMLInputElement>;

    expect(inputs[0].getAttribute('capture')).toBe('environment');
    expect(inputs[1].multiple).toBe(true);

    const file = new File(['image'], 'camara.jpg', { type: 'image/jpeg' });
    Object.defineProperty(inputs[0], 'files', { configurable: true, value: [file] });
    inputs[0].dispatchEvent(new Event('change'));

    expect(dismiss).toHaveBeenCalledWith([file]);
  });
});

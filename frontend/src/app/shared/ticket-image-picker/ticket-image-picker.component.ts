import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { MAT_BOTTOM_SHEET_DATA, MatBottomSheet, MatBottomSheetRef } from '@angular/material/bottom-sheet';
import { take } from 'rxjs';

const ACCEPTED_IMAGES = 'image/jpeg,image/png,image/heic,image/heif,.heic,.heif';

interface ImageSourceSheetData {
  multiple: boolean;
}

@Component({
  selector: 'app-ticket-image-source-sheet',
  template: `
    <section class="source-sheet" aria-labelledby="image-source-title">
      <div class="sheet-handle" aria-hidden="true"></div>
      <header>
        <p>Adjuntar evidencia</p>
        <h2 id="image-source-title">¿De dónde querés agregarla?</h2>
      </header>
      <div class="source-options">
        <label>
          <span class="source-mark camera" aria-hidden="true"></span>
          <strong>Sacar foto</strong>
          <small>Usar la cámara trasera</small>
          <input type="file" accept="image/*" capture="environment" (change)="select($event)">
        </label>
        <label>
          <span class="source-mark gallery" aria-hidden="true"></span>
          <strong>Elegir de galería</strong>
          <small>{{ data.multiple ? 'Seleccionar una o varias' : 'Seleccionar una imagen' }}</small>
          <input type="file" [accept]="acceptedImages" [multiple]="data.multiple" (change)="select($event)">
        </label>
      </div>
      <button class="cancel" type="button" (click)="dismiss()">Cancelar</button>
    </section>
  `,
  styles: [`
    :host { color: var(--color-text); display: block; }
    .source-sheet { padding: .4rem var(--space-4) max(var(--space-4), env(safe-area-inset-bottom)); }
    .sheet-handle { background: var(--color-border-strong); border-radius: 99px; height: 4px; margin: .25rem auto var(--space-4); width: 42px; }
    header p { color: var(--color-cyan); font-size: var(--text-xs); font-weight: var(--font-weight-black); letter-spacing: .13em; margin: 0; text-transform: uppercase; }
    h2 { font-size: clamp(var(--text-xl), 5vw, var(--text-2xl)); line-height: 1.15; margin: .35rem 0 var(--space-4); }
    .source-options { display: grid; gap: var(--space-3); grid-template-columns: 1fr 1fr; }
    label { background: var(--color-surface-raised); border: 1px solid var(--color-border-strong); border-radius: var(--radius-md); cursor: pointer; display: grid; gap: .3rem; min-height: 132px; padding: var(--space-4); position: relative; touch-action: manipulation; }
    label:active { background: var(--color-surface-hover); border-color: var(--color-cyan-strong); }
    label:has(input:focus-visible) { box-shadow: var(--focus-shadow); outline: var(--focus-outline); outline-offset: var(--focus-offset); }
    label strong { color: var(--color-text-strong); font-size: var(--text-md); margin-top: auto; }
    label small { color: var(--color-text-subtle); font-size: var(--text-xs); }
    label input { height: 1px; opacity: 0; position: absolute; width: 1px; }
    .source-mark { border: 2px solid var(--color-cyan); border-radius: 8px; display: block; height: 32px; position: relative; width: 38px; }
    .source-mark.camera::before { border: 2px solid var(--color-orange); border-radius: 50%; content: ''; height: 11px; left: 50%; position: absolute; top: 50%; transform: translate(-50%, -50%); width: 11px; }
    .source-mark.camera::after { background: var(--color-cyan); border-radius: 3px 3px 0 0; content: ''; height: 5px; left: 7px; position: absolute; top: -6px; width: 12px; }
    .source-mark.gallery::before { border-bottom: 2px solid var(--color-orange); border-left: 2px solid var(--color-orange); content: ''; height: 13px; left: 8px; position: absolute; top: 8px; transform: rotate(-45deg); width: 18px; }
    .cancel { background: transparent; border: 0; color: var(--color-text-muted); cursor: pointer; font-weight: var(--font-weight-bold); margin-top: var(--space-3); min-height: var(--control-min-height); width: 100%; }
    @media (max-width: 390px) { .source-options { grid-template-columns: 1fr; } label { min-height: 108px; } }
    @media (prefers-reduced-motion: reduce) { label:active { background: var(--color-surface-raised); } }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TicketImageSourceSheetComponent {
  readonly data = inject<ImageSourceSheetData>(MAT_BOTTOM_SHEET_DATA);
  readonly acceptedImages = ACCEPTED_IMAGES;
  private readonly sheetRef = inject(MatBottomSheetRef<TicketImageSourceSheetComponent, File[]>);

  select(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    if (files.length) this.sheetRef.dismiss(files);
  }

  dismiss(): void {
    this.sheetRef.dismiss();
  }
}

@Component({
  selector: 'app-ticket-image-picker',
  template: `
    <label class="desktop-picker" [class.disabled]="disabled()">
      {{ label() }}
      <input type="file" [attr.name]="inputName()" [accept]="acceptedImages" [multiple]="multiple()" [disabled]="disabled()" (change)="selectDesktop($event)">
    </label>
    <button class="mobile-picker" type="button" aria-haspopup="dialog" [disabled]="disabled()" (click)="openMobilePicker()">{{ label() }}</button>
  `,
  styleUrl: './ticket-image-picker.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TicketImagePickerComponent {
  readonly label = input('Agregar imagen');
  readonly inputName = input('ticketImage');
  readonly multiple = input(false);
  readonly disabled = input(false);
  readonly filesSelected = output<File[]>();
  readonly acceptedImages = ACCEPTED_IMAGES;
  private readonly bottomSheet = inject(MatBottomSheet);

  selectDesktop(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    if (files.length) this.filesSelected.emit(files);
  }

  openMobilePicker(): void {
    if (this.disabled()) return;
    this.bottomSheet.open<TicketImageSourceSheetComponent, ImageSourceSheetData, File[]>(TicketImageSourceSheetComponent, {
      ariaLabel: 'Elegir cámara o galería',
      ariaModal: true,
      autoFocus: 'first-tabbable',
      backdropClass: 'ticket-image-source-backdrop',
      data: { multiple: this.multiple() },
      panelClass: 'ticket-image-source-panel',
      restoreFocus: true,
    }).afterDismissed().pipe(take(1)).subscribe((files) => {
      if (files?.length) this.filesSelected.emit(files);
    });
  }
}

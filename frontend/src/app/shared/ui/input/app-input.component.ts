import { ChangeDetectionStrategy, Component, ElementRef, booleanAttribute, computed, forwardRef, input, signal, viewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

export type AppInputType = 'text' | 'email' | 'password' | 'tel' | 'search' | 'number';
export type AppInputValue = string | number | null;

let inputId = 0;

@Component({
  selector: 'app-input',
  host: { tabindex: '-1', '(focus)': 'focusControl()' },
  template: `
    <label class="app-field" [class.app-field--disabled]="isDisabled()">
      @if (label()) { <span class="app-field__label">{{ label() }} @if (required()) { <span class="app-field__required" aria-hidden="true">*</span> }</span> }
      <input
        #control
        class="app-field__control"
        [id]="controlId"
        [type]="type()"
        [name]="name()"
        [value]="displayValue()"
        [placeholder]="placeholder()"
        [autocomplete]="autocomplete()"
        [attr.spellcheck]="spellcheck()"
        [attr.inputmode]="inputMode()"
        [attr.min]="min()"
        [attr.max]="max()"
        [attr.step]="step()"
        [attr.maxlength]="maxLength()"
        [required]="required()"
        [disabled]="isDisabled()"
        [attr.aria-label]="ariaLabel() || label() || null"
        [attr.aria-describedby]="describedBy()"
        [attr.aria-invalid]="error() ? 'true' : null"
        (input)="handleInput($event)"
        (blur)="markTouched()">
      @if (hint()) { <small class="app-field__hint" [id]="hintId">{{ hint() }}</small> }
      @if (error()) { <small class="app-field__error" [id]="errorId" role="alert">{{ error() }}</small> }
    </label>
  `,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => AppInputComponent), multi: true }],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppInputComponent implements ControlValueAccessor {
  readonly label = input('');
  readonly type = input<AppInputType>('text');
  readonly name = input('');
  readonly placeholder = input('');
  readonly autocomplete = input('off');
  readonly spellcheck = input<string | null>(null);
  readonly inputMode = input<string | null>(null);
  readonly min = input<string | number | null>(null);
  readonly max = input<string | number | null>(null);
  readonly step = input<string | number | null>(null);
  readonly maxLength = input<number | null>(null);
  readonly hint = input('');
  readonly error = input('');
  readonly ariaLabel = input('', { alias: 'aria-label' });
  readonly ariaDescribedBy = input('', { alias: 'aria-describedby' });
  readonly required = input(false, { transform: booleanAttribute });
  readonly disabled = input(false, { transform: booleanAttribute });

  protected readonly controlId = `app-input-${++inputId}`;
  protected readonly hintId = `${this.controlId}-hint`;
  protected readonly errorId = `${this.controlId}-error`;
  private readonly value = signal<AppInputValue>('');
  private readonly control = viewChild.required<ElementRef<HTMLInputElement>>('control');
  private readonly formDisabled = signal(false);
  protected readonly displayValue = computed(() => this.value() ?? '');
  protected readonly isDisabled = computed(() => this.disabled() || this.formDisabled());
  protected readonly describedBy = computed(() => [
    this.ariaDescribedBy(),
    this.hint() ? this.hintId : '',
    this.error() ? this.errorId : '',
  ].filter(Boolean).join(' ') || null);
  private onChange: (value: AppInputValue) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  writeValue(value: AppInputValue): void { this.value.set(value ?? ''); }
  registerOnChange(fn: (value: AppInputValue) => void): void { this.onChange = fn; }
  registerOnTouched(fn: () => void): void { this.onTouched = fn; }
  setDisabledState(disabled: boolean): void { this.formDisabled.set(disabled); }

  protected handleInput(event: Event): void {
    const inputElement = event.target as HTMLInputElement;
    const value = this.type() === 'number'
      ? inputElement.value === '' || Number.isNaN(inputElement.valueAsNumber) ? null : inputElement.valueAsNumber
      : inputElement.value;
    this.value.set(value);
    this.onChange(value);
  }

  protected markTouched(): void { this.onTouched(); }
  protected focusControl(): void { this.control().nativeElement.focus(); }
}

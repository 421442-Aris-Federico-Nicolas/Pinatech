import { ChangeDetectionStrategy, Component, ElementRef, booleanAttribute, computed, forwardRef, input, signal, viewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

export interface AppSelectOption {
  readonly value: unknown;
  readonly label: string;
  readonly disabled?: boolean;
}

let selectId = 0;

@Component({
  selector: 'app-select',
  host: { tabindex: '-1', '(focus)': 'focusControl()' },
  template: `
    <label class="app-field" [class.app-field--disabled]="isDisabled()">
      @if (label()) { <span class="app-field__label">{{ label() }} @if (required()) { <span class="app-field__required" aria-hidden="true">*</span> }</span> }
      <select
        #control
        class="app-field__control"
        [id]="controlId"
        [name]="name()"
        [value]="selectedIndex()"
        [required]="required()"
        [disabled]="isDisabled()"
        [attr.aria-label]="ariaLabel() || label() || null"
        [attr.aria-describedby]="describedBy()"
        [attr.aria-invalid]="error() ? 'true' : null"
        (change)="handleChange($event)"
        (blur)="markTouched()">
        @if (placeholder()) { <option value="-1" [disabled]="required()" [selected]="selectedIndex() === -1">{{ placeholder() }}</option> }
        @for (option of options(); track $index) {
          <option [value]="$index" [disabled]="option.disabled" [selected]="$index === selectedIndex()">{{ option.label }}</option>
        }
      </select>
      @if (hint()) { <small class="app-field__hint" [id]="hintId">{{ hint() }}</small> }
      @if (error()) { <small class="app-field__error" [id]="errorId" role="alert">{{ error() }}</small> }
    </label>
  `,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => AppSelectComponent), multi: true }],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppSelectComponent implements ControlValueAccessor {
  readonly label = input('');
  readonly name = input('');
  readonly placeholder = input('');
  readonly options = input.required<readonly AppSelectOption[]>();
  readonly hint = input('');
  readonly error = input('');
  readonly ariaLabel = input('', { alias: 'aria-label' });
  readonly ariaDescribedBy = input('', { alias: 'aria-describedby' });
  readonly required = input(false, { transform: booleanAttribute });
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly compareWith = input<(left: unknown, right: unknown) => boolean>(Object.is);

  protected readonly controlId = `app-select-${++selectId}`;
  protected readonly hintId = `${this.controlId}-hint`;
  protected readonly errorId = `${this.controlId}-error`;
  private readonly value = signal<unknown>(null);
  private readonly control = viewChild.required<ElementRef<HTMLSelectElement>>('control');
  private readonly formDisabled = signal(false);
  protected readonly isDisabled = computed(() => this.disabled() || this.formDisabled());
  protected readonly selectedIndex = computed(() => this.options().findIndex((option) => this.compareWith()(option.value, this.value())));
  protected readonly describedBy = computed(() => [
    this.ariaDescribedBy(),
    this.hint() ? this.hintId : '',
    this.error() ? this.errorId : '',
  ].filter(Boolean).join(' ') || null);
  private onChange: (value: unknown) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  writeValue(value: unknown): void { this.value.set(value); }
  registerOnChange(fn: (value: unknown) => void): void { this.onChange = fn; }
  registerOnTouched(fn: () => void): void { this.onTouched = fn; }
  setDisabledState(disabled: boolean): void { this.formDisabled.set(disabled); }

  protected handleChange(event: Event): void {
    const index = Number((event.target as HTMLSelectElement).value);
    const value = index >= 0 ? this.options()[index]?.value ?? null : null;
    this.value.set(value);
    this.onChange(value);
  }

  protected markTouched(): void { this.onTouched(); }
  protected focusControl(): void { this.control().nativeElement.focus(); }
}

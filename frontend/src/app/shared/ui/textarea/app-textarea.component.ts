import { ChangeDetectionStrategy, Component, ElementRef, booleanAttribute, computed, forwardRef, input, signal, viewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

let textareaId = 0;

@Component({
  selector: 'app-textarea',
  host: { tabindex: '-1', '(focus)': 'focusControl()' },
  template: `
    <label class="app-field" [class.app-field--disabled]="isDisabled()">
      @if (label()) { <span class="app-field__label">{{ label() }} @if (required()) { <span class="app-field__required" aria-hidden="true">*</span> }</span> }
      <textarea
        #control
        class="app-field__control"
        [id]="controlId"
        [name]="name()"
        [value]="value()"
        [rows]="rows()"
        [placeholder]="placeholder()"
        [autocomplete]="autocomplete()"
        [attr.spellcheck]="spellcheck()"
        [attr.maxlength]="maxLength()"
        [required]="required()"
        [disabled]="isDisabled()"
        [attr.aria-label]="ariaLabel() || label() || null"
        [attr.aria-describedby]="describedBy()"
        [attr.aria-invalid]="error() ? 'true' : null"
        (input)="handleInput($event)"
        (blur)="markTouched()"></textarea>
      @if (hint()) { <small class="app-field__hint" [id]="hintId">{{ hint() }}</small> }
      @if (error()) { <small class="app-field__error" [id]="errorId">{{ error() }}</small> }
    </label>
  `,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => AppTextareaComponent), multi: true }],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppTextareaComponent implements ControlValueAccessor {
  readonly label = input('');
  readonly name = input('');
  readonly rows = input(4);
  readonly placeholder = input('');
  readonly autocomplete = input('off');
  readonly spellcheck = input<string | null>(null);
  readonly maxLength = input<number | null>(null);
  readonly hint = input('');
  readonly error = input('');
  readonly ariaLabel = input('', { alias: 'aria-label' });
  readonly ariaDescribedBy = input('', { alias: 'aria-describedby' });
  readonly required = input(false, { transform: booleanAttribute });
  readonly disabled = input(false, { transform: booleanAttribute });

  protected readonly controlId = `app-textarea-${++textareaId}`;
  protected readonly hintId = `${this.controlId}-hint`;
  protected readonly errorId = `${this.controlId}-error`;
  protected readonly value = signal('');
  private readonly control = viewChild.required<ElementRef<HTMLTextAreaElement>>('control');
  private readonly formDisabled = signal(false);
  protected readonly isDisabled = computed(() => this.disabled() || this.formDisabled());
  protected readonly describedBy = computed(() => [
    this.ariaDescribedBy(),
    this.hint() ? this.hintId : '',
    this.error() ? this.errorId : '',
  ].filter(Boolean).join(' ') || null);
  private onChange: (value: string) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  writeValue(value: string | null): void { this.value.set(value ?? ''); }
  registerOnChange(fn: (value: string) => void): void { this.onChange = fn; }
  registerOnTouched(fn: () => void): void { this.onTouched = fn; }
  setDisabledState(disabled: boolean): void { this.formDisabled.set(disabled); }

  protected handleInput(event: Event): void {
    const value = (event.target as HTMLTextAreaElement).value;
    this.value.set(value);
    this.onChange(value);
  }

  protected markTouched(): void { this.onTouched(); }
  protected focusControl(): void { this.control().nativeElement.focus(); }
}

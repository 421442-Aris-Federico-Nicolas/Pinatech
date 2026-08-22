import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { AppBadgeDirective } from './app-badge.directive';
import { AppButtonDirective } from './app-button.directive';
import { AppCardDirective } from './app-card.directive';
import { AppInputComponent } from './input/app-input.component';
import { AppSelectComponent, AppSelectOption } from './select/app-select.component';
import { AppTextareaComponent } from './textarea/app-textarea.component';

@Component({
  imports: [
    AppBadgeDirective,
    AppButtonDirective,
    AppCardDirective,
    AppInputComponent,
    AppSelectComponent,
    AppTextareaComponent,
    FormsModule,
    ReactiveFormsModule,
  ],
  template: `
    <form [formGroup]="form">
      <app-input label="Nombre" formControlName="name" spellcheck="false" />
      <app-select label="Opción" formControlName="choice" [options]="options" />
    </form>
    <app-textarea label="Notas" autocomplete="off" spellcheck="true" [(ngModel)]="notes" [ngModelOptions]="{ standalone: true }" />
    <article appCard><span appBadge="warning">Pendiente</span><button appButton="outlined">Acción</button></article>
  `,
})
class UiControlsHostComponent {
  readonly form = new FormGroup({
    name: new FormControl('Ada', { nonNullable: true }),
    choice: new FormControl<unknown>(2),
  });
  readonly options: readonly AppSelectOption[] = [
    { value: 1, label: 'Uno' },
    { value: 2, label: 'Dos' },
  ];
  notes = 'Inicial';
}

describe('shared UI controls', () => {
  it('works with reactive and template-driven forms', async () => {
    await TestBed.configureTestingModule({ imports: [UiControlsHostComponent] }).compileComponents();
    const fixture = TestBed.createComponent(UiControlsHostComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    const textarea = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    expect(input.value).toBe('Ada');
    expect(input.getAttribute('spellcheck')).toBe('false');
    expect(select.value).toBe('1');
    expect(textarea.value).toBe('Inicial');
    expect(textarea.autocomplete).toBe('off');
    expect(textarea.getAttribute('spellcheck')).toBe('true');

    (fixture.nativeElement.querySelector('app-input') as HTMLElement).focus();
    expect(document.activeElement).toBe(input);

    input.value = 'Grace';
    input.dispatchEvent(new Event('input', { bubbles: true }));
    select.value = '0';
    select.dispatchEvent(new Event('change', { bubbles: true }));
    textarea.value = 'Actualizada';
    textarea.dispatchEvent(new Event('input', { bubbles: true }));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.componentInstance.form.getRawValue()).toEqual({ name: 'Grace', choice: 1 });
    expect(fixture.componentInstance.notes).toBe('Actualizada');
  });

  it('forwards disabled state and applies primitive appearances', async () => {
    await TestBed.configureTestingModule({ imports: [UiControlsHostComponent] }).compileComponents();
    const fixture = TestBed.createComponent(UiControlsHostComponent);
    fixture.componentInstance.form.controls.name.disable();
    fixture.detectChanges();

    expect((fixture.nativeElement.querySelector('input') as HTMLInputElement).disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('article').classList).toContain('app-card');
    expect(fixture.nativeElement.querySelector('article span').classList).toContain('app-badge--warning');
    expect(fixture.nativeElement.querySelector('button').classList).toContain('app-button--outlined');
  });
});

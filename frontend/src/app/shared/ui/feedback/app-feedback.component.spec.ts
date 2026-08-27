import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AppFeedbackAnnouncement, AppFeedbackComponent, AppFeedbackTone } from './app-feedback.component';

@Component({
  imports: [AppFeedbackComponent],
  template: `<app-feedback [tone]="tone()" [announce]="announce()"><strong>Resultado</strong><button feedback-actions type="button" (click)="acted.set(true)">Reintentar</button></app-feedback>`,
})
class FeedbackHostComponent {
  readonly tone = signal<AppFeedbackTone>('info');
  readonly announce = signal<AppFeedbackAnnouncement>('off');
  readonly acted = signal(false);
}

describe('AppFeedbackComponent', () => {
  it.each(['info', 'success', 'warning', 'error'] as const)('renders the %s tone', async (tone) => {
    await TestBed.configureTestingModule({ imports: [FeedbackHostComponent] }).compileComponents();
    const fixture = TestBed.createComponent(FeedbackHostComponent);
    fixture.componentInstance.tone.set(tone);
    fixture.detectChanges();

    const feedback = fixture.nativeElement.querySelector('app-feedback') as HTMLElement;
    expect(feedback.dataset['tone']).toBe(tone);
    expect(feedback.querySelector('iconify-icon')).not.toBeNull();
  });

  it.each([
    { announce: 'off', role: null },
    { announce: 'polite', role: 'status' },
    { announce: 'assertive', role: 'alert' },
  ] as const)('maps $announce to its semantic role', async ({ announce, role }) => {
    await TestBed.configureTestingModule({ imports: [FeedbackHostComponent] }).compileComponents();
    const fixture = TestBed.createComponent(FeedbackHostComponent);
    fixture.componentInstance.announce.set(announce);
    fixture.detectChanges();

    const feedback = fixture.nativeElement.querySelector('app-feedback') as HTMLElement;
    const announcement = feedback.querySelector('.app-feedback__body') as HTMLElement;
    expect(feedback.getAttribute('role')).toBeNull();
    expect(announcement.getAttribute('role')).toBe(role);
    expect(announcement.getAttribute('aria-live')).toBeNull();
  });

  it('projects an executable action without adding dismissal or timer controls', async () => {
    await TestBed.configureTestingModule({ imports: [FeedbackHostComponent] }).compileComponents();
    const fixture = TestBed.createComponent(FeedbackHostComponent);
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    button.click();
    expect(fixture.componentInstance.acted()).toBe(true);
    expect(button.closest('.app-feedback__actions')).not.toBeNull();
    expect(button.closest('[role]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[aria-label*="Cerrar"]')).toBeNull();
  });
});

import { TestBed } from '@angular/core/testing';
import { consumeActionToken } from './action-token';

describe('consumeActionToken', () => {
  afterEach(() => history.replaceState(null, '', '/'));

  it('consumes a fragment token in memory and immediately removes it from the URL', () => {
    history.replaceState({ navigationId: 1 }, '', '/reset-password?source=email#token=secret-token');

    const token = TestBed.runInInjectionContext(() => consumeActionToken());

    expect(token).toBe('secret-token');
    expect(location.pathname + location.search + location.hash).toBe('/reset-password?source=email');
    expect(history.state).toEqual({ navigationId: 1 });
  });

  it('temporarily accepts a query token and removes only sensitive token parameters', () => {
    history.replaceState(null, '', '/verify-email?token=legacy-token&source=dev#step=confirm');

    const token = TestBed.runInInjectionContext(() => consumeActionToken());

    expect(token).toBe('legacy-token');
    expect(location.pathname + location.search + location.hash).toBe('/verify-email?source=dev#step=confirm');
  });
});

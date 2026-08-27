import { DOCUMENT } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, vi } from 'vitest';
import { DeploymentVersionService } from './deployment-version.service';

describe('DeploymentVersionService', () => {
  let visibilityState: DocumentVisibilityState;
  let visibilityListener: EventListener | null;
  let reload: ReturnType<typeof vi.fn>;
  let service: DeploymentVersionService;
  let http: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers();
    visibilityState = 'visible';
    visibilityListener = null;
    reload = vi.fn();
    const documentStub = {
      get visibilityState() { return visibilityState; },
      defaultView: { location: { reload } },
      querySelector: vi.fn((selector: string) => selector === 'meta[name="pinatech-deployment-version"]'
        ? { content: 'build-a' }
        : null),
      addEventListener: vi.fn((type: string, listener: EventListener) => {
        if (type === 'visibilitychange') visibilityListener = listener;
      }),
      removeEventListener: vi.fn(),
    } as unknown as Document;

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DOCUMENT, useValue: documentStub },
      ],
    });
    service = TestBed.inject(DeploymentVersionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    service.ngOnDestroy();
    http.verify();
    vi.useRealTimers();
  });

  it('uses the first version as its baseline and reports a later version once', () => {
    service.initialize();
    flushVersion('build-a');
    expect(service.updateAvailable()).toBe(false);

    vi.advanceTimersByTime(60_000);
    flushVersion('build-a');
    expect(service.updateAvailable()).toBe(false);

    vi.advanceTimersByTime(60_000);
    flushVersion('build-b');
    expect(service.updateAvailable()).toBe(true);
    expect(reload).not.toHaveBeenCalled();

    vi.advanceTimersByTime(60_000);
    flushVersion('build-c');
    expect(service.updateAvailable()).toBe(true);
    expect(reload).not.toHaveBeenCalled();
  });

  it('retains the embedded build after a network failure and detects the next deployment', () => {
    service.initialize();
    http.expectOne(versionRequest).error(new ProgressEvent('network error'));
    expect(service.updateAvailable()).toBe(false);

    vi.advanceTimersByTime(59_999);
    http.expectNone(versionRequest);
    vi.advanceTimersByTime(1);
    flushVersion('build-b');
    expect(service.updateAvailable()).toBe(true);
  });

  it('checks when the page becomes visible but not when it becomes hidden', () => {
    service.initialize();
    flushVersion('build-a');

    visibilityState = 'hidden';
    visibilityListener?.(new Event('visibilitychange'));
    http.expectNone(versionRequest);

    visibilityState = 'visible';
    visibilityListener?.(new Event('visibilitychange'));
    flushVersion('build-b');
    expect(service.updateAvailable()).toBe(true);
  });

  it('reloads only when the explicit action method is called', () => {
    service.initialize();
    flushVersion('build-a');
    vi.advanceTimersByTime(60_000);
    flushVersion('build-b');

    expect(reload).not.toHaveBeenCalled();
    service.reload();
    expect(reload).toHaveBeenCalledOnce();
  });

  function flushVersion(version: string): void {
    const request = http.expectOne(versionRequest);
    expect(request.request.method).toBe('GET');
    request.flush({ version });
  }

  function versionRequest(request: { url: string }): boolean {
    return request.url === '/version.json';
  }
});

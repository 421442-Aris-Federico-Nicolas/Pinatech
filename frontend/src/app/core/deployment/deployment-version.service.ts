import { DOCUMENT } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { inject, Injectable, OnDestroy, signal } from '@angular/core';
import { finalize, Subscription, take } from 'rxjs';

interface DeploymentVersionResponse {
  version: string;
}

@Injectable({ providedIn: 'root' })
export class DeploymentVersionService implements OnDestroy {
  private static readonly pollIntervalMs = 60_000;
  private readonly http = inject(HttpClient);
  private readonly document = inject(DOCUMENT);
  private readonly updateAvailableState = signal(false);
  private baselineVersion = this.document
    .querySelector<HTMLMetaElement>('meta[name="pinatech-deployment-version"]')
    ?.content.trim() || null;
  private pollHandle: ReturnType<typeof setInterval> | null = null;
  private request: Subscription | null = null;
  private initialized = false;
  readonly updateAvailable = this.updateAvailableState.asReadonly();

  initialize(): void {
    if (this.initialized) return;
    this.initialized = true;
    this.document.addEventListener('visibilitychange', this.onVisibilityChange);
    this.checkForUpdate();
    this.pollHandle = setInterval(() => this.checkForUpdate(), DeploymentVersionService.pollIntervalMs);
  }

  reload(): void {
    this.document.defaultView?.location.reload();
  }

  ngOnDestroy(): void {
    if (this.pollHandle !== null) clearInterval(this.pollHandle);
    this.pollHandle = null;
    this.request?.unsubscribe();
    this.request = null;
    this.document.removeEventListener('visibilitychange', this.onVisibilityChange);
  }

  private readonly onVisibilityChange = (): void => {
    if (this.document.visibilityState === 'visible') this.checkForUpdate();
  };

  private checkForUpdate(): void {
    if (this.request && !this.request.closed) return;

    this.request = this.http.get<DeploymentVersionResponse>('/version.json', {
      params: { _: Date.now().toString() },
    }).pipe(
      take(1),
      finalize(() => { this.request = null; }),
    ).subscribe({
      next: ({ version }) => this.acceptVersion(version),
      error: () => undefined,
    });
  }

  private acceptVersion(version: unknown): void {
    if (typeof version !== 'string' || !version.trim()) return;
    const normalizedVersion = version.trim();
    if (this.baselineVersion === null) {
      this.baselineVersion = normalizedVersion;
      return;
    }
    if (normalizedVersion !== this.baselineVersion) this.updateAvailableState.set(true);
  }
}

import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { finalize } from 'rxjs';
import { HealthResponse } from '../../core/models/health-response';
import { ApiHealthService } from '../../core/services/api-health.service';

@Component({
  selector: 'app-home',
  imports: [MatButtonModule, MatCardModule],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent {
  private readonly apiHealthService = inject(ApiHealthService);

  protected readonly health = signal<HealthResponse | null>(null);
  protected readonly isLoading = signal(true);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.checkApi();
  }

  protected checkApi(): void {
    this.isLoading.set(true);
    this.error.set(null);

    this.apiHealthService
      .getHealth()
      .pipe(
        takeUntilDestroyed(),
        finalize(() => this.isLoading.set(false)),
      )
      .subscribe({
        next: (response) => this.health.set(response),
        error: () => this.error.set('No se pudo conectar con la API local.'),
      });
  }
}

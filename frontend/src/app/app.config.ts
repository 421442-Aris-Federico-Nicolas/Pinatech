import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localeEsAr from '@angular/common/locales/es-AR';
import { ApplicationConfig, inject, LOCALE_ID, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { DeploymentVersionService } from './core/deployment/deployment-version.service';
import { authInterceptor } from './core/interceptors/auth.interceptor';

registerLocaleData(localeEsAr);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideAnimationsAsync(),
    provideAppInitializer(() => inject(AuthService).restoreSession()),
    provideAppInitializer(() => inject(DeploymentVersionService).initialize()),
    { provide: LOCALE_ID, useValue: 'es-AR' },
  ]
};

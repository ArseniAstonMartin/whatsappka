import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { TitleStrategy, provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { RussianTitleStrategy } from './core/russian-title-strategy';
import { authInterceptor } from './core/auth.interceptor';
import { AuthService } from './core/auth.service';
import { RealtimeService } from './core/realtime/realtime.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor])),
    // Сессия восстанавливается до первой навигации, иначе guard увидит гостя после перезагрузки.
    provideAppInitializer(() => inject(AuthService).restore()),
    // Создание сервиса запускает слежение за входом: STOMP подключается после входа и отключается при выходе.
    provideAppInitializer(() => {
      inject(RealtimeService);
    }),
    provideRouter(routes),
    { provide: TitleStrategy, useClass: RussianTitleStrategy },
  ]
};

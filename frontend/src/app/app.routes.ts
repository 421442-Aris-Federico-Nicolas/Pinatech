import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    title: 'Computer Store',
    loadComponent: () => import('./features/home/home.component').then((component) => component.HomeComponent),
  },
  { path: 'login', title: 'Iniciar sesión', loadComponent: () => import('./features/auth/login.component').then((component) => component.LoginComponent) },
  { path: 'profile', title: 'Mi perfil', canActivate: [authGuard], loadComponent: () => import('./features/home/home.component').then((component) => component.HomeComponent) },
  {
    path: '**',
    redirectTo: '',
  },
];

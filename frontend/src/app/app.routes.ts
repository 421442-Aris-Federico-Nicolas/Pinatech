import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    title: 'Computer Store',
    loadComponent: () => import('./features/home/home.component').then((component) => component.HomeComponent),
  },
  {
    path: '**',
    redirectTo: '',
  },
];

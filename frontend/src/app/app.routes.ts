import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { adminGuard } from './core/guards/admin.guard';
import { technicalGuard } from './core/guards/technical.guard';

export const routes: Routes = [
  {
    path: '',
    title: 'Computer Store',
    loadComponent: () => import('./features/home/home.component').then((component) => component.HomeComponent),
  },
  { path: 'catalog', title: 'Catálogo', loadComponent: () => import('./features/catalog/catalog.component').then((component) => component.CatalogComponent) },
  { path: 'products/:id', title: 'Producto', loadComponent: () => import('./features/product/product.component').then((component) => component.ProductComponent) },
  { path: 'login', title: 'Iniciar sesión', loadComponent: () => import('./features/auth/login.component').then((component) => component.LoginComponent) },
  { path: 'cart', title: 'Carrito', loadComponent: () => import('./features/cart/cart.component').then((component) => component.CartComponent) },
  { path: 'orders', title: 'Mis pedidos', canActivate: [authGuard], loadComponent: () => import('./features/orders/orders.component').then((component) => component.OrdersComponent) },
  { path: 'tickets', title: 'Servicio técnico', canActivate: [authGuard], loadComponent: () => import('./features/tickets/tickets.component').then((component) => component.TicketsComponent) },
  { path: 'technical', title: 'Panel técnico', canActivate: [authGuard, technicalGuard], loadComponent: () => import('./features/technical/technical.component').then((component) => component.TechnicalComponent) },
  { path: 'profile', title: 'Mi perfil', canActivate: [authGuard], loadComponent: () => import('./features/home/home.component').then((component) => component.HomeComponent) },
  { path: 'admin', title: 'Administración', canActivate: [authGuard, adminGuard], loadComponent: () => import('./features/admin/admin.component').then((component) => component.AdminComponent) },
  {
    path: '**',
    redirectTo: '',
  },
];

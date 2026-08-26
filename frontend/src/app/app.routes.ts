import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { adminGuard } from './core/guards/admin.guard';
import { technicalGuard } from './core/guards/technical.guard';
import { customerGuard } from './core/guards/customer.guard';

export const routes: Routes = [
  {
    path: '',
    title: 'Pinatech | Tecnología y hardware',
    loadComponent: () => import('./features/home/home.component').then((component) => component.HomeComponent),
  },
  { path: 'catalog', title: 'Catálogo | Pinatech', loadComponent: () => import('./features/catalog/catalog.component').then((component) => component.CatalogComponent) },
  { path: 'products/:id', title: 'Producto', loadComponent: () => import('./features/product/product.component').then((component) => component.ProductComponent) },
  { path: 'login', title: 'Iniciar sesión | Pinatech', loadComponent: () => import('./features/auth/login.component').then((component) => component.LoginComponent) },
  { path: 'register', title: 'Crear cuenta | Pinatech', loadComponent: () => import('./features/auth/register.component').then((component) => component.RegisterComponent) },
  { path: 'verify-email', title: 'Verificar email | Pinatech', loadComponent: () => import('./features/account-actions/verify-email.component').then((component) => component.VerifyEmailComponent) },
  { path: 'forgot-password', title: 'Recuperar contraseña | Pinatech', loadComponent: () => import('./features/account-actions/forgot-password.component').then((component) => component.ForgotPasswordComponent) },
  { path: 'reset-password', title: 'Restablecer contraseña | Pinatech', loadComponent: () => import('./features/account-actions/reset-password.component').then((component) => component.ResetPasswordComponent) },
  { path: 'confirm-email-change', title: 'Confirmar cambio de email | Pinatech', loadComponent: () => import('./features/account-actions/confirm-email-change.component').then((component) => component.ConfirmEmailChangeComponent) },
  { path: 'cart', title: 'Carrito | Pinatech', loadComponent: () => import('./features/cart/cart.component').then((component) => component.CartComponent) },
  { path: 'checkout', title: 'Finalizar compra | Pinatech', canActivate: [authGuard, customerGuard], loadComponent: () => import('./features/checkout/checkout.component').then((component) => component.CheckoutComponent) },
  { path: 'checkout/result', title: 'Estado del pago | Pinatech', canActivate: [authGuard, customerGuard], loadComponent: () => import('./features/checkout-result/checkout-result.component').then((component) => component.CheckoutResultComponent) },
  { path: 'orders', title: 'Mis pedidos', canActivate: [authGuard, customerGuard], loadComponent: () => import('./features/orders/orders.component').then((component) => component.OrdersComponent) },
  { path: 'tickets', title: 'Servicio técnico', canActivate: [authGuard], loadComponent: () => import('./features/tickets/tickets.component').then((component) => component.TicketsComponent) },
  { path: 'technical', title: 'Panel técnico', canActivate: [authGuard, technicalGuard], loadComponent: () => import('./features/technical/technical.component').then((component) => component.TechnicalComponent) },
  { path: 'profile', title: 'Mi perfil', canActivate: [authGuard], loadComponent: () => import('./features/profile/profile.component').then((component) => component.ProfileComponent) },
  { path: 'admin', title: 'Administración', canActivate: [authGuard, adminGuard], loadComponent: () => import('./features/admin/admin.component').then((component) => component.AdminComponent) },
  {
    path: '**',
    title: 'Página no encontrada | Pinatech',
    loadComponent: () => import('./features/not-found/not-found.component').then((component) => component.NotFoundComponent),
  },
];

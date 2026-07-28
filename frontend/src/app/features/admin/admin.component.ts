import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, CUSTOM_ELEMENTS_SCHEMA, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { finalize, forkJoin } from 'rxjs';
import { Order } from '../../core/orders/order.service';
import { Product } from '../catalog/catalog.service';
import { AdminService, Brand, Category, Inventory, ProductPayload } from './admin.service';

type AdminSection = 'overview' | 'sales' | 'catalog' | 'inventory';
type OrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'PREPARING' | 'READY' | 'DELIVERED' | 'CANCELLED';
interface ProductForm extends ProductPayload {}
interface OrderAction { label: string; status: OrderStatus; danger?: boolean; }

const REVENUE_STATUSES = new Set<OrderStatus>(['PAID', 'PREPARING', 'READY', 'DELIVERED']);

@Component({
  imports: [DatePipe, DecimalPipe, FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminComponent {
  private readonly service = inject(AdminService);
  readonly section = signal<AdminSection>('overview');
  readonly sidebarCollapsed = signal(false);
  readonly loading = signal(true);
  readonly products = signal<Product[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly brands = signal<Brand[]>([]);
  readonly inventories = signal<Inventory[]>([]);
  readonly orders = signal<Order[]>([]);
  readonly selected = signal<Product | null>(null);
  readonly inventory = signal<Inventory | null>(null);
  readonly expandedOrder = signal<number | null>(null);
  readonly orderFilter = signal<string>('ALL');
  readonly orderUpdating = signal<number | null>(null);
  readonly error = signal('');
  readonly success = signal('');
  readonly saving = signal(false);
  readonly form: ProductForm = this.emptyProduct();
  categoryName = '';
  categorySlug = '';
  brandName = '';
  adjustment = 0;
  adjustmentReason = '';

  readonly soldOrders = computed(() => this.orders().filter((order) => REVENUE_STATUSES.has(order.status as OrderStatus)));
  readonly revenue = computed(() => this.soldOrders().reduce((total, order) => total + order.total, 0));
  readonly averageTicket = computed(() => this.soldOrders().length ? this.revenue() / this.soldOrders().length : 0);
  readonly activeOrders = computed(() => this.orders().filter((order) => !['DELIVERED', 'CANCELLED'].includes(order.status)).length);
  readonly lowStock = computed(() => this.inventories().filter((item) => item.availableQuantity <= 5).length);
  readonly availableUnits = computed(() => this.inventories().reduce((total, item) => total + item.availableQuantity, 0));
  readonly recentOrders = computed(() => this.orders().slice(0, 5));
  readonly filteredOrders = computed(() => this.orderFilter() === 'ALL'
    ? this.orders()
    : this.orders().filter((order) => order.status === this.orderFilter()));
  readonly salesChart = computed(() => {
    const days = Array.from({ length: 7 }, (_, index) => {
      const date = new Date();
      date.setHours(0, 0, 0, 0);
      date.setDate(date.getDate() - (6 - index));
      const total = this.soldOrders()
        .filter((order) => new Date(order.createdAt).toDateString() === date.toDateString())
        .reduce((sum, order) => sum + order.total, 0);
      return { label: new Intl.DateTimeFormat('es-AR', { weekday: 'short' }).format(date).replace('.', ''), total };
    });
    const maximum = Math.max(...days.map((day) => day.total), 1);
    return days.map((day) => ({ ...day, height: day.total ? Math.max(12, day.total / maximum * 100) : 4 }));
  });

  constructor() { this.reload(); }

  reload(): void {
    this.loading.set(true);
    forkJoin({
      products: this.service.products(),
      categories: this.service.categories(),
      brands: this.service.brands(),
      inventories: this.service.inventories(),
      orders: this.service.orders(),
    }).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: ({ products, categories, brands, inventories, orders }) => {
        this.products.set(products.content);
        this.categories.set(categories);
        this.brands.set(brands);
        this.inventories.set(inventories);
        this.orders.set(orders);
        const selectedId = this.selected()?.id;
        if (selectedId) {
          const product = products.content.find((candidate) => candidate.id === selectedId) ?? null;
          this.selected.set(product);
          this.inventory.set(inventories.find((item) => item.productId === selectedId) ?? null);
        }
      },
      error: () => this.fail('No se pudieron cargar los datos de administración.'),
    });
  }

  navigate(section: AdminSection): void { this.section.set(section); this.clearMessages(); }
  sectionTitle(): string { return { overview: 'Resumen del negocio', sales: 'Ventas y pedidos', catalog: 'Catálogo', inventory: 'Inventario' }[this.section()]; }
  sectionDescription(): string { return {
    overview: 'Indicadores comerciales y operativos en tiempo real.',
    sales: 'Seguimiento y actualización del ciclo de cada pedido.',
    catalog: 'Productos, categorías y marcas de la tienda.',
    inventory: 'Disponibilidad, reservas y ajustes de stock.',
  }[this.section()]; }

  openNewProduct(): void { this.navigate('catalog'); this.resetProduct(); }

  select(product: Product, openInventory = false): void {
    this.selected.set(product);
    Object.assign(this.form, product);
    this.inventory.set(this.inventories().find((item) => item.productId === product.id) ?? null);
    if (openInventory) this.navigate('inventory');
  }

  resetProduct(): void {
    this.selected.set(null);
    this.inventory.set(null);
    Object.assign(this.form, this.emptyProduct());
  }

  updateSlug(): void {
    if (!this.selected()) this.form.slug = this.slug(this.form.name);
  }

  saveProduct(): void {
    this.clearMessages();
    this.saving.set(true);
    const request = this.selected()
      ? this.service.updateProduct(this.selected()!.id, this.form)
      : this.service.createProduct(this.form);
    request.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (product) => {
        this.success.set(this.selected() ? 'Producto actualizado.' : 'Producto creado con stock inicial en cero.');
        this.reload();
        this.select(product);
      },
      error: () => this.fail('No se pudo guardar el producto. Revisá los campos requeridos.'),
    });
  }

  deleteProduct(): void {
    const product = this.selected();
    if (!product || !confirm(`¿Dar de baja "${product.name}"?`)) return;
    this.service.deleteProduct(product.id).subscribe({
      next: () => { this.success.set('Producto dado de baja.'); this.resetProduct(); this.reload(); },
      error: () => this.fail('No se pudo dar de baja el producto.'),
    });
  }

  addCategory(): void {
    const name = this.categoryName.trim();
    const slug = this.categorySlug.trim() || this.slug(name);
    if (!name || !slug) return this.fail('Indicá nombre y slug para la categoría.');
    this.service.createCategory({ name, slug }).subscribe({
      next: () => { this.categoryName = ''; this.categorySlug = ''; this.success.set('Categoría creada.'); this.reload(); },
      error: () => this.fail('No se pudo crear la categoría.'),
    });
  }

  addBrand(): void {
    const name = this.brandName.trim();
    if (!name) return this.fail('Indicá un nombre para la marca.');
    this.service.createBrand(name).subscribe({
      next: () => { this.brandName = ''; this.success.set('Marca creada.'); this.reload(); },
      error: () => this.fail('No se pudo crear la marca.'),
    });
  }

  adjustStock(): void {
    const product = this.selected();
    if (!product || !this.adjustment || !this.adjustmentReason.trim()) return this.fail('Indicá un ajuste distinto de cero y su motivo.');
    this.service.adjustInventory(product.id, Number(this.adjustment), this.adjustmentReason.trim()).subscribe({
      next: (inventory) => {
        this.inventory.set(inventory);
        this.inventories.update((items) => items.map((item) => item.productId === inventory.productId ? inventory : item));
        this.adjustment = 0;
        this.adjustmentReason = '';
        this.success.set('Stock actualizado.');
      },
      error: () => this.fail('No se pudo ajustar el stock. El resultado no puede ser negativo.'),
    });
  }

  changeOrderStatus(order: Order, action: OrderAction): void {
    if (action.danger && !confirm(`¿Cancelar el pedido #${order.id}?`)) return;
    this.clearMessages();
    this.orderUpdating.set(order.id);
    this.service.updateOrderStatus(order.id, action.status).pipe(finalize(() => this.orderUpdating.set(null))).subscribe({
      next: (updated) => {
        this.orders.update((orders) => orders.map((current) => current.id === updated.id ? updated : current));
        this.success.set(`Pedido #${order.id} actualizado a ${this.statusLabel(updated.status).toLowerCase()}.`);
        this.service.inventories().subscribe((inventories) => this.inventories.set(inventories));
      },
      error: () => this.fail('No se pudo actualizar el pedido. La reserva puede haber vencido.'),
    });
  }

  orderActions(status: string): OrderAction[] {
    switch (status as OrderStatus) {
      case 'PENDING_PAYMENT': return [{ label: 'Marcar pagado', status: 'PAID' }, { label: 'Cancelar', status: 'CANCELLED', danger: true }];
      case 'PAID': return [{ label: 'Preparar pedido', status: 'PREPARING' }, { label: 'Cancelar', status: 'CANCELLED', danger: true }];
      case 'PREPARING': return [{ label: 'Marcar listo', status: 'READY' }];
      case 'READY': return [{ label: 'Marcar entregado', status: 'DELIVERED' }];
      default: return [];
    }
  }

  statusLabel(status: string): string {
    return { PENDING_PAYMENT: 'Pendiente de pago', PAID: 'Pagado', PREPARING: 'En preparación', READY: 'Listo', DELIVERED: 'Entregado', CANCELLED: 'Cancelado' }[status] ?? status;
  }

  statusCount(status: string): number { return this.orders().filter((order) => order.status === status).length; }
  stockFor(productId: number): Inventory | undefined { return this.inventories().find((item) => item.productId === productId); }
  totalItems(order: Order): number { return order.items.reduce((total, item) => total + item.quantity, 0); }

  private emptyProduct(): ProductForm { return { name: '', slug: '', description: '', price: 0, categoryId: 0, brandId: 0 }; }
  private slug(value: string): string { return value.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, ''); }
  private clearMessages(): void { this.error.set(''); this.success.set(''); }
  private fail(message: string): void { this.success.set(''); this.error.set(message); }
}

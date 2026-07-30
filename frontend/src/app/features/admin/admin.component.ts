import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, CUSTOM_ELEMENTS_SCHEMA, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { catchError, concatMap, finalize, forkJoin, from, map, of, toArray } from 'rxjs';
import { Order } from '../../core/orders/order.service';
import { resolveApiContentUrl } from '../../core/utils/api-content-url';
import { summarizeUploadResults, UploadResult } from '../../core/utils/upload-results';
import { Product, ProductImage } from '../catalog/catalog.service';
import { AdminService, Brand, Category, Inventory, ProductPayload } from './admin.service';

type AdminSection = 'overview' | 'sales' | 'catalog' | 'inventory';
type OrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'PREPARING' | 'READY' | 'DELIVERED' | 'CANCELLED';
interface ProductForm extends ProductPayload {}
interface PendingProductImage { file: File; previewUrl: string; altText: string; }
interface OrderAction { label: string; status: OrderStatus; danger?: boolean; }

const REVENUE_STATUSES = new Set<OrderStatus>(['PAID', 'PREPARING', 'READY', 'DELIVERED']);

@Component({
  selector: 'app-admin',
  imports: [DatePipe, DecimalPipe, FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminComponent {
  private readonly service = inject(AdminService);
  private readonly destroyRef = inject(DestroyRef);
  readonly imageUrl = resolveApiContentUrl;
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
  readonly taxonomySaving = signal(false);
  readonly deletingTaxonomy = signal('');
  readonly deletingImage = signal<number | null>(null);
  readonly pendingImages = signal<PendingProductImage[]>([]);
  readonly form: ProductForm = this.emptyProduct();
  categoryName = '';
  categorySlug = '';
  brandName = '';
  editingCategoryId: number | null = null;
  editingBrandId: number | null = null;
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

  constructor() {
    this.destroyRef.onDestroy(() => this.revokePendingImages());
    this.reload();
  }

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
    this.clearPendingImages();
    this.selected.set(product);
    Object.assign(this.form, { ...product, specifications: product.specifications.map(({ groupName, name, value, highlighted }) => ({ groupName, name, value, highlighted })) });
    this.inventory.set(this.inventories().find((item) => item.productId === product.id) ?? null);
    if (openInventory) this.navigate('inventory');
  }

  resetProduct(): void {
    this.clearPendingImages();
    this.selected.set(null);
    this.inventory.set(null);
    Object.assign(this.form, this.emptyProduct());
  }

  updateSlug(): void {
    if (!this.selected()) this.form.slug = this.slug(this.form.name);
  }

  saveProduct(): void {
    this.clearMessages();
    if (this.form.specifications.some((item) => !item.groupName.trim() || !item.name.trim() || !item.value.trim())) return this.fail('Completá grupo, característica y valor en todas las filas.');
    const names = this.form.specifications.map((item) => item.name.trim().toLowerCase());
    if (new Set(names).size !== names.length) return this.fail('No puede haber características con el mismo nombre.');
    this.saving.set(true);
    const request = this.selected()
      ? this.service.updateProduct(this.selected()!.id, this.form)
      : this.service.createProduct(this.form);
    const wasEditing = !!this.selected();
    const pendingImages = [...this.pendingImages()];
    request.subscribe({
      next: (product) => {
        if (!pendingImages.length) {
          this.saving.set(false);
          this.finishProductSave(product, wasEditing ? 'Producto actualizado.' : 'Producto creado con stock inicial en cero.');
          return;
        }

        from(pendingImages).pipe(
          concatMap((item) => this.service.uploadProductImage(product.id, item.file, item.altText).pipe(
            map((uploaded): UploadResult<PendingProductImage, ProductImage> => ({ pending: item, uploaded })),
            catchError(() => of<UploadResult<PendingProductImage, ProductImage>>({ pending: item, uploaded: null })),
          )),
          toArray(),
          finalize(() => this.saving.set(false)),
        ).subscribe((results) => {
          const { uploaded, succeeded, failed } = summarizeUploadResults(results);
          const updated = { ...product, images: [...(product.images ?? []), ...uploaded] };
          const message = uploaded.length === pendingImages.length
            ? `${wasEditing ? 'Producto actualizado' : 'Producto creado'} y ${uploaded.length} ${uploaded.length === 1 ? 'imagen subida' : 'imágenes subidas'}.`
            : `${wasEditing ? 'El producto se actualizó' : 'El producto se creó'} correctamente, pero solo se subieron ${uploaded.length} de ${pendingImages.length} imágenes. Podés volver a intentar las restantes.`;
          succeeded.forEach((item) => URL.revokeObjectURL(item.previewUrl));
          this.pendingImages.set(failed);
          this.finishProductSave(updated, message, failed.length > 0);
        });
      },
      error: () => { this.saving.set(false); this.fail('No se pudo guardar el producto. Revisá los campos requeridos.'); },
    });
  }

  selectProductImages(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    if (!files.length) return;
    const available = 6 - (this.selected()?.images.length ?? 0) - this.pendingImages().length;
    if (files.length > available) return this.fail(`Podés agregar hasta ${Math.max(0, available)} imágenes más; el máximo es 6 por producto.`);
    const invalidType = files.find((file) => !['image/jpeg', 'image/png'].includes(file.type));
    if (invalidType) return this.fail(`"${invalidType.name}" no es JPEG ni PNG.`);
    const oversized = files.find((file) => file.size > 5 * 1024 * 1024);
    if (oversized) return this.fail(`"${oversized.name}" supera el máximo de 5 MiB.`);

    this.clearMessages();
    this.pendingImages.update((current) => [...current, ...files.map((file) => ({ file, previewUrl: URL.createObjectURL(file), altText: '' }))]);
  }

  removePendingImage(index: number): void {
    const item = this.pendingImages()[index];
    if (item) URL.revokeObjectURL(item.previewUrl);
    this.pendingImages.update((items) => items.filter((_, currentIndex) => currentIndex !== index));
  }

  addSpecification(): void {
    if (this.form.specifications.length >= 60) return this.fail('Podés agregar hasta 60 características por producto.');
    this.form.specifications = [...this.form.specifications, { groupName: 'Características generales', name: '', value: '', highlighted: false }];
    this.clearMessages();
  }

  removeSpecification(index: number): void {
    this.form.specifications = this.form.specifications.filter((_, current) => current !== index);
  }

  moveSpecification(index: number, change: number): void {
    const target = index + change;
    if (target < 0 || target >= this.form.specifications.length) return;
    const specifications = [...this.form.specifications];
    [specifications[index], specifications[target]] = [specifications[target], specifications[index]];
    this.form.specifications = specifications;
  }

  deleteImage(image: ProductImage): void {
    const product = this.selected();
    if (!product || this.deletingImage() !== null || !confirm(`¿Eliminar la imagen "${image.altText || image.id}"?`)) return;
    this.deletingImage.set(image.id);
    this.clearMessages();
    this.service.deleteProductImage(product.id, image.id).pipe(finalize(() => this.deletingImage.set(null))).subscribe({
      next: () => {
        const updated = { ...product, images: product.images.filter((current) => current.id !== image.id) };
        this.products.update((products) => products.map((current) => current.id === product.id ? updated : current));
        this.selected.set(updated);
        this.success.set('Imagen eliminada.');
      },
      error: () => this.fail('No se pudo eliminar la imagen.'),
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
    if (this.taxonomySaving()) return;
    const name = this.categoryName.trim();
    const slug = this.categorySlug.trim() || this.slug(name);
    if (!name || !slug) return this.fail('Indicá nombre y slug para la categoría.');
    const editing = this.editingCategoryId;
    const request = editing === null
      ? this.service.createCategory({ name, slug })
      : this.service.updateCategory(editing, { name, slug });
    this.taxonomySaving.set(true);
    request.pipe(finalize(() => this.taxonomySaving.set(false))).subscribe({
      next: () => { this.cancelCategoryEdit(); this.success.set(editing === null ? 'Categoría creada.' : 'Categoría actualizada.'); this.reload(); },
      error: () => this.fail(editing === null ? 'No se pudo crear la categoría.' : 'No se pudo actualizar la categoría.'),
    });
  }

  editCategory(category: Category): void {
    this.editingCategoryId = category.id;
    this.categoryName = category.name;
    this.categorySlug = category.slug;
    this.clearMessages();
  }

  cancelCategoryEdit(): void {
    this.editingCategoryId = null;
    this.categoryName = '';
    this.categorySlug = '';
  }

  deleteCategory(category: Category): void {
    const key = `category-${category.id}`;
    if (this.deletingTaxonomy() || !confirm(`¿Eliminar la categoría "${category.name}"? Solo se puede eliminar si no tiene productos activos.`)) return;
    this.deletingTaxonomy.set(key);
    this.clearMessages();
    this.service.deleteCategory(category.id).pipe(finalize(() => this.deletingTaxonomy.set(''))).subscribe({
      next: () => {
        if (this.editingCategoryId === category.id) this.cancelCategoryEdit();
        this.categories.update((categories) => categories.filter((current) => current.id !== category.id));
        this.success.set('Categoría eliminada.');
      },
      error: () => this.fail('No se puede eliminar la categoría mientras tenga productos activos.'),
    });
  }

  addBrand(): void {
    if (this.taxonomySaving()) return;
    const name = this.brandName.trim();
    if (!name) return this.fail('Indicá un nombre para la marca.');
    const editing = this.editingBrandId;
    const request = editing === null ? this.service.createBrand(name) : this.service.updateBrand(editing, name);
    this.taxonomySaving.set(true);
    request.pipe(finalize(() => this.taxonomySaving.set(false))).subscribe({
      next: () => { this.cancelBrandEdit(); this.success.set(editing === null ? 'Marca creada.' : 'Marca actualizada.'); this.reload(); },
      error: () => this.fail(editing === null ? 'No se pudo crear la marca.' : 'No se pudo actualizar la marca.'),
    });
  }

  editBrand(brand: Brand): void {
    this.editingBrandId = brand.id;
    this.brandName = brand.name;
    this.clearMessages();
  }

  cancelBrandEdit(): void {
    this.editingBrandId = null;
    this.brandName = '';
  }

  deleteBrand(brand: Brand): void {
    const key = `brand-${brand.id}`;
    if (this.deletingTaxonomy() || !confirm(`¿Eliminar la marca "${brand.name}"? Solo se puede eliminar si no tiene productos activos.`)) return;
    this.deletingTaxonomy.set(key);
    this.clearMessages();
    this.service.deleteBrand(brand.id).pipe(finalize(() => this.deletingTaxonomy.set(''))).subscribe({
      next: () => {
        if (this.editingBrandId === brand.id) this.cancelBrandEdit();
        this.brands.update((brands) => brands.filter((current) => current.id !== brand.id));
        this.success.set('Marca eliminada.');
      },
      error: () => this.fail('No se puede eliminar la marca mientras tenga productos activos.'),
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
    if (action.danger && !confirm(`¿Cancelar el pedido #${order.id}? El stock reservado o preparado volverá a estar disponible.`)) return;
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
      case 'PREPARING': return [{ label: 'Marcar listo', status: 'READY' }, { label: 'Cancelar venta', status: 'CANCELLED', danger: true }];
      case 'READY': return [{ label: 'Marcar entregado', status: 'DELIVERED' }, { label: 'Cancelar venta', status: 'CANCELLED', danger: true }];
      default: return [];
    }
  }

  statusLabel(status: string): string {
    return { PENDING_PAYMENT: 'Pendiente de pago', PAID: 'Pagado', PREPARING: 'En preparación', READY: 'Listo', DELIVERED: 'Entregado', CANCELLED: 'Cancelado' }[status] ?? status;
  }

  statusCount(status: string): number { return this.orders().filter((order) => order.status === status).length; }
  stockFor(productId: number): Inventory | undefined { return this.inventories().find((item) => item.productId === productId); }
  totalItems(order: Order): number { return order.items.reduce((total, item) => total + item.quantity, 0); }

  private emptyProduct(): ProductForm { return { name: '', slug: '', description: '', price: 0, categoryId: 0, brandId: 0, specifications: [] }; }
  private finishProductSave(product: Product, message: string, preservePendingImages = false): void {
    this.products.update((products) => products.some((current) => current.id === product.id)
      ? products.map((current) => current.id === product.id ? product : current)
      : [...products, product]);
    if (preservePendingImages) {
      this.selected.set(product);
      Object.assign(this.form, { ...product, specifications: product.specifications.map(({ groupName, name, value, highlighted }) => ({ groupName, name, value, highlighted })) });
      this.inventory.set(this.inventories().find((item) => item.productId === product.id) ?? null);
    } else {
      this.select(product);
    }
    this.success.set(message);
    this.service.inventories().subscribe((inventories) => {
      this.inventories.set(inventories);
      this.inventory.set(inventories.find((item) => item.productId === product.id) ?? null);
    });
  }
  private clearPendingImages(): void { this.revokePendingImages(); this.pendingImages.set([]); }
  private revokePendingImages(): void { this.pendingImages().forEach((item) => URL.revokeObjectURL(item.previewUrl)); }
  private slug(value: string): string { return value.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, ''); }
  private clearMessages(): void { this.error.set(''); this.success.set(''); }
  private fail(message: string): void { this.success.set(''); this.error.set(message); }
}

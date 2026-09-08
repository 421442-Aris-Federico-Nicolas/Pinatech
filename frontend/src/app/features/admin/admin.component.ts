import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, CUSTOM_ELEMENTS_SCHEMA, DestroyRef, ElementRef, HostListener, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, concatMap, finalize, forkJoin, from, map, of, toArray, Subject, takeUntil, mergeMap } from 'rxjs';
import { NotificationService } from '../../core/notifications/notification.service';
import { CancellationReasonCode } from '../../core/orders/order.service';
import { resolveApiContentUrl } from '../../core/utils/api-content-url';
import { estadoLabel, estadoTono } from '../../core/utils/estado-label';
import { DEFAULT_PRODUCT_VARIANT_NAME, hasVisibleColorVariants, isDefaultProductVariantName } from '../../core/utils/product-variant';
import { summarizeUploadResults, UploadResult } from '../../core/utils/upload-results';
import { AppBadgeDirective } from '../../shared/ui/app-badge.directive';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';
import { AppCardDirective } from '../../shared/ui/app-card.directive';
import { AppFeedbackComponent } from '../../shared/ui/feedback/app-feedback.component';
import { AppInputComponent } from '../../shared/ui/input/app-input.component';
import { AppSelectComponent, AppSelectOption } from '../../shared/ui/select/app-select.component';
import { AppTextareaComponent } from '../../shared/ui/textarea/app-textarea.component';
import { Product, ProductImage } from '../catalog/catalog.service';
import { AdminOrder, AdminService, Brand, CancellationScope, Category, Inventory, InventoryListItem, OrdersSummary, InventorySummary, ProductListItem, PendingBankTransferProof, ProductPayload, ProductVariantPayload } from './admin.service';
import { HomeSectionsComponent } from './home-sections/home-sections.component';

type AdminSection = 'overview' | 'sales' | 'catalog' | 'inventory' | 'home';
type OrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'PREPARING' | 'READY' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';
const ORDER_FILTERS = ['ALL', 'PENDING_PAYMENT', 'PAID', 'PREPARING', 'READY', 'SHIPPED', 'DELIVERED', 'CANCELLED'];
interface ProductForm extends ProductPayload { hasColorVariants: boolean; }
interface PendingProductImage { file: File; previewUrl: string; altText: string; }
interface OrderAction { label: string; status: OrderStatus; danger?: boolean; }

@Component({
  selector: 'app-admin',
  imports: [AppBadgeDirective, AppButtonDirective, AppCardDirective, AppFeedbackComponent, AppInputComponent, AppSelectComponent, AppTextareaComponent, CurrencyPipe, DatePipe, DecimalPipe, FormsModule, HomeSectionsComponent],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminComponent {
  private readonly service = inject(AdminService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);
  private readonly route = inject(ActivatedRoute, { optional: true });
  private readonly router = inject(Router, { optional: true });
  private readonly notifications = inject(NotificationService);
  private productSnapshot = '';
  private proofPreviewGeneration = 0;
  private readonly cancelLoad = new Subject<void>();
  private readonly cancelDetail = new Subject<void>();
  private readonly cancelOrderDetail = new Subject<void>();
  readonly requestedOrder = signal<AdminOrder | null>(null);
  private readonly cancelPreview = new Subject<void>();
  readonly detailLoading = signal(false);
  readonly previewLoading = signal<string | null>(null);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  search = '';
  readonly ordersSummary = signal<OrdersSummary | null>(null);
  readonly inventorySummary = signal<InventorySummary | null>(null);
  private cancellationTrigger: HTMLElement | null = null;
  private refundTrigger: HTMLElement | null = null;
  readonly imageUrl = resolveApiContentUrl;
  readonly section = signal<AdminSection>('overview');
  readonly homeDirty = signal(false);
  private readonly homeEditor = viewChild(HomeSectionsComponent);
  readonly sidebarCollapsed = signal(false);
  readonly loading = signal(false);
  readonly products = signal<ProductListItem[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly brands = signal<Brand[]>([]);
  readonly inventories = signal<Inventory[]>([]);
  readonly inventoryRows = signal<InventoryListItem[]>([]);
  readonly orders = signal<AdminOrder[]>([]);
  readonly pendingTransferProofs = signal<PendingBankTransferProof[]>([]);
  readonly proofPreviewUrls = signal<Record<string, string[]>>({});
  readonly proofReviewing = signal<string | null>(null);
  readonly proofReviewError = signal<Record<string, string>>({});
  readonly selected = signal<Product | null>(null);
  readonly inventory = signal<Inventory | null>(null);
  readonly selectedVariantId = signal<number | null>(null);
  readonly expandedOrder = signal<number | null>(null);
  readonly orderFilter = signal<string>('ALL');
  readonly orderUpdating = signal<number | null>(null);
  readonly shipmentUpdating = signal<number | null>(null);
  readonly ordersRefreshing = signal(false);
  readonly shipmentDocumentLoading = signal<string | null>(null);
  readonly cancellationOrder = signal<AdminOrder | null>(null);
  readonly cancellationError = signal('');
  readonly refundOrder = signal<AdminOrder | null>(null);
  readonly refundConfirming = signal<number | null>(null);
  readonly refundError = signal('');
  readonly error = signal('');
  readonly saving = signal(false);
  readonly taxonomySaving = signal(false);
  readonly deletingTaxonomy = signal('');
  readonly deletingImage = signal<number | null>(null);
  readonly deactivatingProduct = signal(false);
  readonly adjustingStock = signal(false);
  readonly pendingImages = signal<PendingProductImage[]>([]);
  readonly form: ProductForm = this.emptyProduct();
  categoryName = '';
  categorySlug = '';
  brandName = '';
  editingCategoryId: number | null = null;
  editingBrandId: number | null = null;
  adjustment = 0;
  adjustmentReason = '';
  proofAmounts: Record<string, number | null> = {};
  proofReferences: Record<string, string> = {};
  proofRejectionReasons: Record<string, string> = {};
  cancellationScope: CancellationScope = 'SHIPMENT_ONLY';
  cancellationReason: CancellationReasonCode | null = null;
  cancellationDetail = '';
  refundReference = '';

  readonly soldOrders = computed(() => this.ordersSummary()?.soldOrders ?? 0);
  readonly revenue = computed(() => this.ordersSummary()?.revenue ?? 0);
  readonly averageTicket = computed(() => this.ordersSummary()?.averageTicket ?? 0);
  readonly activeOrders = computed(() => this.ordersSummary()?.activeOrders ?? 0);
  readonly lowStock = computed(() => this.inventorySummary()?.lowStock ?? 0);
  readonly availableUnits = computed(() => this.inventorySummary()?.availableUnits ?? 0);
  readonly recentOrders = computed(() => this.ordersSummary()?.recentOrders ?? []);
  readonly filteredOrders = computed(() => {
    const requested = this.requestedOrder();
    return requested && requested.id === this.expandedOrder() && !this.orders().some((order) => order.id === requested.id)
      ? [requested, ...this.orders()] : this.orders();
  });
  readonly salesChart = computed(() => this.ordersSummary()?.salesChart ?? []);
  readonly categoryOptions = computed<readonly AppSelectOption[]>(() => this.categories().map((category) => ({ value: category.id, label: category.name })));
  readonly brandOptions = computed<readonly AppSelectOption[]>(() => this.brands().map((brand) => ({ value: brand.id, label: brand.name })));
  readonly productImageOptions = computed<readonly AppSelectOption[]>(() => [
    { value: null, label: 'Sin imagen específica' },
    ...(this.selected()?.images ?? []).map((image, index) => ({ value: image.id, label: this.imageLabel(image, index) })),
  ]);
  readonly cancellationReasonOptions: readonly AppSelectOption[] = [
    { value: 'CUSTOMER_REQUEST', label: 'Solicitud del cliente' },
    { value: 'INVALID_DELIVERY_DATA', label: 'Datos de entrega inválidos' },
    { value: 'PRODUCT_UNAVAILABLE', label: 'Producto no disponible' },
    { value: 'LOGISTICS_PROBLEM', label: 'Problema logístico' },
    { value: 'DUPLICATE_OR_ERROR', label: 'Pedido duplicado o error' },
    { value: 'OTHER', label: 'Otro motivo' },
  ];
  readonly estadoTono = estadoTono;
  readonly hasVisibleColorVariants = hasVisibleColorVariants;
  readonly isDefaultProductVariantName = isDefaultProductVariantName;

  constructor() {
    const section = this.route?.snapshot.queryParamMap.get('section');
    if (this.isSection(section)) this.section.set(section);
    const filter = this.route?.snapshot.queryParamMap.get('orderStatus');
    if (filter && ORDER_FILTERS.includes(filter)) this.orderFilter.set(filter);
    else if (filter) queueMicrotask(() => this.syncUrl({ orderStatus: null }));
    const orderId = Number(this.route?.snapshot.queryParamMap.get('order'));
    if (orderId > 0) this.expandedOrder.set(orderId);
    this.productSnapshot = this.productState();
    this.destroyRef.onDestroy(() => {
      this.cancelLoad.next();
      this.cancelDetail.next();
      this.cancelOrderDetail.next();
      this.revokePendingImages();
      this.clearProofPreviews();
    });
    this.reload(true);
  }

  reload(force = false, preserveMessages = false, refreshDetail = true): void {
    if (this.saving() || this.deactivatingProduct() || this.adjustingStock()
      || this.proofReviewing() !== null || this.taxonomySaving() || this.deletingTaxonomy() || this.deletingImage() !== null
      || this.orderUpdating() !== null || this.shipmentUpdating() !== null || this.refundConfirming() !== null
      || this.ordersRefreshing() || this.shipmentDocumentLoading() !== null
      || (!force && !this.confirmDiscard())) return;
    if (this.section() === 'home') {
      this.loading.set(false);
      return;
    }
    if (!preserveMessages) this.clearMessages();
    this.cancelLoad.next();
    this.cancelDetail.next();
    this.cancelOrderDetail.next();
    this.clearProofPreviews();
    this.loading.set(true);
    const section = this.section();
    forkJoin({
      products: section === 'catalog' ? this.service.products(this.search, this.page()) : of(null),
      categories: section === 'catalog' ? this.service.categories() : of(null),
      brands: section === 'catalog' ? this.service.brands() : of(null),
      inventories: section === 'inventory' ? this.service.inventoryPage(this.search, this.page()) : of(null),
      orders: section === 'sales' ? this.service.ordersPage(this.page(), this.orderFilter()) : of(null),
      summary: section === 'overview' || section === 'sales' ? this.service.ordersSummary() : of(null),
      stock: section === 'overview' ? this.service.inventorySummary() : of(null),
      transferProofs: section === 'sales' ? this.service.pendingBankTransferProofs() : of(null),
    }).pipe(takeUntil(this.cancelLoad), finalize(() => this.loading.set(false))).subscribe({
      next: ({ products, categories, brands, inventories, orders, summary, stock, transferProofs }) => {
        if (products) this.products.set(products.content);
        if (categories) this.categories.set(categories);
        if (brands) this.brands.set(brands);
        if (inventories) {
          this.inventoryRows.set(inventories.content);
          this.inventories.set(inventories.content);
          if (!refreshDetail) this.syncSelectedInventory();
        }
        if (orders) { this.orders.set(orders.content); this.loadRequestedOrder(); }
        if (summary) this.ordersSummary.set(summary);
        if (stock) this.inventorySummary.set(stock);
        if (transferProofs) this.pendingTransferProofs.set(transferProofs);
        const result = products ?? inventories ?? orders;
        if (result) { this.page.set(result.number); this.totalPages.set(result.totalPages); this.totalElements.set(result.totalElements); }
        if (section === 'catalog' && !this.selected() && this.productState() === this.productSnapshot) this.initializeTaxonomySelections();
        const id = this.selected()?.id ?? Number(this.route?.snapshot.queryParamMap.get('product'));
        if (refreshDetail && id > 0 && (section === 'catalog' || section === 'inventory')) this.loadProduct(id, (this.selectedVariantId() ?? Number(this.route?.snapshot.queryParamMap.get('variant'))) || undefined, true);
      },
      error: () => this.fail('No se pudieron cargar los datos de administración.'),
    });
  }

  navigate(section: AdminSection): boolean {
    if (section === this.section()) return true;
    if (this.saving() || this.adjustingStock() || this.taxonomySaving() || this.deletingTaxonomy() || this.deletingImage() !== null || this.deactivatingProduct() || this.proofReviewing() !== null || this.orderUpdating() !== null || this.shipmentUpdating() !== null || this.refundConfirming() !== null || this.ordersRefreshing()) return false;
    if (section !== this.section() && !this.confirmDiscard()) return false;
    this.cancelLoad.next();
    if (this.section() === 'home') this.homeDirty.set(false);
    if (this.section() === 'catalog') {
      this.clearPendingImages();
      Object.assign(this.form, this.selected() ? this.productForm(this.selected()!) : this.emptyProduct());
      this.productSnapshot = this.productState();
    }
    this.section.set(section);
    this.syncUrl({ section, product: section === 'catalog' || section === 'inventory' ? this.selected()?.id ?? null : null, order: section === 'sales' ? this.expandedOrder() : null });
    this.clearMessages();
    this.page.set(0);
    this.search = '';
    this.reload(true);
    return true;
  }
  sectionTitle(): string { return { overview: 'Resumen del negocio', sales: 'Ventas y pedidos', catalog: 'Catálogo', inventory: 'Inventario', home: 'Inicio de la tienda' }[this.section()]; }
  sectionDescription(): string { return {
    overview: 'Indicadores comerciales y operativos en tiempo real.',
    sales: 'Seguimiento y actualización del ciclo de cada pedido.',
    catalog: 'Productos, categorías y marcas de la tienda.',
    inventory: 'Disponibilidad, reservas y ajustes de stock.',
    home: 'Contenido y productos que se publican en la portada.',
  }[this.section()]; }

  refreshSection(): void {
    if (this.section() === 'home') this.homeEditor()?.reload();
    else this.reload();
  }

  openNewProduct(): void { if (this.navigate('catalog')) this.resetProduct(); }

  changePage(page: number): void {
    if (page < 0 || page >= this.totalPages() || this.saving() || this.adjustingStock() || this.ordersRefreshing() || this.orderUpdating() !== null || this.shipmentUpdating() !== null || this.refundConfirming() !== null || this.proofReviewing() !== null) return;
    this.page.set(page);
    this.reload(true, false, false);
  }

  searchPage(): void {
    if (this.saving() || this.adjustingStock()) return;
    this.page.set(0);
    this.reload(true, false, false);
  }

  loadProduct(id: number, variantId?: number, force = false): void {
    if ((!force && !this.confirmDiscardProductChanges()) || this.saving() || this.adjustingStock()) return;
    this.cancelDetail.next();
    this.detailLoading.set(true);
    this.service.product(id).pipe(takeUntil(this.cancelDetail), finalize(() => this.detailLoading.set(false))).subscribe({
      next: (product) => {
        this.select(product, false, variantId, true);
        if (this.section() === 'inventory') this.syncSelectedInventory();
      },
      error: () => this.fail('No se pudo cargar el detalle del producto.'),
    });
  }

  private syncSelectedInventory(): void {
    const variantId = this.selectedVariantId();
    const row = this.inventories().find((item) => item.variantId === variantId);
    this.inventory.set(row ?? null);
    if (variantId === null || row) return;
    this.service.inventory(variantId).pipe(takeUntil(this.cancelDetail), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (stock) => this.inventory.set(stock),
      error: () => this.fail('No se pudo cargar el stock seleccionado.'),
    });
  }

  select(product: Product, openInventory = false, variantId?: number, force = false): void {
    if (!force && this.selected()?.id !== product.id && !this.confirmDiscardProductChanges()) return;
    this.clearPendingImages();
    this.selected.set(product);
    Object.assign(this.form, this.productForm(product));
    const selectedVariantId = product.variants.some((variant) => variant.id === variantId) ? variantId! : product.variants[0]?.id ?? null;
    this.selectedVariantId.set(selectedVariantId);
    this.inventory.set(this.inventories().find((item) => item.variantId === selectedVariantId) ?? null);
    this.productSnapshot = this.productState();
    this.syncUrl({ product: product.id, variant: selectedVariantId });
    if (openInventory) this.navigate('inventory');
  }

  resetProduct(force = false): void {
    if (!force && !this.confirmDiscardProductChanges()) return;
    this.cancelDetail.next();
    this.clearPendingImages();
    this.selected.set(null);
    this.inventory.set(null);
    this.selectedVariantId.set(null);
    Object.assign(this.form, this.emptyProduct());
    this.productSnapshot = this.productState();
    this.syncUrl({ product: null, variant: null });
  }

  updateSlug(): void {
    if (!this.selected()) this.form.slug = this.slug(this.form.name);
  }

  saveProduct(productForm?: NgForm): void {
    if (this.saving() || this.detailLoading()) return;
    this.clearMessages();
    if (productForm?.invalid) {
      this.fail('Revisá los campos requeridos del producto.');
      queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>('.editor :is(app-input, app-select, app-textarea, input, textarea).ng-invalid')?.focus());
      return;
    }
    if (!Number.isFinite(Number(this.form.price)) || Number(this.form.price) <= 0) {
      this.failAndFocus('Indicá un precio mayor que cero.', '[name="productPrice"]');
      return;
    }
    if (!this.isValidTaxonomyId(this.form.categoryId, this.categories())) {
      this.failAndFocus('Seleccioná una categoría válida.', '[name="productCategory"]');
      return;
    }
    if (!this.isValidTaxonomyId(this.form.brandId, this.brands())) {
      this.failAndFocus('Seleccioná una marca válida.', '[name="productBrand"]');
      return;
    }
    const shippingFields = [
      { value: this.form.shippingWeightGrams, min: 10, max: 10000000, label: 'peso', selector: '[name="shippingWeightGrams"]' },
      { value: this.form.shippingHeightCm, min: 1, max: 5000, label: 'alto', selector: '[name="shippingHeightCm"]' },
      { value: this.form.shippingWidthCm, min: 1, max: 5000, label: 'ancho', selector: '[name="shippingWidthCm"]' },
      { value: this.form.shippingLengthCm, min: 1, max: 5000, label: 'largo', selector: '[name="shippingLengthCm"]' },
      { value: this.form.shippingClassificationId, min: 1, max: 8, label: 'clasificación', selector: '[name="shippingClassificationId"]' },
    ];
    const invalidShippingField = shippingFields.find((field) => !Number.isInteger(Number(field.value)) || Number(field.value) < field.min || Number(field.value) > field.max);
    if (invalidShippingField) {
      this.failAndFocus(`Indicá un ${invalidShippingField.label} entero entre ${invalidShippingField.min} y ${invalidShippingField.max}.`, invalidShippingField.selector);
      return;
    }
    const incompleteSpecification = this.form.specifications.findIndex((item) => !item.groupName.trim() || !item.name.trim() || !item.value.trim());
    if (incompleteSpecification >= 0) {
      const item = this.form.specifications[incompleteSpecification];
      const field = !item.groupName.trim() ? 'specGroup' : !item.name.trim() ? 'specName' : 'specValue';
      this.failAndFocus('Completá grupo, característica y valor en todas las filas.', `[name="${field}${incompleteSpecification}"]`);
      return;
    }
    const names = this.form.specifications.map((item) => item.name.trim().toLowerCase());
    const duplicateSpecification = names.findIndex((name, index) => names.indexOf(name) !== index);
    if (duplicateSpecification >= 0) {
      this.failAndFocus('No puede haber características con el mismo nombre.', `[name="specName${duplicateSpecification}"]`);
      return;
    }
    if (this.form.hasColorVariants) {
      const incompleteVariant = this.form.variants.findIndex((variant) => !variant.colorName.trim());
      if (!this.form.variants.length || incompleteVariant >= 0) {
        this.failAndFocus('Agregá al menos un color y completá todos sus nombres.', incompleteVariant >= 0 ? `[name="variantName${incompleteVariant}"]` : '.variants-editor button');
        return;
      }
      const colors = this.form.variants.map((variant) => variant.colorName.trim().toLowerCase());
      const duplicateColor = colors.findIndex((color, index) => colors.indexOf(color) !== index);
      if (duplicateColor >= 0) {
        this.failAndFocus('No puede haber colores repetidos.', `[name="variantName${duplicateColor}"]`);
        return;
      }
    }
    const payload = this.productPayload();
    this.saving.set(true);
    const request = this.selected()
      ? this.service.updateProduct(this.selected()!.id, payload)
      : this.service.createProduct(payload);
    const wasEditing = !!this.selected();
    const pendingImages = [...this.pendingImages()];
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
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
          takeUntilDestroyed(this.destroyRef),
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
    if (this.saving()) return;
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
    if (this.saving()) return;
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

  addVariant(): void {
    if (this.form.variants.length >= 20) return this.fail('Podés agregar hasta 20 colores por producto.');
    this.form.variants = [...this.form.variants, { colorName: '', colorHex: '#7D8798', imageId: null }];
    this.clearMessages();
  }

  setColorVariantMode(enabled: boolean): void {
    this.form.hasColorVariants = enabled;
    if (enabled && this.form.variants.length === 1 && isDefaultProductVariantName(this.form.variants[0].colorName)) {
      this.form.variants = [{ ...this.form.variants[0], colorName: '', colorHex: '#7D8798' }];
    }
    this.clearMessages();
  }

  removeVariant(index: number): void {
    if (this.form.variants.length === 1) return this.fail('El producto debe conservar al menos un color.');
    this.form.variants = this.form.variants.filter((_, current) => current !== index);
  }

  moveVariant(index: number, change: number): void {
    const target=index+change;
    if (target<0 || target>=this.form.variants.length) return;
    const variants=[...this.form.variants];
    [variants[index],variants[target]]=[variants[target],variants[index]];
    this.form.variants=variants;
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
    if (!product || this.deletingImage() !== null || !confirm(`¿Eliminar la imagen "${this.imageLabel(image)}"?`)) return;
    this.deletingImage.set(image.id);
    this.clearMessages();
    this.service.deleteProductImage(product.id, image.id).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.deletingImage.set(null))).subscribe({
      next: () => {
        const formWasClean = this.productState() === this.productSnapshot && !this.pendingImages().length;
        const variants = product.variants.map((variant) => variant.imageId === image.id ? { ...variant, imageId: null } : variant);
        const updated = { ...product, images: product.images.filter((current) => current.id !== image.id), variants };
        this.form.variants = this.form.variants.map((variant) => variant.imageId === image.id ? { ...variant, imageId: null } : variant);
        this.products.update((products) => products.map((current) => current.id === product.id ? updated : current));
        this.selected.set(updated);
        if (formWasClean) this.productSnapshot = this.productState();
        this.succeed('Imagen eliminada.');
      },
      error: () => this.fail('No se pudo eliminar la imagen.'),
    });
  }

  deleteProduct(): void {
    const product = this.selected();
    if (!product || this.saving() || this.deactivatingProduct() || !confirm(`¿Dar de baja "${product.name}"?`)) return;
    this.deactivatingProduct.set(true);
    this.service.deleteProduct(product.id).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.deactivatingProduct.set(false))).subscribe({
      next: () => { this.deactivatingProduct.set(false); this.succeed('Producto dado de baja.'); this.resetProduct(true); this.reload(false, true); },
      error: () => this.fail('No se pudo dar de baja el producto.'),
    });
  }

  addCategory(): void {
    if (this.taxonomySaving()) return;
    const name = this.categoryName.trim();
    const slug = this.categorySlug.trim() || this.slug(name);
    if (!name || !slug) {
      this.failAndFocus('Indicá nombre y slug para la categoría.', `[name="${!name ? 'categoryName' : 'categorySlug'}"]`);
      return;
    }
    const editing = this.editingCategoryId;
    const request = editing === null
      ? this.service.createCategory({ name, slug })
      : this.service.updateCategory(editing, { name, slug });
    this.taxonomySaving.set(true);
    request.pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.taxonomySaving.set(false))).subscribe({
      next: (category) => {
        this.categories.update((categories) => editing === null ? [...categories, category] : categories.map((current) => current.id === category.id ? category : current));
        if (!this.isValidTaxonomyId(this.form.categoryId, this.categories())) this.form.categoryId = category.id;
        this.cancelCategoryEdit();
        this.succeed(editing === null ? 'Categoría creada.' : 'Categoría actualizada.');
      },
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
    this.service.deleteCategory(category.id).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.deletingTaxonomy.set(''))).subscribe({
      next: () => {
        if (this.editingCategoryId === category.id) this.cancelCategoryEdit();
        this.categories.update((categories) => categories.filter((current) => current.id !== category.id));
        this.succeed('Categoría eliminada.');
      },
      error: () => this.fail('No se puede eliminar la categoría mientras tenga productos activos.'),
    });
  }

  addBrand(): void {
    if (this.taxonomySaving()) return;
    const name = this.brandName.trim();
    if (!name) {
      this.failAndFocus('Indicá un nombre para la marca.', '[name="brandName"]');
      return;
    }
    const editing = this.editingBrandId;
    const request = editing === null ? this.service.createBrand(name) : this.service.updateBrand(editing, name);
    this.taxonomySaving.set(true);
    request.pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.taxonomySaving.set(false))).subscribe({
      next: (brand) => {
        this.brands.update((brands) => editing === null ? [...brands, brand] : brands.map((current) => current.id === brand.id ? brand : current));
        if (!this.isValidTaxonomyId(this.form.brandId, this.brands())) this.form.brandId = brand.id;
        this.cancelBrandEdit();
        this.succeed(editing === null ? 'Marca creada.' : 'Marca actualizada.');
      },
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
    this.service.deleteBrand(brand.id).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.deletingTaxonomy.set(''))).subscribe({
      next: () => {
        if (this.editingBrandId === brand.id) this.cancelBrandEdit();
        this.brands.update((brands) => brands.filter((current) => current.id !== brand.id));
        this.succeed('Marca eliminada.');
      },
      error: () => this.fail('No se puede eliminar la marca mientras tenga productos activos.'),
    });
  }

  adjustStock(): void {
    const current = this.inventory();
    if (this.adjustingStock() || this.detailLoading()) return;
    if (!current) {
      this.failAndFocus('Indicá un color, un ajuste distinto de cero y su motivo.', '.inventory-products button');
      return;
    }
    if (!Number.isFinite(Number(this.adjustment)) || !Number(this.adjustment)) {
      this.failAndFocus('Indicá un color, un ajuste distinto de cero y su motivo.', '[name="stockAdjustment"]');
      return;
    }
    if (!this.adjustmentReason.trim()) {
      this.failAndFocus('Indicá un color, un ajuste distinto de cero y su motivo.', '[name="stockReason"]');
      return;
    }
    this.adjustingStock.set(true);
    this.service.adjustInventory(current.variantId, Number(this.adjustment), this.adjustmentReason.trim()).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.adjustingStock.set(false))).subscribe({
      next: (inventory) => {
        this.inventory.set(inventory);
        this.inventoryRows.update((items) => items.map((item) => item.variantId === inventory.variantId ? { ...item, ...inventory } : item));
        this.inventories.update((items) => items.map((item) => item.variantId === inventory.variantId ? inventory : item));
        this.adjustment = 0;
        this.adjustmentReason = '';
        this.succeed('Stock actualizado.');
      },
      error: () => this.fail('No se pudo ajustar el stock. El resultado no puede ser negativo.'),
    });
  }

  changeOrderStatus(order: AdminOrder, action: OrderAction): void {
    if (this.orderUpdating() !== null || this.shipmentUpdating() !== null
      || this.refundConfirming() !== null || this.ordersRefreshing()) return;
    if (action.danger && !confirm(`¿Cancelar el pedido #${order.id}? El stock reservado o preparado volverá a estar disponible.`)) return;
    this.clearMessages();
    this.orderUpdating.set(order.id);
    this.service.updateOrderStatus(order.id, action.status).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.orderUpdating.set(null))).subscribe({
      next: (updated) => {
        this.orders.update((orders) => orders.map((current) => current.id === updated.id ? updated : current));
        this.succeed(`Pedido #${order.id} actualizado a ${this.statusLabel(updated.status).toLowerCase()}.`);
        this.refreshOrdersAfterShipmentAction();
      },
      error: () => this.fail('No se pudo actualizar el pedido. La reserva puede haber vencido.'),
    });
  }

  orderActions(status: string, paymentStatus?: string, fulfillmentMethod?: string | null, fulfillmentStatus?: string, shipmentStatus?: string): OrderAction[] {
    if (fulfillmentMethod === 'DELIVERY' && (fulfillmentStatus === 'CANCELLED' || shipmentStatus === 'CANCELLED')) return [];
    switch (status as OrderStatus) {
      case 'PENDING_PAYMENT': return paymentStatus === 'UNDER_REVIEW'
        ? []
        : [{ label: 'Cancelar', status: 'CANCELLED', danger: true }];
      case 'PAID': return [{ label: 'Preparar pedido', status: 'PREPARING' }];
      case 'PREPARING': return [{ label: 'Marcar listo', status: 'READY' }];
      case 'READY': return fulfillmentMethod === 'DELIVERY' ? [] : [{ label: 'Registrar entrega física', status: 'DELIVERED' }];
      default: return [];
    }
  }

  canRetryShipment(order: AdminOrder): boolean {
    return order.fulfillmentMethod === 'DELIVERY'
      && order.paymentStatus === 'APPROVED'
      && !['DELIVERED', 'CANCELLED'].includes(order.status)
      && ['RETRY', 'FAILED', 'CANCELLED'].includes(order.shipment?.status ?? '');
  }

  canCancelShipment(order: AdminOrder): boolean {
    const providerStatus = order.shipment?.providerStatus?.toLowerCase() ?? '';
    return order.fulfillmentMethod === 'DELIVERY'
      && order.shipment?.status === 'ACTIVE'
      && order.paymentStatus === 'APPROVED'
      && !['SHIPPED', 'DELIVERED', 'CANCELLED'].includes(order.status)
      && ['new', 'documentation_ready'].includes(providerStatus);
  }

  canCancelOrderAfterShipmentCancellation(order: AdminOrder): boolean {
    return order.fulfillmentMethod === 'DELIVERY'
      && order.shipment?.status === 'CANCELLED'
      && order.paymentStatus === 'APPROVED'
      && ['PAID', 'PREPARING', 'READY'].includes(order.status);
  }

  canConfirmBankTransferRefund(order: AdminOrder): boolean {
    return order.status === 'CANCELLED'
      && order.paymentMethod === 'BANK_TRANSFER'
      && order.paymentStatus === 'REFUND_PENDING';
  }

  canDownloadShipmentDocuments(order: AdminOrder): boolean {
    const providerStatus = order.shipment?.providerStatus?.toLowerCase() ?? '';
    return ['ACTIVE', 'INCIDENT', 'DELIVERED'].includes(order.shipment?.status ?? '')
      && ['documentation_ready', 'shipped', 'in_transit', 'out_for_delivery', 'delivered', 'delivered_with_damage'].includes(providerStatus);
  }

  retryShipment(order: AdminOrder): void {
    if (!this.canRetryShipment(order) || this.shipmentUpdating() !== null
      || this.orderUpdating() !== null || this.refundConfirming() !== null || this.ordersRefreshing()) return;
    const replacement = order.shipment?.status === 'CANCELLED';
    if (replacement && !confirm(`¿Crear un envío de reemplazo para el pedido #${order.id}? El pedido y el pago actuales seguirán vigentes.`)) return;
    this.clearMessages();
    this.shipmentUpdating.set(order.id);
    this.service.retryShipment(order.id).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.shipmentUpdating.set(null))).subscribe({
      next: () => {
        this.succeed(replacement
          ? `Envío de reemplazo solicitado para el pedido #${order.id}.`
          : `Reintento de envío solicitado para el pedido #${order.id}.`);
        this.refreshOrdersAfterShipmentAction();
      },
      error: () => this.fail(replacement
        ? 'No se pudo crear el envío de reemplazo.'
        : 'No se pudo reintentar la creación del envío.'),
    });
  }

  openCancellationDialog(order: AdminOrder, orderOnly = false, event?: Event): void {
    if (this.shipmentUpdating() !== null || this.orderUpdating() !== null
      || this.refundConfirming() !== null || this.ordersRefreshing()
      || (orderOnly ? !this.canCancelOrderAfterShipmentCancellation(order) : !this.canCancelShipment(order))) return;
    this.cancellationTrigger = event?.currentTarget instanceof HTMLElement ? event.currentTarget : null;
    this.cancellationScope = orderOnly ? 'ORDER' : 'SHIPMENT_ONLY';
    this.cancellationReason = null;
    this.cancellationDetail = '';
    this.cancellationError.set('');
    this.cancellationOrder.set(order);
    queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>(orderOnly ? '.cancellation-reason' : '.cancellation-scope input')?.focus());
  }

  closeCancellationDialog(): void {
    if (this.shipmentUpdating() !== null) return;
    this.dismissCancellationDialog();
  }

  submitCancellation(): void {
    const order = this.cancellationOrder();
    if (!order || this.shipmentUpdating() !== null || !this.canRetryCancellation(order, this.cancellationScope)) return;
    const detail = this.cancellationDetail.trim();
    const hasStandardReason = this.cancellationReasonOptions.some((option) => option.value === this.cancellationReason);
    if (this.cancellationScope === 'ORDER' && !hasStandardReason) {
      this.cancellationError.set('Seleccioná un motivo para cancelar el pedido.');
      queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>('.cancellation-reason')?.focus());
      return;
    }
    if (this.cancellationScope === 'ORDER' && detail.length > 500) {
      this.cancellationError.set('El detalle interno no puede superar los 500 caracteres.');
      queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>('.cancellation-detail')?.focus());
      return;
    }
    const scope = this.cancellationScope;
    const payload = scope === 'ORDER'
      ? { scope, reasonCode: this.cancellationReason!, ...(detail ? { internalDetail: detail } : {}) }
      : { scope };
    this.cancellationError.set('');
    this.shipmentUpdating.set(order.id);
    queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>('.cancellation-dialog')?.focus());
    this.service.cancelShipment(order.id, payload).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (updated) => {
        this.shipmentUpdating.set(null);
        this.updateLocalOrder(updated);
        if (!this.cancellationCompleted(updated, scope)) {
          this.dismissCancellationDialog();
          this.notifications.warning('La cancelación ya está en proceso. Actualizá los pedidos en unos segundos para ver el resultado.');
          return;
        }
        this.dismissCancellationDialog();
        this.succeed(this.cancellationSuccessMessage(updated, scope));
        if (scope === 'ORDER') this.refreshInventoriesAfterCancellation();
      },
      error: () => {
        this.cancellationError.set('No pudimos confirmar el resultado. Estamos actualizando el estado antes de que vuelvas a intentar.');
        this.refreshCancellationAfterError(order.id, scope);
      },
    });
  }

  openRefundDialog(order: AdminOrder, event?: Event): void {
    if (!this.canConfirmBankTransferRefund(order) || this.refundConfirming() !== null
      || this.shipmentUpdating() !== null || this.orderUpdating() !== null || this.ordersRefreshing()) return;
    this.refundTrigger = event?.currentTarget instanceof HTMLElement ? event.currentTarget : null;
    this.refundReference = '';
    this.refundError.set('');
    this.refundOrder.set(order);
    queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>('.refund-reference')?.focus());
  }

  closeRefundDialog(): void {
    if (this.refundConfirming() !== null) return;
    this.dismissRefundDialog();
  }

  confirmBankTransferRefund(): void {
    const order = this.refundOrder();
    if (!order || this.refundConfirming() !== null || this.shipmentUpdating() !== null
      || this.orderUpdating() !== null || this.ordersRefreshing() || !this.canConfirmBankTransferRefund(order)) return;
    const reference = this.refundReference.trim();
    if (reference.length > 200) {
      this.refundError.set('La referencia no puede superar los 200 caracteres.');
      queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>('.refund-reference')?.focus());
      return;
    }
    this.refundError.set('');
    this.refundConfirming.set(order.id);
    queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>('.refund-dialog')?.focus());
    this.service.confirmBankTransferRefund(order.id, reference || undefined).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (updated) => {
        this.refundConfirming.set(null);
        this.updateLocalOrder(updated);
        this.dismissRefundDialog();
        this.succeed(`Reintegro del pedido #${order.id} confirmado.`);
        this.refreshOrdersAfterShipmentAction();
      },
      error: () => {
        this.refundError.set('No pudimos confirmar el resultado. Estamos actualizando el pedido antes de que vuelvas a intentar.');
        this.refreshRefundAfterError(order.id);
      },
    });
  }

  handleDialogKeydown(event: KeyboardEvent, dialog: 'cancellation' | 'refund'): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      if (dialog === 'cancellation') this.closeCancellationDialog();
      else this.closeRefundDialog();
      return;
    }
    if (event.key !== 'Tab') return;
    const container = event.currentTarget as HTMLElement;
    const controls = Array.from(container.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [href], [tabindex]:not([tabindex="-1"])'));
    if (!controls.length) {
      event.preventDefault();
      container.focus();
      return;
    }
    const first = controls[0];
    const last = controls[controls.length - 1];
    if (event.shiftKey && (document.activeElement === container || document.activeElement === first || !container.contains(document.activeElement))) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && (document.activeElement === container || document.activeElement === last || !container.contains(document.activeElement))) {
      event.preventDefault();
      first.focus();
    }
  }

  downloadShipmentPdf(order: AdminOrder, kind: 'label' | 'document'): void {
    if (!this.canDownloadShipmentDocuments(order) || this.shipmentDocumentLoading() !== null) return;
    this.clearMessages();
    const key = `${order.id}-${kind}`;
    this.shipmentDocumentLoading.set(key);
    const request = kind === 'label' ? this.service.shipmentLabel(order.id) : this.service.shipmentDocument(order.id);
    request.pipe(takeUntil(this.cancelLoad), takeUntilDestroyed(this.destroyRef), finalize(() => this.shipmentDocumentLoading.set(null))).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `PIN-${order.id}-${kind === 'label' ? 'label' : 'document'}.pdf`;
        document.body.append(anchor);
        anchor.click();
        anchor.remove();
        setTimeout(() => URL.revokeObjectURL(url), 0);
      },
      error: () => this.fail(`No se pudo descargar ${kind === 'label' ? 'la etiqueta' : 'el documento'} del envío.`),
    });
  }

  shipmentStatusLabel(status: string): string {
    return ({
      PENDING_CREATE: 'Pendiente de creación', CREATING: 'Creando envío', ACTIVE: 'Activo', RETRY: 'Reintento pendiente',
      BLOCKED_PAYMENT: 'Bloqueado por pago', CANCELLED: 'Cancelado', DELIVERED: 'Entregado', INCIDENT: 'Con incidencia', FAILED: 'Fallido',
    } as Record<string, string>)[status] ?? status;
  }

  statusLabel(status: string): string {
    return estadoLabel(status, 'pedido');
  }

  statusCount(status: string): number { const counts = this.ordersSummary()?.statusCounts ?? {}; return status === 'ALL' ? Object.values(counts).reduce((sum, count) => sum + count, 0) : counts[status] ?? 0; }
  filterOrders(status: string): void {
    if (!ORDER_FILTERS.includes(status) || this.orderUpdating() !== null || this.shipmentUpdating() !== null || this.refundConfirming() !== null || this.ordersRefreshing() || this.proofReviewing() !== null) return;
    this.orderFilter.set(status);
    this.page.set(0);
    this.syncUrl({ orderStatus: status });
    if (this.section() === 'sales') this.reload(true);
  }
  openOrder(orderId: number): void { this.expandedOrder.set(orderId); this.filterOrders('ALL'); this.navigate('sales'); this.syncUrl({ order: orderId }); }
  toggleOrder(orderId: number): void {
    this.cancelOrderDetail.next();
    const previous = this.expandedOrder();
    const expanded = previous === orderId ? null : orderId;
    this.expandedOrder.set(expanded);
    if (previous !== null && previous !== expanded) {
      const leavingDetail = this.host.nativeElement.querySelector<HTMLElement>(`#order-detail-${previous}`);
      leavingDetail?.setAttribute('aria-hidden', 'true');
      leavingDetail?.setAttribute('inert', '');
    }
    this.syncUrl({ order: expanded });
  }
  stockForVariant(variantId: number): Inventory | undefined { return this.inventories().find((item) => item.variantId === variantId); }
  imageLabel(image: ProductImage, index?: number): string { return image.originalFilename || image.altText || (index === undefined ? `Imagen ${image.id}` : `Imagen ${index + 1}`); }
  variantImage(imageId: number | null | undefined): ProductImage | undefined { return this.selected()?.images.find((image) => image.id === imageId); }
  totalItems(order: AdminOrder): number { return order.items.reduce((total, item) => total + item.quantity, 0); }

  approveProof(proof: PendingBankTransferProof): void {
    if (!this.proofPreviewsReady(proof)) {
      this.setProofError(proof.id, 'Esperá a que carguen todas las vistas previas antes de aprobar.');
      return;
    }
    const amount = Number(this.proofAmounts[proof.id]);
    const reference = (this.proofReferences[proof.id] ?? '').trim();
    if (!Number.isFinite(amount) || amount !== proof.total) {
      this.setProofError(proof.id, `El importe debe coincidir exactamente con ${proof.total.toFixed(2)}.`, `#proof-amount-${proof.id}`);
      return;
    }
    const normalizedReference = reference.toUpperCase().replace(/[\s-]/g, '');
    if (!/^[A-Z0-9]{6,100}$/.test(normalizedReference)) {
      this.setProofError(proof.id, 'La referencia debe contener entre 6 y 100 letras o números.', `#proof-reference-${proof.id}`);
      return;
    }
    if (this.proofReviewing() !== null) return;
    this.proofReviewing.set(proof.id);
    this.clearProofError(proof.id);
    this.service.approveBankTransferProof(proof.id, amount, reference).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.proofReviewing.set(null))).subscribe({
      next: () => this.finishProofReview(proof, 'Comprobante aprobado y pago acreditado.'),
      error: () => this.setProofError(proof.id, 'No se pudo aprobar el comprobante.'),
    });
  }

  proofPreviewsReady(proof: PendingBankTransferProof): boolean {
    return proof.previewCount > 0 && (this.proofPreviewUrls()[proof.id]?.length ?? 0) === proof.previewCount;
  }

  rejectProof(proof: PendingBankTransferProof): void {
    const reason = (this.proofRejectionReasons[proof.id] ?? '').trim();
    if (!reason || reason.length > 1000) {
      this.setProofError(proof.id, 'Ingresá un motivo de hasta 1000 caracteres.', `#proof-reason-${proof.id}`);
      return;
    }
    if (this.proofReviewing() !== null) return;
    this.proofReviewing.set(proof.id);
    this.clearProofError(proof.id);
    this.service.rejectBankTransferProof(proof.id, reason).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.proofReviewing.set(null))).subscribe({
      next: () => this.finishProofReview(proof, 'Comprobante rechazado; el cliente podrá ver el motivo.'),
      error: () => this.setProofError(proof.id, 'No se pudo rechazar el comprobante.'),
    });
  }

  loadProofPreviews(proof: PendingBankTransferProof): void {
    if (this.previewLoading() !== null || this.proofPreviewsReady(proof) || this.section() !== 'sales') return;
    this.previewLoading.set(proof.id);
    this.clearProofError(proof.id);
    const generation = ++this.proofPreviewGeneration;
    from(Array.from({ length: proof.previewCount }, (_, index) => index)).pipe(
      mergeMap((index) => this.service.bankTransferProofPreview(proof.id, index).pipe(map((blob) => ({ index, blob }))), 2),
      toArray(), takeUntil(this.cancelPreview), finalize(() => this.previewLoading.set(null)),
    ).subscribe({
        next: (results) => {
          const urls = results.sort((a, b) => a.index - b.index).map(({ blob }) => URL.createObjectURL(blob));
          if (generation !== this.proofPreviewGeneration) {
            urls.forEach((url) => URL.revokeObjectURL(url));
            return;
          }
          this.proofPreviewUrls.update((current) => ({ ...current, [proof.id]: urls }));
        },
        error: () => this.setProofError(proof.id, 'No se pudo cargar la vista previa sanitizada.'),
      });
  }

  private finishProofReview(proof: PendingBankTransferProof, message: string): void {
    if (this.previewLoading() === proof.id) this.cancelPreview.next();
    this.pendingTransferProofs.update((proofs) => proofs.filter((current) => current.id !== proof.id));
    this.revokeProofPreviews(proof.id);
    delete this.proofAmounts[proof.id];
    delete this.proofReferences[proof.id];
    delete this.proofRejectionReasons[proof.id];
    this.notifications.success(message);
    this.refreshOrdersAfterShipmentAction();
  }

  private clearProofError(proofId: string): void {
    this.proofReviewError.update((current) => ({ ...current, [proofId]: '' }));
  }

  private setProofError(proofId: string, message: string, selector?: string): void {
    this.proofReviewError.update((current) => ({ ...current, [proofId]: message }));
    if (selector) queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus());
  }

  private revokeProofPreviews(proofId: string): void {
    this.proofPreviewUrls()[proofId]?.forEach((url) => URL.revokeObjectURL(url));
    this.proofPreviewUrls.update((current) => {
      const next = { ...current };
      delete next[proofId];
      return next;
    });
  }

  private clearProofPreviews(): void {
    this.cancelPreview.next();
    this.proofPreviewGeneration++;
    Object.values(this.proofPreviewUrls()).flat().forEach((url) => URL.revokeObjectURL(url));
    this.proofPreviewUrls.set({});
  }

  private emptyProduct(): ProductForm {
    return {
      name: '', slug: '', description: '', price: 0, categoryId: this.categories()[0]?.id ?? 0, brandId: this.brands()[0]?.id ?? 0,
      shippingWeightGrams: 0, shippingHeightCm: 0, shippingWidthCm: 0, shippingLengthCm: 0, shippingClassificationId: 1,
      mustKeepVertical: false, specifications: [], hasColorVariants: false,
      variants: [{ colorName: DEFAULT_PRODUCT_VARIANT_NAME, colorHex: null, imageId: null }],
    };
  }
  private finishProductSave(product: Product, message: string, preservePendingImages = false): void {
    this.products.update((products) => products.some((current) => current.id === product.id)
      ? products.map((current) => current.id === product.id ? product : current)
      : [...products, product]);
    if (preservePendingImages) {
      this.selected.set(product);
      Object.assign(this.form, this.productForm(product));
      this.productSnapshot = this.productState();
      this.selectedVariantId.set(product.variants[0]?.id ?? null);
      this.inventory.set(this.inventories().find((item) => item.variantId === this.selectedVariantId()) ?? null);
    } else {
      this.select(product, false, undefined, true);
    }
    this.succeed(message, preservePendingImages ? 'warning' : 'success');
    this.service.products(this.search, this.page()).pipe(takeUntil(this.cancelLoad), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (page) => { this.products.set(page.content); this.totalElements.set(page.totalElements); this.totalPages.set(page.totalPages); },
      error: () => this.notifications.warning('El producto se guardo, pero no pudimos actualizar la lista.'),
    });
  }
  private clearPendingImages(): void { this.revokePendingImages(); this.pendingImages.set([]); }
  private revokePendingImages(): void { this.pendingImages().forEach((item) => URL.revokeObjectURL(item.previewUrl)); }
  private slug(value: string): string { return value.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, ''); }
  private clearMessages(): void { this.error.set(''); }
  private fail(message: string): void { this.error.set(message); }
  private succeed(message: string, tone: 'success' | 'warning' = 'success'): void {
    this.error.set('');
    if (tone === 'warning') this.notifications.warning(message);
    else this.notifications.success(message);
  }
  private failAndFocus(message: string, selector: string): void {
    this.fail(message);
    queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus());
  }
  private initializeTaxonomySelections(): void {
    if (!this.isValidTaxonomyId(this.form.categoryId, this.categories())) this.form.categoryId = this.categories()[0]?.id ?? 0;
    if (!this.isValidTaxonomyId(this.form.brandId, this.brands())) this.form.brandId = this.brands()[0]?.id ?? 0;
    this.productSnapshot = this.productState();
  }
  private isValidTaxonomyId(value: number, items: Array<{ id: number }>): boolean {
    return Number.isInteger(value) && value > 0 && items.some((item) => item.id === value);
  }
  private productForm(product: Product): ProductForm {
    return {
      name: product.name,
      slug: product.slug,
      description: product.description,
      price: product.price,
      categoryId: product.categoryId,
      brandId: product.brandId,
      shippingWeightGrams: product.shippingWeightGrams ?? 0,
      shippingHeightCm: product.shippingHeightCm ?? 0,
      shippingWidthCm: product.shippingWidthCm ?? 0,
      shippingLengthCm: product.shippingLengthCm ?? 0,
      shippingClassificationId: product.shippingClassificationId ?? 1,
      mustKeepVertical: product.mustKeepVertical ?? false,
      specifications: product.specifications.map(({ groupName, name, value, highlighted }) => ({ groupName, name, value, highlighted })),
      hasColorVariants: hasVisibleColorVariants(product.variants),
      variants: product.variants.map(({ id, colorName, colorHex, imageId }) => ({ id, colorName, colorHex, imageId: imageId ?? null })),
    };
  }
  private productPayload(): ProductPayload {
    const { hasColorVariants, ...payload } = this.form;
    if (hasColorVariants) return payload;
    const defaultVariant: ProductVariantPayload = { colorName: DEFAULT_PRODUCT_VARIANT_NAME, colorHex: null, imageId: null };
    if (this.form.variants[0]?.id) defaultVariant.id = this.form.variants[0].id;
    return { ...payload, variants: [defaultVariant] };
  }
  private productState(): string { return JSON.stringify(this.form); }
  private confirmDiscardProductChanges(): boolean {
    if (this.section() !== 'catalog' || (this.productState() === this.productSnapshot && !this.pendingImages().length)) return true;
    return confirm('Tenés cambios sin guardar en el producto. ¿Querés descartarlos?');
  }
  hasUnsavedChanges(): boolean {
    if (this.section() === 'home') return this.homeEditor()?.hasUnsavedChanges() ?? this.homeDirty();
    return this.section() === 'catalog' && (this.productState() !== this.productSnapshot || this.pendingImages().length > 0);
  }

  hasPendingOperation(): boolean {
    return this.saving() || this.deactivatingProduct() || this.adjustingStock()
      || this.proofReviewing() !== null || this.taxonomySaving() || Boolean(this.deletingTaxonomy())
      || this.deletingImage() !== null || this.orderUpdating() !== null || this.shipmentUpdating() !== null
      || this.refundConfirming() !== null || this.ordersRefreshing() || this.shipmentDocumentLoading() !== null
      || (this.section() === 'home' && (this.homeEditor()?.isBusy() ?? false));
  }

  confirmDiscard(): boolean {
    if (this.hasPendingOperation()) return false;
    if (!this.hasUnsavedChanges()) return true;
    if (this.section() === 'home') {
      return this.homeEditor()?.confirmDiscard()
        ?? confirm('Tenés cambios sin guardar en la sección del inicio. ¿Querés descartarlos?');
    }
    return confirm('Tenés cambios sin guardar en el producto. ¿Querés descartarlos?');
  }

  @HostListener('window:beforeunload', ['$event'])
  protectUnsavedChanges(event: BeforeUnloadEvent): void {
    if (!this.hasUnsavedChanges() && !this.hasPendingOperation()) return;
    event.preventDefault();
    event.returnValue = '';
  }
  private refreshOrdersAfterShipmentAction(): void {
    this.ordersRefreshing.set(true);
    forkJoin({ orders: this.service.ordersPage(this.page(), this.orderFilter()), summary: this.service.ordersSummary() }).pipe(takeUntil(this.cancelLoad), takeUntilDestroyed(this.destroyRef), finalize(() => this.ordersRefreshing.set(false))).subscribe({
      next: ({ orders, summary }) => { this.orders.set(orders.content); this.totalPages.set(orders.totalPages); this.totalElements.set(orders.totalElements); this.ordersSummary.set(summary); this.loadRequestedOrder(); },
      error: () => this.notifications.warning('La acción se registró, pero no pudimos actualizar el estado del envío. Usá Actualizar para reintentar.'),
    });
  }
  private loadRequestedOrder(): void {
    this.cancelOrderDetail.next();
    this.requestedOrder.set(null);
    const id = this.expandedOrder();
    if (this.section() !== 'sales' || id === null || this.orders().some((order) => order.id === id)) return;
    this.service.order(id).pipe(takeUntil(this.cancelOrderDetail), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (order) => this.requestedOrder.set(order),
      error: () => this.fail(`No se pudo cargar el pedido #${id}.`),
    });
  }

  private updateLocalOrder(updated: AdminOrder): void {
    this.orders.update((orders) => orders.map((order) => order.id === updated.id ? updated : order));
    if (this.requestedOrder()?.id === updated.id) this.requestedOrder.set(updated);
  }
  private refreshInventoriesAfterCancellation(): void {
    this.refreshOrdersAfterShipmentAction();
  }
  private refreshCancellationAfterError(orderId: number, scope: CancellationScope): void {
    // Reconcile by ID, not by the filtered page: cancellation can move the order out of that page.
    this.service.order(orderId).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.shipmentUpdating.set(null))).subscribe({
      next: (updated) => {
        if (this.cancellationOrder()?.id !== orderId) return;
        this.updateLocalOrder(updated);
        const completed = this.cancellationCompleted(updated, scope);
        if (completed) {
          this.dismissCancellationDialog();
          this.succeed(this.cancellationSuccessMessage(updated, scope));
          if (scope === 'ORDER') this.refreshInventoriesAfterCancellation();
        } else if (!this.canRetryCancellation(updated, scope)) {
          this.dismissCancellationDialog();
          this.notifications.warning('El pedido o el envío cambió de estado y ya no admite esta cancelación.');
        } else {
          this.cancellationOrder.set(updated);
          this.cancellationError.set('La cancelación no quedó confirmada. Revisá el estado actualizado antes de volver a intentar.');
          queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>(
            scope === 'ORDER' ? '.cancellation-reason' : '.cancellation-scope input')?.focus());
        }
      },
      error: () => {
        this.cancellationError.set('No pudimos actualizar el estado. Esperá unos segundos antes de volver a intentar.');
        queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>('.cancellation-dialog')?.focus());
      },
    });
  }
  private refreshRefundAfterError(orderId: number): void {
    this.service.order(orderId).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.refundConfirming.set(null))).subscribe({
      next: (updated) => {
        if (this.refundOrder()?.id !== orderId) return;
        this.updateLocalOrder(updated);
        if (updated.paymentStatus === 'REFUNDED') {
          this.dismissRefundDialog();
          this.succeed(`Reintegro del pedido #${orderId} confirmado.`);
          this.refreshOrdersAfterShipmentAction();
        } else if (!this.canConfirmBankTransferRefund(updated)) {
          this.dismissRefundDialog();
          this.notifications.warning('El pedido cambió de estado y ya no admite confirmar este reintegro.');
        } else {
          this.refundOrder.set(updated);
          this.refundError.set('El reintegro no quedó confirmado. Revisá el pedido actualizado antes de volver a intentar.');
          queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>('.refund-reference')?.focus());
        }
      },
      error: () => {
        this.refundError.set('No pudimos actualizar el pedido. Esperá unos segundos antes de volver a intentar.');
        queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>('.refund-dialog')?.focus());
      },
    });
  }
  private canRetryCancellation(order: AdminOrder, scope: CancellationScope): boolean {
    if (scope === 'SHIPMENT_ONLY') return this.canCancelShipment(order);
    return order.shipment?.status === 'CANCELLED'
      ? this.canCancelOrderAfterShipmentCancellation(order)
      : this.canCancelShipment(order);
  }
  private cancellationCompleted(order: AdminOrder, scope: CancellationScope): boolean {
    return scope === 'ORDER' ? order.status === 'CANCELLED' : order.shipment?.status === 'CANCELLED';
  }
  private cancellationSuccessMessage(order: AdminOrder, scope: CancellationScope): string {
    if (scope === 'SHIPMENT_ONLY') return `Envío del pedido #${order.id} cancelado en Zipnova.`;
    if (order.paymentMethod === 'MERCADO_PAGO') {
      return order.paymentStatus === 'REFUNDED'
        ? `Pedido #${order.id} cancelado y reintegro de Mercado Pago confirmado.`
        : `Pedido #${order.id} cancelado. El reintegro de Mercado Pago quedó pendiente.`;
    }
    return `Pedido #${order.id} cancelado. El reintegro por transferencia quedó pendiente de confirmación.`;
  }
  private dismissCancellationDialog(): void {
    const orderId = this.cancellationOrder()?.id;
    const trigger = this.cancellationTrigger;
    this.cancellationOrder.set(null);
    this.cancellationError.set('');
    this.cancellationTrigger = null;
    this.restoreDialogFocus(trigger, orderId);
  }
  private dismissRefundDialog(): void {
    const orderId = this.refundOrder()?.id;
    const trigger = this.refundTrigger;
    this.refundOrder.set(null);
    this.refundError.set('');
    this.refundTrigger = null;
    this.restoreDialogFocus(trigger, orderId);
  }
  private restoreDialogFocus(trigger: HTMLElement | null, orderId?: number): void {
    queueMicrotask(() => {
      if (trigger?.isConnected) trigger.focus();
      else if (orderId) this.host.nativeElement.querySelector<HTMLElement>(`[aria-controls="order-detail-${orderId}"]`)?.focus();
      if (!this.host.nativeElement.contains(document.activeElement)) {
        this.host.nativeElement.querySelector<HTMLElement>('nav button[aria-current="page"]')?.focus();
      }
    });
  }
  private isSection(value: string | null): value is AdminSection { return ['overview', 'sales', 'catalog', 'inventory', 'home'].includes(value ?? ''); }
  private syncUrl(queryParams: Record<string, string | number | null | undefined>): void {
    if (!this.router || !this.route) return;
    void this.router.navigate([], { relativeTo: this.route, queryParams, queryParamsHandling: 'merge', replaceUrl: true });
  }
}

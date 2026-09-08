import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, HostListener, CUSTOM_ELEMENTS_SCHEMA, computed, inject, output, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, forkJoin, Subscription } from 'rxjs';
import { resolveHomeBannerUrl, HomeSectionMode, HomeSectionSort } from '../../home/home-sections.service';
import { ProductListItemResponse } from '../../catalog/catalog.service';
import { AppButtonDirective } from '../../../shared/ui/app-button.directive';
import { AppCardDirective } from '../../../shared/ui/app-card.directive';
import { AppFeedbackComponent } from '../../../shared/ui/feedback/app-feedback.component';
import { AppInputComponent } from '../../../shared/ui/input/app-input.component';
import { AppSelectComponent, AppSelectOption } from '../../../shared/ui/select/app-select.component';
import { AppTextareaComponent } from '../../../shared/ui/textarea/app-textarea.component';
import { AdminHomeSection, HomeBannerDevice, HomeSectionPayload, HomeSectionsAdminService } from './home-sections-admin.service';
import { Category } from '../admin.service';

interface HomeSectionForm extends HomeSectionPayload {}
interface PendingBanner { readonly file: File; readonly previewUrl: string; }

@Component({
  selector: 'app-home-sections-admin',
  imports: [AppButtonDirective, AppCardDirective, AppFeedbackComponent, AppInputComponent, AppSelectComponent, AppTextareaComponent, FormsModule],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './home-sections.component.html',
  styleUrl: './home-sections.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeSectionsComponent {
  private readonly service = inject(HomeSectionsAdminService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private snapshot = '';
  private candidateRequest?: Subscription;
  private candidateGeneration = 0;
  private bannerPreviewRequests = new Subscription();
  private bannerPreviewGeneration = 0;
  private readonly bannerPreviewObjectUrls = new Set<string>();

  readonly dirtyChange = output<boolean>();
  readonly sections = signal<AdminHomeSection[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly selected = signal<AdminHomeSection | null>(null);
  readonly candidates = signal<ProductListItemResponse[]>([]);
  readonly selectedProducts = signal<ProductListItemResponse[]>([]);
  readonly loading = signal(false);
  readonly candidatesLoading = signal(false);
  readonly saving = signal(false);
  readonly deleting = signal(false);
  readonly ordering = signal(false);
  readonly bannerBusy = signal<HomeBannerDevice | null>(null);
  readonly error = signal('');
  readonly message = signal('');
  readonly dirty = signal(false);
  readonly candidatePage = signal(0);
  readonly candidateTotalPages = signal(0);
  readonly candidateTotalElements = signal(0);
  readonly pendingDesktop = signal<PendingBanner | null>(null);
  readonly pendingMobile = signal<PendingBanner | null>(null);
  readonly bannerPreviews = signal<Partial<Record<HomeBannerDevice, string>>>({});
  readonly bannerPreviewLoading = signal<Partial<Record<HomeBannerDevice, boolean>>>({});
  readonly mutationBusy = computed(() => this.loading() || this.saving() || this.deleting() || this.ordering() || this.bannerBusy() !== null);
  readonly busy = computed(() => this.mutationBusy() || this.candidatesLoading());
  candidateSearch = '';
  readonly form: HomeSectionForm = this.emptyForm();

  readonly modeOptions: readonly AppSelectOption[] = [
    { value: 'MANUAL', label: 'Selección manual' },
    { value: 'AUTOMATIC', label: 'Selección automática' },
  ];
  readonly sortOptions: readonly AppSelectOption[] = [
    { value: 'NEWEST', label: 'Más nuevos primero' },
    { value: 'NAME_ASC', label: 'Nombre A-Z' },
    { value: 'NAME_DESC', label: 'Nombre Z-A' },
    { value: 'PRICE_ASC', label: 'Menor precio' },
    { value: 'PRICE_DESC', label: 'Mayor precio' },
  ];
  readonly selectedProductIds = computed(() => new Set(this.selectedProducts().map((product) => product.id)));
  readonly bannerUrl = resolveHomeBannerUrl;

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.cancelCandidates();
      this.clearPendingBanners();
      this.clearBannerPreviews();
    });
    this.reload(true);
  }

  reload(force = false): void {
    if (this.busy()) return;
    if (!force && !this.confirmDiscard()) return;
    const selectedId = this.selected()?.id ?? null;
    this.loading.set(true);
    this.clearMessages();
    forkJoin({ sections: this.service.sections(), categories: this.service.categories() })
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: ({ sections, categories }) => {
          this.sections.set(sections);
          this.categories.set(categories);
          const selection = sections.find((section) => section.id === selectedId) ?? sections[0] ?? null;
          if (selection) this.open(selection, true);
          else this.resetEditor(true);
        },
        error: () => this.error.set('No se pudieron cargar las secciones del inicio.'),
      });
  }

  newSection(): void {
    if (this.mutationBusy()) return;
    if (this.sections().length >= 6) {
      this.error.set('El inicio admite como máximo 6 secciones.');
      return;
    }
    if (!this.confirmDirtyDiscard()) return;
    this.cancelCandidates();
    this.resetEditor(true);
    this.loadCandidates();
    queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>('[name="homeTitle"]')?.focus());
  }

  open(section: AdminHomeSection, force = false): void {
    if (!force && this.mutationBusy()) return;
    if (!force && section.id !== this.selected()?.id && !this.confirmDirtyDiscard()) return;
    this.cancelCandidates();
    this.clearPendingBanners();
    this.selected.set(section);
    Object.assign(this.form, this.formFrom(section));
    this.selectedProducts.set(this.orderedProducts(section));
    this.candidateSearch = '';
    this.candidates.set([]);
    this.candidatePage.set(0);
    this.clearMessages();
    this.captureSnapshot();
    this.loadBannerPreviews(section);
    if (section.mode === 'MANUAL') this.loadCandidates();
  }

  setMode(mode: HomeSectionMode): void {
    this.form.mode = mode;
    this.markDirty();
    if (mode === 'MANUAL' && !this.candidates().length) this.loadCandidates();
    else if (mode === 'AUTOMATIC') this.cancelCandidates();
  }

  toggleCategory(id: number, checked: boolean): void {
    if (checked && !this.form.categoryIds.includes(id) && this.form.categoryIds.length >= 20) {
      this.error.set('Podés seleccionar hasta 20 categorías por sección automática.');
      return;
    }
    this.form.categoryIds = checked
      ? [...new Set([...this.form.categoryIds, id])]
      : this.form.categoryIds.filter((categoryId) => categoryId !== id);
    this.markDirty();
  }

  categorySelected(id: number): boolean { return this.form.categoryIds.includes(id); }

  searchCandidates(): void {
    this.candidatePage.set(0);
    this.loadCandidates();
  }

  changeCandidatePage(page: number): void {
    if (page < 0 || page >= this.candidateTotalPages()) return;
    this.candidatePage.set(page);
    this.loadCandidates();
  }

  addProduct(product: ProductListItemResponse): void {
    if (this.selectedProductIds().has(product.id)) return;
    if (this.selectedProducts().length >= 24) {
      this.error.set('Podés seleccionar hasta 24 productos por sección.');
      return;
    }
    this.selectedProducts.update((products) => [...products, product]);
    this.markDirty();
  }

  removeProduct(index: number): void {
    this.selectedProducts.update((products) => products.filter((_, current) => current !== index));
    this.markDirty();
  }

  moveProduct(index: number, change: -1 | 1): void {
    const target = index + change;
    if (target < 0 || target >= this.selectedProducts().length) return;
    this.selectedProducts.update((current) => {
      const products = [...current];
      [products[index], products[target]] = [products[target], products[index]];
      return products;
    });
    this.markDirty();
  }

  save(form?: NgForm): void {
    if (this.saving()) return;
    this.clearMessages();
    if (form?.invalid || !this.form.title.trim()) {
      this.error.set('Ingresá un título para la sección.');
      queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>('[name="homeTitle"]')?.focus());
      return;
    }
    const limit = Number(this.form.productLimit);
    if (!Number.isInteger(limit) || limit < 1 || limit > 12) {
      this.error.set('El límite de productos debe ser un entero entre 1 y 12.');
      queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>('[name="homeProductLimit"]')?.focus());
      return;
    }
    if (this.form.mode === 'AUTOMATIC' && !this.form.categoryIds.length) {
      this.error.set('Seleccioná al menos una categoría para el modo automático.');
      queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>('.category-picker input')?.focus());
      return;
    }
    if (this.form.mode === 'MANUAL' && !this.selectedProducts().length) {
      this.error.set('Seleccioná al menos un producto para el modo manual.');
      queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>('.candidate-search app-input')?.focus());
      return;
    }
    const payload = this.payload();
    this.saving.set(true);
    const request = this.selected() ? this.service.update(this.selected()!.id, payload) : this.service.create(payload);
    request.pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.saving.set(false))).subscribe({
      next: (saved) => {
        this.sections.update((sections) => sections.some((section) => section.id === saved.id)
          ? sections.map((section) => section.id === saved.id ? saved : section)
          : [...sections, saved]);
        this.selected.set(saved);
        Object.assign(this.form, this.formFrom(saved));
        this.selectedProducts.set(this.orderedProducts(saved, this.selectedProducts()));
        this.captureSnapshot();
        this.message.set('Sección publicada correctamente.');
      },
      error: () => this.error.set('No se pudo guardar la sección. Revisá los datos e intentá nuevamente.'),
    });
  }

  deleteSection(): void {
    const section = this.selected();
    if (!section || this.deleting() || !confirm(`¿Eliminar la sección "${section.title}" del inicio?`)) return;
    this.deleting.set(true);
    this.service.delete(section.id).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.deleting.set(false))).subscribe({
      next: () => {
        this.sections.update((sections) => sections.filter((current) => current.id !== section.id));
        const next = this.sections()[0];
        if (next) this.open(next, true); else this.resetEditor(true);
        this.message.set('Sección eliminada.');
      },
      error: () => this.error.set('No se pudo eliminar la sección.'),
    });
  }

  moveSection(index: number, change: -1 | 1): void {
    const target = index + change;
    if (target < 0 || target >= this.sections().length || this.busy() || !this.confirmDiscard()) return;
    const previous = this.sections();
    const reordered = [...previous];
    [reordered[index], reordered[target]] = [reordered[target], reordered[index]];
    this.sections.set(reordered);
    this.ordering.set(true);
    this.service.reorder(reordered.map((section) => section.id))
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.ordering.set(false)))
      .subscribe({
        next: () => this.message.set('Orden del inicio actualizado.'),
        error: () => { this.sections.set(previous); this.error.set('No se pudo actualizar el orden.'); },
      });
  }

  selectBanner(event: Event, device: HomeBannerDevice): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      this.error.set('Seleccioná un archivo de imagen válido.');
      return;
    }
    const pending = { file, previewUrl: URL.createObjectURL(file) };
    this.replacePendingBanner(device, pending);
    this.markDirty();
  }

  uploadBanner(device: HomeBannerDevice): void {
    const section = this.selected();
    const pending = device === 'DESKTOP' ? this.pendingDesktop() : this.pendingMobile();
    if (!section || !pending || this.bannerBusy()) return;
    this.bannerBusy.set(device);
    this.service.uploadBanner(section.id, device, pending.file)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.bannerBusy.set(null)))
      .subscribe({
        next: (banner) => {
          this.replacePendingBanner(device, null);
          const updated = { ...section, [device === 'DESKTOP' ? 'bannerDesktopUrl' : 'bannerMobileUrl']: banner.url };
          this.replaceSavedSection(updated);
          this.loadBannerPreviews(updated);
          this.message.set(`Banner ${device === 'DESKTOP' ? 'desktop' : 'mobile'} publicado.`);
        },
        error: () => this.error.set('No se pudo subir el banner.'),
      });
  }

  deleteBanner(device: HomeBannerDevice): void {
    const section = this.selected();
    if (!section || this.bannerBusy() || !confirm(`¿Eliminar el banner ${device === 'DESKTOP' ? 'desktop' : 'mobile'}?`)) return;
    this.bannerBusy.set(device);
    this.service.deleteBanner(section.id, device)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.bannerBusy.set(null)))
      .subscribe({
        next: () => {
          const updated = { ...section, [device === 'DESKTOP' ? 'bannerDesktopUrl' : 'bannerMobileUrl']: null };
          this.replaceSavedSection(updated);
          this.loadBannerPreviews(updated);
          this.message.set('Banner eliminado.');
        },
        error: () => this.error.set('No se pudo eliminar el banner.'),
      });
  }

  discardPendingBanner(device: HomeBannerDevice): void {
    this.replacePendingBanner(device, null);
    this.updateDirty();
  }

  notifyFormChange(): void { queueMicrotask(() => this.updateDirty()); }

  confirmDiscard(): boolean {
    if (this.isBusy()) return false;
    return this.confirmDirtyDiscard();
  }

  hasUnsavedChanges(): boolean { return this.dirty(); }
  isBusy(): boolean { return this.busy(); }

  bannerPreview(device: HomeBannerDevice): string { return this.bannerPreviews()[device] ?? ''; }
  isBannerPreviewLoading(device: HomeBannerDevice): boolean { return this.bannerPreviewLoading()[device] ?? false; }

  @HostListener('window:beforeunload', ['$event'])
  protectBrowserUnload(event: BeforeUnloadEvent): void {
    if (!this.dirty() && !this.isBusy()) return;
    event.preventDefault();
    event.returnValue = '';
  }

  private loadCandidates(): void {
    this.candidateRequest?.unsubscribe();
    const generation = ++this.candidateGeneration;
    const sectionId = this.selected()?.id ?? null;
    this.candidatesLoading.set(true);
    this.candidateRequest = this.service.productCandidates(this.candidateSearch, this.candidatePage())
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => {
        if (generation === this.candidateGeneration) this.candidatesLoading.set(false);
      }))
      .subscribe({
        next: (page) => {
          if (generation !== this.candidateGeneration || (this.selected()?.id ?? null) !== sectionId) return;
          this.candidates.set(page.content);
          const productsById = new Map(page.content.map((product) => [product.id, product]));
          this.selectedProducts.update((products) => products.map((product) => productsById.get(product.id) ?? product));
          this.candidatePage.set(page.number);
          this.candidateTotalPages.set(page.totalPages);
          this.candidateTotalElements.set(page.totalElements);
        },
        error: () => { if (generation === this.candidateGeneration) this.error.set('No se pudieron cargar los productos candidatos.'); },
      });
  }

  private cancelCandidates(): void {
    this.candidateGeneration++;
    this.candidateRequest?.unsubscribe();
    this.candidateRequest = undefined;
    this.candidatesLoading.set(false);
  }

  private loadBannerPreviews(section: AdminHomeSection): void {
    this.clearBannerPreviews();
    const generation = this.bannerPreviewGeneration;
    const banners: Array<[HomeBannerDevice, string | null]> = [
      ['DESKTOP', section.bannerDesktopUrl], ['MOBILE', section.bannerMobileUrl],
    ];
    for (const [device, url] of banners) {
      if (!url) continue;
      if (!this.isProtectedBanner(url)) {
        this.bannerPreviews.update((previews) => ({ ...previews, [device]: resolveHomeBannerUrl(url) }));
        continue;
      }
      this.bannerPreviewLoading.update((loading) => ({ ...loading, [device]: true }));
      this.bannerPreviewRequests.add(this.service.fetchBanner(url).pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          if (generation === this.bannerPreviewGeneration) {
            this.bannerPreviewLoading.update((loading) => ({ ...loading, [device]: false }));
          }
        }),
      ).subscribe({
        next: (blob) => {
          if (generation !== this.bannerPreviewGeneration) return;
          const previewUrl = URL.createObjectURL(blob);
          this.bannerPreviewObjectUrls.add(previewUrl);
          this.bannerPreviews.update((previews) => ({ ...previews, [device]: previewUrl }));
        },
        error: () => undefined,
      }));
    }
  }

  private clearBannerPreviews(): void {
    this.bannerPreviewGeneration++;
    this.bannerPreviewRequests.unsubscribe();
    this.bannerPreviewRequests = new Subscription();
    this.bannerPreviewObjectUrls.forEach((url) => URL.revokeObjectURL(url));
    this.bannerPreviewObjectUrls.clear();
    this.bannerPreviews.set({});
    this.bannerPreviewLoading.set({});
  }

  private isProtectedBanner(url: string): boolean {
    try {
      return new URL(url, globalThis.location?.origin ?? 'http://localhost').pathname.startsWith('/api/admin/');
    } catch {
      return false;
    }
  }

  private confirmDirtyDiscard(): boolean {
    return !this.dirty() || confirm('Tenés cambios sin guardar en la sección del inicio. ¿Querés descartarlos?');
  }

  private payload(): HomeSectionPayload {
    return {
      eyebrow: this.form.eyebrow.trim(),
      title: this.form.title.trim(),
      description: this.form.description.trim(),
      buttonLabel: this.form.buttonLabel.trim(),
      mode: this.form.mode,
      productLimit: Number(this.form.productLimit),
      sort: this.form.sort,
      categoryIds: [...this.form.categoryIds],
      productIds: this.form.mode === 'MANUAL' ? this.selectedProducts().map((product) => product.id) : [],
      active: this.form.active,
    };
  }

  private formFrom(section: AdminHomeSection): HomeSectionForm {
    return {
      eyebrow: section.eyebrow ?? '', title: section.title ?? '', description: section.description ?? '',
      buttonLabel: section.buttonLabel ?? '', mode: section.mode, productLimit: section.productLimit,
      sort: section.sort ?? 'NEWEST', categoryIds: [...section.categoryIds],
      productIds: [...section.productIds], active: section.active,
    };
  }

  private orderedProducts(section: AdminHomeSection, fallback: readonly ProductListItemResponse[] = []): ProductListItemResponse[] {
    const available = new Map([...(section.products ?? []), ...fallback].map((product) => [product.id, product]));
    return section.productIds.map((id) => available.get(id) ?? this.productPlaceholder(id));
  }

  private productPlaceholder(id: number): ProductListItemResponse {
    return { id, name: `Producto #${id}`, slug: '', price: 0, categoryId: 0, categoryName: 'Detalle no disponible', brandId: 0, brandName: '', images: [], inStock: true };
  }

  private emptyForm(): HomeSectionForm {
    return { eyebrow: '', title: '', description: '', buttonLabel: '', mode: 'MANUAL', productLimit: 8, sort: 'NEWEST', categoryIds: [], productIds: [], active: true };
  }

  private resetEditor(force = false): void {
    if (!force && !this.confirmDiscard()) return;
    this.clearPendingBanners();
    this.cancelCandidates();
    this.clearBannerPreviews();
    this.selected.set(null);
    Object.assign(this.form, this.emptyForm());
    this.selectedProducts.set([]);
    this.clearMessages();
    this.captureSnapshot();
  }

  private replaceSavedSection(section: AdminHomeSection): void {
    this.sections.update((sections) => sections.map((current) => current.id === section.id ? section : current));
    this.selected.set(section);
    this.updateDirty();
  }

  private replacePendingBanner(device: HomeBannerDevice, pending: PendingBanner | null): void {
    const state = device === 'DESKTOP' ? this.pendingDesktop : this.pendingMobile;
    const current = state();
    if (current) URL.revokeObjectURL(current.previewUrl);
    state.set(pending);
  }

  private clearPendingBanners(): void {
    this.replacePendingBanner('DESKTOP', null);
    this.replacePendingBanner('MOBILE', null);
  }

  private state(includePending = true): string {
    const fileState = (pending: PendingBanner | null) => pending ? [pending.file.name, pending.file.size, pending.file.lastModified] : null;
    return JSON.stringify({
      form: this.form,
      products: this.selectedProducts().map((product) => product.id),
      desktop: includePending ? fileState(this.pendingDesktop()) : null,
      mobile: includePending ? fileState(this.pendingMobile()) : null,
    });
  }

  private captureSnapshot(): void {
    this.snapshot = this.state(false);
    this.updateDirty();
  }

  private markDirty(): void { this.setDirty(true); }
  private updateDirty(): void { this.setDirty(this.state() !== this.snapshot); }
  private setDirty(value: boolean): void { if (this.dirty() !== value) { this.dirty.set(value); this.dirtyChange.emit(value); } }
  private clearMessages(): void { this.error.set(''); this.message.set(''); }
}

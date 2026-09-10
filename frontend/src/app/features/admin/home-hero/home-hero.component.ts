import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, HostListener, CUSTOM_ELEMENTS_SCHEMA, computed, inject, output, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, Subscription } from 'rxjs';
import { AppButtonDirective } from '../../../shared/ui/app-button.directive';
import { AppCardDirective } from '../../../shared/ui/app-card.directive';
import { AppFeedbackComponent } from '../../../shared/ui/feedback/app-feedback.component';
import { AppInputComponent } from '../../../shared/ui/input/app-input.component';
import { AppTextareaComponent } from '../../../shared/ui/textarea/app-textarea.component';
import { resolveApiContentUrl } from '../../../core/utils/api-content-url';
import { AdminHomeHeroImage, AdminHomeHeroSlide, HomeHeroAdminService, HomeHeroImageDevice, HomeHeroSlidePayload } from './home-hero-admin.service';

interface PendingImage { readonly file: File; readonly previewUrl: string; }

const MAX_SLIDES = 5;

@Component({
  selector: 'app-home-hero-admin',
  imports: [AppButtonDirective, AppCardDirective, AppFeedbackComponent, AppInputComponent, AppTextareaComponent, FormsModule],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './home-hero.component.html',
  styleUrl: './home-hero.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeHeroComponent {
  private readonly service = inject(HomeHeroAdminService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private snapshot = '';
  private previewRequests = new Subscription();
  private previewGeneration = 0;
  private readonly previewObjectUrls = new Set<string>();

  readonly slides = signal<AdminHomeHeroSlide[]>([]);
  readonly selected = signal<AdminHomeHeroSlide | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly deleting = signal(false);
  readonly ordering = signal(false);
  readonly imageBusy = signal<HomeHeroImageDevice | null>(null);
  readonly error = signal('');
  readonly message = signal('');
  readonly dirty = signal(false);
  readonly pendingDesktop = signal<PendingImage | null>(null);
  readonly pendingMobile = signal<PendingImage | null>(null);
  readonly previews = signal<Partial<Record<HomeHeroImageDevice, string>>>({});
  readonly previewLoading = signal<Partial<Record<HomeHeroImageDevice, boolean>>>({});
  readonly mutationBusy = computed(() => this.loading() || this.saving() || this.deleting() || this.ordering() || this.imageBusy() !== null);
  readonly dirtyChange = output<boolean>();
  protected readonly MAX_SLIDES = MAX_SLIDES;

  form: HomeHeroSlidePayload = this.emptyForm();

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.clearPendingImages();
      this.clearPreviews();
    });
    this.reload(true);
  }

  reload(force = false): void {
    if (this.mutationBusy()) return;
    if (!force && !this.confirmDiscard()) return;
    const selectedId = this.selected()?.id ?? null;
    this.loading.set(true);
    this.clearMessages();
    this.service.slides()
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: (slides) => {
          this.slides.set(slides);
          const selection = slides.find((slide) => slide.id === selectedId) ?? slides[0] ?? null;
          if (selection) this.open(selection, true);
          else this.resetEditor(true);
        },
        error: () => this.error.set('No se pudieron cargar los slides del inicio.'),
      });
  }

  newSlide(): void {
    if (this.mutationBusy()) return;
    if (this.slides().length >= MAX_SLIDES) {
      this.error.set(`El inicio admite como máximo ${MAX_SLIDES} slides de presentación.`);
      return;
    }
    if (!this.confirmDirtyDiscard()) return;
    this.resetEditor(true);
    queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>('[name="heroTitle"]')?.focus());
  }

  open(slide: AdminHomeHeroSlide, force = false): void {
    if (!force && this.mutationBusy()) return;
    if (!force && slide.id !== this.selected()?.id && !this.confirmDirtyDiscard()) return;
    this.clearPendingImages();
    this.selected.set(slide);
    Object.assign(this.form, this.formFrom(slide));
    this.clearMessages();
    this.captureSnapshot();
    this.loadPreviews(slide);
  }

  save(form?: NgForm): void {
    if (this.saving()) return;
    this.clearMessages();
    const missing = [
      ['eyebrow', this.form.eyebrow], ['título', this.form.title], ['texto destacado', this.form.accent],
      ['descripción', this.form.description], ['etiqueta del botón', this.form.linkLabel],
      ['ruta de destino', this.form.link], ['texto alternativo', this.form.altText],
    ].filter(([, value]) => !value.trim()).map(([label]) => label);
    if (form?.invalid || missing.length) {
      this.error.set(`Completá ${missing.length ? missing.join(', ') : 'los campos obligatorios'} del slide.`);
      return;
    }
    if (!this.validInternalLink(this.form.link)) {
      this.error.set('El enlace del botón debe ser una ruta interna de la tienda, por ejemplo /catalog.');
      return;
    }
    const payload = this.payload();
    this.saving.set(true);
    const request = this.selected() ? this.service.update(this.selected()!.id, payload) : this.service.create(payload);
    request.pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.saving.set(false))).subscribe({
      next: (saved) => {
        this.slides.update((slides) => slides.some((slide) => slide.id === saved.id)
          ? slides.map((slide) => slide.id === saved.id ? saved : slide)
          : [...slides, saved]);
        this.selected.set(saved);
        Object.assign(this.form, this.formFrom(saved));
        this.captureSnapshot();
        this.message.set('Slide publicado correctamente.');
      },
      error: (response: unknown) => {
        const detail = response instanceof HttpErrorResponse && response.error && typeof response.error === 'object'
          ? (response.error as Record<string, unknown>)['detail'] : null;
        this.error.set(typeof detail === 'string' ? detail : 'No se pudo guardar el slide. Revisá los datos e intentá nuevamente.');
      },
    });
  }

  deleteSlide(): void {
    const slide = this.selected();
    if (!slide || this.deleting() || !confirm(`¿Eliminar el slide "${slide.title}"?`)) return;
    this.deleting.set(true);
    this.service.delete(slide.id).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.deleting.set(false))).subscribe({
      next: () => {
        this.slides.update((slides) => slides.filter((current) => current.id !== slide.id));
        const next = this.slides()[0];
        if (next) this.open(next, true); else this.resetEditor(true);
        this.message.set('Slide eliminado.');
      },
      error: () => this.error.set('No se pudo eliminar el slide.'),
    });
  }

  moveSlide(index: number, change: -1 | 1): void {
    const target = index + change;
    if (target < 0 || target >= this.slides().length || this.busy() || !this.confirmDiscard()) return;
    const previous = this.slides();
    const reordered = [...previous];
    [reordered[index], reordered[target]] = [reordered[target], reordered[index]];
    this.slides.set(reordered);
    this.ordering.set(true);
    this.service.reorder(reordered.map((slide) => slide.id))
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.ordering.set(false)))
      .subscribe({
        next: () => this.message.set('Orden del inicio actualizado.'),
        error: () => { this.slides.set(previous); this.error.set('No se pudo actualizar el orden.'); },
      });
  }

  selectImage(event: Event, device: HomeHeroImageDevice): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      this.error.set('Seleccioná un archivo de imagen válido.');
      return;
    }
    const pending = { file, previewUrl: URL.createObjectURL(file) };
    this.replacePendingImage(device, pending);
    this.markDirty();
  }

  uploadImage(device: HomeHeroImageDevice): void {
    const slide = this.selected();
    const pending = device === 'DESKTOP' ? this.pendingDesktop() : this.pendingMobile();
    if (!slide || !pending || this.imageBusy()) return;
    this.imageBusy.set(device);
    this.service.uploadImage(slide.id, device, pending.file)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.imageBusy.set(null)))
      .subscribe({
        next: (image) => {
          this.replacePendingImage(device, null);
          const updated = this.replaceImage(slide, image);
          this.selected.set(updated);
          this.slides.update((slides) => slides.map((current) => current.id === updated.id ? updated : current));
          this.loadPreviews(updated);
          this.message.set(`Imagen ${device === 'DESKTOP' ? 'desktop' : 'mobile'} publicada.`);
        },
        error: () => this.error.set('No se pudo subir la imagen.'),
      });
  }

  deleteImage(device: HomeHeroImageDevice): void {
    const slide = this.selected();
    if (!slide || this.imageBusy() || !confirm(`¿Eliminar la imagen ${device === 'DESKTOP' ? 'desktop' : 'mobile'}?`)) return;
    this.imageBusy.set(device);
    this.service.deleteImage(slide.id, device)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.imageBusy.set(null)))
      .subscribe({
        next: () => {
          const updated = { ...slide, images: slide.images.filter((image) => image.device !== device) };
          this.selected.set(updated);
          this.slides.update((slides) => slides.map((current) => current.id === updated.id ? updated : current));
          this.loadPreviews(updated);
          this.message.set('Imagen eliminada.');
        },
        error: () => this.error.set('No se pudo eliminar la imagen.'),
      });
  }

  discardPendingImage(device: HomeHeroImageDevice): void {
    this.replacePendingImage(device, null);
    this.markDirty();
  }

  notifyFormChange(): void { queueMicrotask(() => this.markDirty()); }

  confirmDiscard(): boolean {
    if (this.isBusy()) return false;
    return this.confirmDirtyDiscard();
  }

  hasUnsavedChanges(): boolean { return this.dirty(); }
  isBusy(): boolean { return this.busy(); }

  imagePreview(device: HomeHeroImageDevice): string { return this.previews()[device] ?? ''; }
  isPreviewLoading(device: HomeHeroImageDevice): boolean { return this.previewLoading()[device] ?? false; }

  private confirmDirtyDiscard(): boolean {
    return !this.dirty() || confirm('Tenés cambios sin guardar en el hero del inicio. ¿Querés descartarlos?');
  }

  private payload(): HomeHeroSlidePayload {
    return {
      eyebrow: this.form.eyebrow.trim(),
      title: this.form.title.trim(),
      accent: this.form.accent.trim(),
      description: this.form.description.trim(),
      link: this.form.link.trim(),
      linkLabel: this.form.linkLabel.trim(),
      showLoginLink: this.form.showLoginLink,
      altText: this.form.altText.trim(),
      active: this.form.active,
    };
  }

  private formFrom(slide: AdminHomeHeroSlide): HomeHeroSlidePayload {
    return {
      eyebrow: slide.eyebrow, title: slide.title, accent: slide.accent, description: slide.description,
      link: slide.link, linkLabel: slide.linkLabel, showLoginLink: slide.showLoginLink,
      altText: slide.altText, active: slide.active,
    };
  }

  private replaceImage(slide: AdminHomeHeroSlide, image: AdminHomeHeroImage): AdminHomeHeroSlide {
    return { ...slide, images: [...slide.images.filter((current) => current.device !== image.device), image] };
  }

  private imageFor(slide: AdminHomeHeroSlide, device: HomeHeroImageDevice): AdminHomeHeroImage | undefined {
    return slide.images.find((image) => image.device === device);
  }

  private loadPreviews(slide: AdminHomeHeroSlide): void {
    this.clearPreviews();
    const generation = this.previewGeneration;
    for (const device of ['DESKTOP', 'MOBILE'] as const) {
      const image = this.imageFor(slide, device);
      if (!image) continue;
      if (!this.isProtectedUrl(image.url)) {
        this.previews.update((previews) => ({ ...previews, [device]: resolveApiContentUrl(image.url) }));
        continue;
      }
      this.previewLoading.update((loading) => ({ ...loading, [device]: true }));
      this.previewRequests.add(this.service.fetchImage(image.url).pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          if (generation === this.previewGeneration) {
            this.previewLoading.update((loading) => ({ ...loading, [device]: false }));
          }
        }),
      ).subscribe({
        next: (blob) => {
          if (generation !== this.previewGeneration) return;
          const previewUrl = URL.createObjectURL(blob);
          this.previewObjectUrls.add(previewUrl);
          this.previews.update((previews) => ({ ...previews, [device]: previewUrl }));
        },
        error: () => undefined,
      }));
    }
  }

  private clearPreviews(): void {
    this.previewGeneration++;
    this.previewRequests.unsubscribe();
    this.previewRequests = new Subscription();
    this.previewObjectUrls.forEach((url) => URL.revokeObjectURL(url));
    this.previewObjectUrls.clear();
    this.previews.set({});
    this.previewLoading.set({});
  }

  private isProtectedUrl(url: string): boolean {
    try {
      return new URL(url, globalThis.location?.origin ?? 'http://localhost').pathname.startsWith('/api/admin/');
    } catch {
      return false;
    }
  }

  private validInternalLink(link: string): boolean {
    return link.startsWith('/') && !link.startsWith('//');
  }

  private emptyForm(): HomeHeroSlidePayload {
    return { eyebrow: '', title: '', accent: '', description: '', link: '/catalog', linkLabel: '', showLoginLink: false, altText: '', active: false };
  }

  private resetEditor(force = false): void {
    if (!force && !this.confirmDiscard()) return;
    this.clearPendingImages();
    this.clearPreviews();
    this.selected.set(null);
    Object.assign(this.form, this.emptyForm());
    this.clearMessages();
    this.captureSnapshot();
  }

  private replacePendingImage(device: HomeHeroImageDevice, pending: PendingImage | null): void {
    const state = device === 'DESKTOP' ? this.pendingDesktop : this.pendingMobile;
    const current = state();
    if (current) URL.revokeObjectURL(current.previewUrl);
    state.set(pending);
  }

  private clearPendingImages(): void {
    this.replacePendingImage('DESKTOP', null);
    this.replacePendingImage('MOBILE', null);
  }

  private state(includePending = true): string {
    const fileState = (pending: PendingImage | null) => pending ? [pending.file.name, pending.file.size, pending.file.lastModified] : null;
    return JSON.stringify({
      form: this.form,
      desktop: includePending ? fileState(this.pendingDesktop()) : null,
      mobile: includePending ? fileState(this.pendingMobile()) : null,
    });
  }

  private captureSnapshot(): void {
    this.snapshot = this.state(false);
    this.markDirty();
  }

  protected busy(): boolean { return this.mutationBusy(); }
  private markDirty(): void {
    const value = this.state() !== this.snapshot;
    if (this.dirty() !== value) {
      this.dirty.set(value);
      this.dirtyChange.emit(value);
    }
  }
  private clearMessages(): void { this.error.set(''); this.message.set(''); }
}

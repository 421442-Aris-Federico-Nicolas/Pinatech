import { TestBed } from '@angular/core/testing';
import { of, Subject } from 'rxjs';
import { ProductListItemResponse } from '../../catalog/catalog.service';
import { HomeHeroAdminService } from '../home-hero/home-hero-admin.service';
import { AdminHomeSection, HomeSectionPayload, HomeSectionsAdminService } from './home-sections-admin.service';
import { HomeSectionsComponent } from './home-sections.component';

describe('HomeSectionsComponent', () => {
  const product = (id: number): ProductListItemResponse => ({
    id, name: `Producto ${id}`, slug: `producto-${id}`, price: 100, categoryId: 2,
    categoryName: 'Hardware', brandId: 1, brandName: 'Pina', images: [], inStock: true,
  });
  const section = (id = 1, overrides: Partial<AdminHomeSection> = {}): AdminHomeSection => ({
    id, displayOrder: id - 1, eyebrow: 'Hardware', title: `Sección ${id}`, description: 'Descripción',
    buttonLabel: 'Ver productos', mode: 'MANUAL', productLimit: 8, sort: 'NEWEST',
    bannerDesktopUrl: null, bannerMobileUrl: null, categoryIds: [], productIds: [id], active: true, ...overrides,
  });

  const candidatePage = (content = [product(1), product(2)], number = 0) => ({ content, number, size: 24, totalPages: 2, totalElements: 30 });

  async function setup(initial = [section()], overrides: Record<string, unknown> = {}) {
    const service = {
      sections: vi.fn(() => of(initial)),
      categories: vi.fn(() => of([{ id: 2, name: 'Hardware', slug: 'hardware' }])),
      productCandidates: vi.fn((_search = '', page = 0) => of(candidatePage(undefined, page))),
      create: vi.fn((payload: HomeSectionPayload) => of(section(9, { ...payload, productIds: payload.productIds }))),
      update: vi.fn((id: number, payload: HomeSectionPayload) => of(section(id, { ...payload, productIds: payload.productIds }))),
      reorder: vi.fn(() => of(void 0)), delete: vi.fn(() => of(void 0)),
      uploadBanner: vi.fn((id: number, device: 'DESKTOP' | 'MOBILE') => of({ id, device, url: `/api/banner/${device.toLowerCase()}` })),
      deleteBanner: vi.fn(() => of(void 0)),
      fetchBanner: vi.fn(() => of(new Blob(['banner'], { type: 'image/webp' }))),
    };
    Object.assign(service, overrides);
    await TestBed.configureTestingModule({
      imports: [HomeSectionsComponent],
      providers: [
        { provide: HomeSectionsAdminService, useValue: service },
        { provide: HomeHeroAdminService, useValue: {
          slides: () => of([]),
          create: () => of({ id: 0, displayOrder: 0, active: false, eyebrow: '', title: '', accent: '', description: '', link: '/catalog', linkLabel: '', showLoginLink: false, altText: '', images: [] }),
          update: () => of({ id: 0, displayOrder: 0, active: false, eyebrow: '', title: '', accent: '', description: '', link: '/catalog', linkLabel: '', showLoginLink: false, altText: '', images: [] }),
          reorder: () => of(void 0), delete: () => of(void 0),
          uploadImage: () => of({ id: 0, device: 'DESKTOP', url: '/x', width: 1, height: 1, originalFilename: '' }),
          deleteImage: () => of(void 0), fetchImage: () => of(new Blob()),
        } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(HomeSectionsComponent);
    fixture.detectChanges();
    await fixture.whenStable(); fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, service };
  }

  it('loads configuration and categories only while mounted, then pages manual candidates by 24', async () => {
    const { component, service } = await setup();
    expect(service.sections).toHaveBeenCalledOnce();
    expect(service.categories).toHaveBeenCalledOnce();
    expect(service.productCandidates).toHaveBeenCalledWith('', 0);
    component.changeCandidatePage(1);
    expect(service.productCandidates).toHaveBeenLastCalledWith('', 1);
    expect(component.candidateTotalElements()).toBe(30);
  });

  it('switches between the hero and section editors without remounting either one', async () => {
    const { component, fixture } = await setup();

    expect(component.editorTab()).toBe('sections');
    expect(fixture.nativeElement.querySelector('#home-sections-panel').hidden).toBe(false);
    expect(fixture.nativeElement.querySelector('#home-hero-panel').hidden).toBe(true);

    component.selectEditorTab('hero');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('#home-sections-panel').hidden).toBe(true);
    expect(fixture.nativeElement.querySelector('#home-hero-panel').hidden).toBe(false);
    expect(fixture.nativeElement.querySelector('#home-hero-panel app-home-hero-admin')).toBeTruthy();
  });

  it('creates, updates and deletes sections with direct publication', async () => {
    const { component, service } = await setup([]);
    component.newSection();
    component.form.title = 'Nueva portada';
    component.addProduct(product(2));
    component.save();
    expect(service.create).toHaveBeenCalledWith(expect.objectContaining({ title: 'Nueva portada', productIds: [2], active: true }));
    expect(component.selected()?.id).toBe(9);

    component.form.title = 'Portada editada';
    component.notifyFormChange();
    await Promise.resolve();
    component.save();
    expect(service.update).toHaveBeenCalledWith(9, expect.objectContaining({ title: 'Portada editada' }));

    vi.spyOn(globalThis, 'confirm').mockReturnValue(true);
    component.deleteSection();
    expect(service.delete).toHaveBeenCalledWith(9);
    expect(component.sections()).toEqual([]);
  });

  it('enforces six sections and persists section ordering', async () => {
    const all = Array.from({ length: 6 }, (_, index) => section(index + 1));
    const { component, service } = await setup(all);
    component.newSection();
    expect(component.error()).toContain('máximo 6');
    component.moveSection(0, 1);
    expect(service.reorder).toHaveBeenCalledWith([2, 1, 3, 4, 5, 6]);
    expect(component.sections().map((item) => item.id)).toEqual([2, 1, 3, 4, 5, 6]);
  });

  it('keeps manual order but omits manual IDs when saving automatic mode', async () => {
    const { component, service, fixture } = await setup();
    component.addProduct(product(2));
    component.moveProduct(1, -1);
    expect(component.selectedProducts().map((item) => item.id)).toEqual([2, 1]);
    component.setMode('AUTOMATIC');
    component.toggleCategory(2, true);
    component.form.buttonLabel = 'Explorar hardware';
    component.save();
    expect(service.update).toHaveBeenCalledWith(1, expect.objectContaining({ mode: 'AUTOMATIC', categoryIds: [2], productIds: [] }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('El botón del carrusel no se muestra');
  });

  it('confirms dirty item changes and protects browser unload', async () => {
    const { component } = await setup([section(1), section(2)]);
    component.setMode('AUTOMATIC');
    expect(component.dirty()).toBe(true);
    const confirmation = vi.spyOn(globalThis, 'confirm').mockReturnValue(false);
    component.open(section(2));
    expect(component.selected()?.id).toBe(1);
    expect(confirmation).toHaveBeenCalled();
    const event = new Event('beforeunload', { cancelable: true }) as BeforeUnloadEvent;
    component.protectBrowserUnload(event);
    expect(event.defaultPrevented).toBe(true);
  });

  it('hydrates configured manual products from the admin response without a perpetual loading label', async () => {
    const configured = { ...product(7), name: 'Mouse configurado', brandName: 'Logitech', categoryName: 'Periféricos' };
    const { component, fixture } = await setup([section(7, { products: [configured], productIds: [7] })]);
    fixture.detectChanges();
    expect(component.selectedProducts()).toEqual([configured]);
    expect(fixture.nativeElement.textContent).toContain('Mouse configurado');
    expect(fixture.nativeElement.textContent).not.toContain('Cargando datos');
  });

  it('normalizes nullable optional copy and banners in admin responses', async () => {
    const { component, fixture } = await setup([section(1, {
      eyebrow: null, description: null, buttonLabel: null, bannerDesktopUrl: null, bannerMobileUrl: null,
    })]);
    fixture.detectChanges();
    expect(component.form.eyebrow).toBe('');
    expect(component.form.description).toBe('');
    expect(component.form.buttonLabel).toBe('');
    expect(fixture.nativeElement.querySelectorAll('.banner-empty')).toHaveLength(2);
  });

  it('previews, uploads and deletes desktop and mobile banners independently', async () => {
    const createUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:preview');
    const revokeUrl = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    const { component, service } = await setup();
    const file = new File(['banner'], 'desktop.jpg', { type: 'image/jpeg' });
    component.form.title = 'Título todavía no guardado';
    component.notifyFormChange();
    await Promise.resolve();
    component.selectBanner({ target: { files: [file], value: 'desktop.jpg' } } as unknown as Event, 'DESKTOP');
    expect(component.pendingDesktop()?.previewUrl).toBe('blob:preview');
    component.uploadBanner('DESKTOP');
    expect(service.uploadBanner).toHaveBeenCalledWith(1, 'DESKTOP', file);
    expect(component.selected()?.bannerDesktopUrl).toBe('/api/banner/desktop');
    expect(revokeUrl).toHaveBeenCalledWith('blob:preview');
    expect(component.dirty()).toBe(true);

    vi.spyOn(globalThis, 'confirm').mockReturnValue(true);
    component.deleteBanner('DESKTOP');
    expect(service.deleteBanner).toHaveBeenCalledWith(1, 'DESKTOP');
    expect(component.selected()?.bannerDesktopUrl).toBeNull();
    expect(component.dirty()).toBe(true);
    createUrl.mockRestore(); revokeUrl.mockRestore();
  });

  it('fetches protected banner previews as blobs and revokes every generated object URL', async () => {
    const createUrl = vi.spyOn(URL, 'createObjectURL')
      .mockReturnValueOnce('blob:desktop-1').mockReturnValueOnce('blob:desktop-2');
    const revokeUrl = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    const first = section(1, { bannerDesktopUrl: '/api/admin/home/banners/1/content' });
    const second = section(2, { bannerDesktopUrl: '/api/admin/home/banners/2/content' });
    const { component, fixture, service } = await setup([first, second]);

    expect(service.fetchBanner).toHaveBeenCalledWith(first.bannerDesktopUrl);
    expect(component.bannerPreview('DESKTOP')).toBe('blob:desktop-1');
    expect(fixture.nativeElement.querySelector('.banner-grid img').src).toContain('blob:desktop-1');

    component.open(second);
    expect(revokeUrl).toHaveBeenCalledWith('blob:desktop-1');
    expect(component.bannerPreview('DESKTOP')).toBe('blob:desktop-2');
    fixture.destroy();
    expect(revokeUrl).toHaveBeenCalledWith('blob:desktop-2');
    createUrl.mockRestore(); revokeUrl.mockRestore();
  });

  it('replaces candidate searches and section requests without accepting late responses', async () => {
    const first = new Subject<ReturnType<typeof candidatePage>>();
    const search = new Subject<ReturnType<typeof candidatePage>>();
    const nextSection = new Subject<ReturnType<typeof candidatePage>>();
    const productCandidates = vi.fn().mockReturnValueOnce(first).mockReturnValueOnce(search).mockReturnValueOnce(nextSection);
    const { component } = await setup([section(1), section(2)], { productCandidates });

    component.candidateSearch = 'mouse';
    component.searchCandidates();
    expect(first.observed).toBe(false);
    first.next(candidatePage([product(10)]));
    expect(component.candidates()).toEqual([]);

    component.open(section(2));
    expect(search.observed).toBe(false);
    search.next(candidatePage([product(20)]));
    expect(component.candidates()).toEqual([]);
    nextSection.next(candidatePage([product(22)]));
    expect(component.candidates().map((item) => item.id)).toEqual([22]);
  });

  it('limits automatic sections to twenty categories', async () => {
    const { component } = await setup([]);
    component.newSection();
    component.setMode('AUTOMATIC');
    for (let id = 1; id <= 21; id++) component.toggleCategory(id, true);
    expect(component.form.categoryIds).toEqual(Array.from({ length: 20 }, (_, index) => index + 1));
    expect(component.error()).toContain('hasta 20 categorías');
  });
});

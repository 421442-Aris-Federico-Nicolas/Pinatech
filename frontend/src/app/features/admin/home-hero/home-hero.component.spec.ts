import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import {
  AdminHomeHeroImage,
  AdminHomeHeroSlide,
  HomeHeroAdminService,
} from './home-hero-admin.service';
import { HomeHeroComponent } from './home-hero.component';

describe('HomeHeroComponent', () => {
  const image = (
    device: 'DESKTOP' | 'MOBILE',
    url = `/api/admin/home/hero/images/${device.toLowerCase()}/content`,
  ): AdminHomeHeroImage => ({
    id: device === 'DESKTOP' ? 9 : 10,
    device,
    url,
    width: device === 'DESKTOP' ? 2000 : 720,
    height: device === 'DESKTOP' ? 848 : 512,
    originalFilename: `${device.toLowerCase()}.jpg`,
  });
  const slide = (images: readonly AdminHomeHeroImage[] = []): AdminHomeHeroSlide => ({
    id: 3,
    displayOrder: 0,
    active: false,
    eyebrow: 'Eyebrow',
    title: 'Title',
    accent: 'Accent',
    description: 'Description',
    link: '/catalog',
    linkLabel: 'Explore',
    showLoginLink: false,
    altText: 'Alt text',
    images,
  });

  async function setup(initial: readonly AdminHomeHeroSlide[] = []) {
    const service = {
      slides: vi.fn(() => of(initial)),
      create: vi.fn(),
      update: vi.fn(),
      reorder: vi.fn(() => of(void 0)),
      delete: vi.fn(() => of(void 0)),
      uploadImage: vi.fn(),
      deleteImage: vi.fn(() => of(void 0)),
      fetchImage: vi.fn(() => of(new Blob(['image'], { type: 'image/jpeg' }))),
    };
    await TestBed.configureTestingModule({
      imports: [HomeHeroComponent],
      providers: [{ provide: HomeHeroAdminService, useValue: service }],
    }).compileComponents();
    const fixture = TestBed.createComponent(HomeHeroComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, service };
  }

  it('loads protected previews without cancelling the request immediately', async () => {
    const createUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:hero-preview');
    const revokeUrl = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    const { fixture, component, service } = await setup([slide([image('DESKTOP')])]);

    expect(service.fetchImage).toHaveBeenCalledWith('/api/admin/home/hero/images/desktop/content');
    expect(component.imagePreview('DESKTOP')).toBe('blob:hero-preview');
    expect(fixture.nativeElement.querySelector('.image-grid img').src).toContain(
      'blob:hero-preview',
    );

    fixture.destroy();
    expect(revokeUrl).toHaveBeenCalledWith('blob:hero-preview');
    createUrl.mockRestore();
    revokeUrl.mockRestore();
  });

  it('rejects external CTA routes before creating a slide', async () => {
    const { component, service } = await setup();
    component.newSlide();
    component.form.eyebrow = 'Eyebrow';
    component.form.title = 'New title';
    component.form.accent = 'Accent';
    component.form.description = 'Description';
    component.form.linkLabel = 'Explore';
    component.form.link = 'https://example.com';
    component.form.altText = 'Alt text';

    component.save();

    expect(component.error()).toContain('ruta interna');
    expect(service.create).not.toHaveBeenCalled();
  });

  it('blocks incomplete slides before sending them to the API', async () => {
    const { component, service } = await setup();
    component.newSlide();

    component.save();

    expect(component.error()).toContain('eyebrow');
    expect(service.create).not.toHaveBeenCalled();
  });
});

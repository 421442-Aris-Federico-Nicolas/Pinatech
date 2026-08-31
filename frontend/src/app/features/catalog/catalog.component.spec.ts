import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of } from 'rxjs';
import { CatalogComponent } from './catalog.component';
import { CatalogService, Page, Product } from './catalog.service';

describe('CatalogComponent', () => {
  it('renders the mascot as the only zero-result status and keeps clear filters usable', async () => {
    const emptyPage: Page<Product> = { content: [], totalPages: 0, totalElements: 0, number: 0, size: 12 };
    const navigate = vi.fn();
    await TestBed.configureTestingModule({
      imports: [CatalogComponent],
      providers: [
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap({ search: 'inexistente', category: '3' })) } },
        { provide: Router, useValue: { navigate, navigateByUrl: vi.fn() } },
        { provide: CatalogService, useValue: { categories: () => of([]), brands: () => of([]), getProducts: () => of(emptyPage) } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(CatalogComponent);
    fixture.detectChanges();
    fixture.componentInstance.resultsAnnounce.set(true);
    fixture.detectChanges();

    const mascot = fixture.nativeElement.querySelector('app-pinatech-empty-state') as HTMLElement;
    const clearButton = [...mascot.querySelectorAll('button')]
      .find((button: HTMLButtonElement) => button.textContent?.includes('Limpiar filtros')) as HTMLButtonElement;
    expect(mascot).toBeTruthy();
    expect(mascot.querySelector('[role="status"]')?.textContent).toContain('No encontramos productos');
    expect(fixture.nativeElement.querySelectorAll('[role="status"]')).toHaveLength(1);

    clearButton.click();

    expect(fixture.componentInstance.filters.search).toBe('');
    expect(fixture.componentInstance.filters.categoryId).toBeNull();
    expect(navigate).toHaveBeenCalled();
  });
});

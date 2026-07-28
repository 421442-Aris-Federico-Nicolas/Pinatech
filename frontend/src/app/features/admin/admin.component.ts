import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { forkJoin } from 'rxjs';
import { Product } from '../catalog/catalog.service';
import { AdminService, Brand, Category, Inventory, ProductPayload } from './admin.service';

interface ProductForm extends ProductPayload {}

@Component({
  imports: [DecimalPipe, FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminComponent {
  private readonly service = inject(AdminService);
  readonly products = signal<Product[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly brands = signal<Brand[]>([]);
  readonly selected = signal<Product | null>(null);
  readonly inventory = signal<Inventory | null>(null);
  readonly error = signal('');
  readonly success = signal('');
  readonly saving = signal(false);
  readonly form: ProductForm = this.emptyProduct();
  categoryName = '';
  categorySlug = '';
  brandName = '';
  adjustment = 0;
  adjustmentReason = '';

  constructor() { this.reload(); }

  reload(): void {
    forkJoin({ products: this.service.products(), categories: this.service.categories(), brands: this.service.brands() }).subscribe({
      next: ({ products, categories, brands }) => {
        this.products.set(products.content);
        this.categories.set(categories);
        this.brands.set(brands);
        if (!this.selected()) this.resetProduct();
      },
      error: () => this.fail('No se pudieron cargar los datos de administración.'),
    });
  }

  select(product: Product): void {
    this.selected.set(product);
    Object.assign(this.form, product);
    this.inventory.set(null);
    this.service.inventory(product.id).subscribe({ next: (inventory) => this.inventory.set(inventory), error: () => this.fail('No se encontró inventario para el producto seleccionado.') });
  }

  resetProduct(): void {
    this.selected.set(null);
    this.inventory.set(null);
    Object.assign(this.form, this.emptyProduct());
  }

  updateSlug(): void {
    if (!this.selected()) this.form.slug = this.form.name.toLowerCase().trim().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');
  }

  saveProduct(): void {
    this.clearMessages();
    this.saving.set(true);
    const request = this.selected()
      ? this.service.updateProduct(this.selected()!.id, this.form)
      : this.service.createProduct(this.form);
    request.subscribe({
      next: (product) => {
        this.success.set(this.selected() ? 'Producto actualizado.' : 'Producto creado con stock inicial en cero.');
        this.saving.set(false);
        this.reload();
        this.select(product);
      },
      error: () => { this.saving.set(false); this.fail('No se pudo guardar el producto. Revisá los campos requeridos.'); },
    });
  }

  deleteProduct(): void {
    const product = this.selected();
    if (!product || !confirm(`¿Dar de baja "${product.name}"?`)) return;
    this.service.deleteProduct(product.id).subscribe({ next: () => { this.success.set('Producto dado de baja.'); this.resetProduct(); this.reload(); }, error: () => this.fail('No se pudo dar de baja el producto.') });
  }

  addCategory(): void {
    const name = this.categoryName.trim();
    const slug = this.categorySlug.trim() || name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');
    if (!name || !slug) return this.fail('Indicá nombre y slug para la categoría.');
    this.service.createCategory({ name, slug }).subscribe({ next: () => { this.categoryName = ''; this.categorySlug = ''; this.success.set('Categoría creada.'); this.reload(); }, error: () => this.fail('No se pudo crear la categoría.') });
  }

  addBrand(): void {
    const name = this.brandName.trim();
    if (!name) return this.fail('Indicá un nombre para la marca.');
    this.service.createBrand(name).subscribe({ next: () => { this.brandName = ''; this.success.set('Marca creada.'); this.reload(); }, error: () => this.fail('No se pudo crear la marca.') });
  }

  adjustStock(): void {
    const product = this.selected();
    if (!product || !this.adjustment || !this.adjustmentReason.trim()) return this.fail('Indicá un ajuste distinto de cero y su motivo.');
    this.service.adjustInventory(product.id, Number(this.adjustment), this.adjustmentReason.trim()).subscribe({
      next: (inventory) => { this.inventory.set(inventory); this.adjustment = 0; this.adjustmentReason = ''; this.success.set('Stock actualizado.'); },
      error: () => this.fail('No se pudo ajustar el stock. El resultado no puede ser negativo.'),
    });
  }

  private emptyProduct(): ProductForm {
    return { name: '', slug: '', description: '', price: 0, categoryId: 0, brandId: 0 };
  }

  private clearMessages(): void { this.error.set(''); this.success.set(''); }
  private fail(message: string): void { this.success.set(''); this.error.set(message); }
}

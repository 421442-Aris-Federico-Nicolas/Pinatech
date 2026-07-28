import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { CatalogService, Page, Product } from './catalog.service';
import { CartService } from '../../core/cart/cart.service';
@Component({ imports: [DecimalPipe, FormsModule, MatButtonModule, MatCardModule], templateUrl: './catalog.component.html', styles: ['main{margin:auto;max-width:1200px;padding:3rem 1.5rem}.grid{display:grid;gap:1rem;grid-template-columns:repeat(auto-fit,minmax(240px,1fr))}.search{display:flex;gap:.5rem}input{flex:1;padding:.7rem}footer{display:flex;gap:1rem;justify-content:center;margin-top:2rem}strong{color:#047857}'], changeDetection: ChangeDetectionStrategy.OnPush }) export class CatalogComponent { private readonly service = inject(CatalogService); readonly cart=inject(CartService); readonly search=signal(''); readonly page=signal<Page<Product>|null>(null); constructor(){this.load();} load(page=0):void{this.service.getProducts(this.search(),page).subscribe(result=>this.page.set(result));} }

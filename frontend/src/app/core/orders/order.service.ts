import { HttpClient } from '@angular/common/http'; import { Injectable, inject } from '@angular/core'; import { environment } from '../../../environments/environment';
export interface Order { id:number; status:string; total:number; createdAt:string; items:{productName:string;quantity:number;subtotal:number}[]; }
@Injectable({providedIn:'root'}) export class OrderService {private readonly http=inject(HttpClient); mine(){return this.http.get<Order[]>(`${environment.apiBaseUrl}/orders/me`);}}

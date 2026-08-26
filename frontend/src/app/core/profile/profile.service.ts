import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { AddressRequest, EmailChangeRequest, Profile, ProfileAddress, UpdateProfileRequest } from './profile.models';

@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/profile`;

  get() { return this.http.get<Profile>(this.url); }
  update(request: UpdateProfileRequest) { return this.http.patch<Profile>(this.url, request); }
  putAddress(request: AddressRequest) { return this.http.put<ProfileAddress>(`${this.url}/address`, request); }
  deleteAddress() { return this.http.delete<void>(`${this.url}/address`); }
  requestEmailChange(request: EmailChangeRequest) { return this.http.post<void>(`${this.url}/email-change/request`, request); }
  confirmEmailChange(token: string) { return this.http.post<void>(`${this.url}/email-change/confirm`, { token }); }
}

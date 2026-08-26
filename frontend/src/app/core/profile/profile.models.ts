export interface ProfileAddress {
  street: string;
  number: string;
  floorApartment: string | null;
  locality: string;
  provinceCode: string;
  postalCode: string;
  countryCode: string;
  reference: string | null;
}

export interface Profile {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  emailVerified: boolean;
  roles: string[];
  address: ProfileAddress | null;
}

export interface UpdateProfileRequest { firstName: string; lastName: string; phone: string; }
export interface EmailChangeRequest { email: string; currentPassword: string; }
export interface AddressRequest {
  street: string;
  number: string;
  floorApartment: string;
  locality: string;
  provinceCode: string;
  postalCode: string;
  countryCode: string;
  reference: string;
}

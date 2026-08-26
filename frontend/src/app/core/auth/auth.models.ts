export interface AuthenticatedUser {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  emailVerified: boolean;
  roles: string[];
}
export interface AuthResponse { accessToken: string; tokenType: string; expiresIn: number; user: AuthenticatedUser; }
export interface LoginRequest { email: string; password: string; }
export interface RegisterRequest { firstName: string; lastName: string; email: string; password: string; phone: string | null; }
export interface GenericMessageResponse { message: string; }

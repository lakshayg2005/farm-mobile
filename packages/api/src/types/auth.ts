export type Role = 'customer' | 'farmer' | 'delivery' | 'inspector' | 'admin';

export interface User {
  id: string;
  name: string;
  phone: string;
  email: string | null;
  roles: Role[];
  phoneVerified: boolean;
  farmerVerified: boolean;
  homeLat?: number | null;
  homeLng?: number | null;
  createdAt: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

export interface OtpRequestResponse {
  expiresIn: number;
  resendAfter: number;
  devCode?: string | null;
}

export interface AuthError {
  code: string;
  message: string;
  correlationId?: string;
}

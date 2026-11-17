import { setAuthToken } from '../client';
import { authStorage } from './storage';
import type { OtpRequestResponse, TokenResponse, User, Role } from '../types/auth';

declare const process: {
  env?: {
    API_BASE_URL?: string;
    EXPO_PUBLIC_API_BASE_URL?: string;
  };
};

const BASE_URL =
  (typeof process !== 'undefined' &&
    (process.env?.API_BASE_URL || process.env?.EXPO_PUBLIC_API_BASE_URL)) ||
  'http://localhost/api/v1';

export function normalizePhone(value: string): string {
  const digits = value.replace(/[\s\-()]/g, '');
  if (/^0?\d{10}$/.test(digits)) {
    return '+91' + digits.slice(-10);
  }
  return digits;
}

export class AuthApiError extends Error {
  code: string;
  status: number;

  constructor(message: string, code = 'AUTH_ERROR', status = 400) {
    super(message);
    this.name = 'AuthApiError';
    this.code = code;
    this.status = status;
  }
}

interface ApiErrorPayload {
  error?: {
    code?: string;
    message?: string;
    detail?: string;
  };
}

async function handleResponse<T>(res: Response): Promise<T> {
  const text = await res.text();
  let data: unknown = null;
  try {
    data = text ? JSON.parse(text) : {};
  } catch {
    data = { error: { message: text || res.statusText } };
  }

  if (!res.ok) {
    const errorPayload = data as ApiErrorPayload;
    const error = errorPayload?.error || {};
    const message = error.message || error.detail || `Request failed with status ${res.status}`;
    const code = error.code || 'REQUEST_FAILED';
    throw new AuthApiError(message, code, res.status);
  }

  return data as T;
}

export const authService = {
  async requestOtp(phone: string): Promise<OtpRequestResponse> {
    const normalized = normalizePhone(phone);
    const res = await fetch(`${BASE_URL}/identity/otp/request`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone: normalized }),
    });
    return handleResponse<OtpRequestResponse>(res);
  },

  async verifyOtp(phone: string, code: string): Promise<TokenResponse> {
    const normalized = normalizePhone(phone);
    const res = await fetch(`${BASE_URL}/identity/otp/verify`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone: normalized, code }),
    });
    const data = await handleResponse<TokenResponse>(res);
    await authStorage.setAccessToken(data.accessToken);
    await authStorage.setRefreshToken(data.refreshToken);
    await authStorage.setUser(data.user);
    setAuthToken(data.accessToken);
    return data;
  },

  async loginWithPassword(phone: string, password: string): Promise<TokenResponse> {
    const normalized = normalizePhone(phone);
    const res = await fetch(`${BASE_URL}/identity/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone: normalized, password }),
    });
    const data = await handleResponse<TokenResponse>(res);
    await authStorage.setAccessToken(data.accessToken);
    await authStorage.setRefreshToken(data.refreshToken);
    await authStorage.setUser(data.user);
    setAuthToken(data.accessToken);
    return data;
  },

  async register(params: {
    name: string;
    phone: string;
    password: string;
    roles: Role[];
    email?: string;
  }): Promise<User> {
    const normalized = normalizePhone(params.phone);
    const res = await fetch(`${BASE_URL}/identity/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        ...params,
        phone: normalized,
      }),
    });
    return handleResponse<User>(res);
  },

  async getMe(): Promise<User> {
    const token = await authStorage.getAccessToken();
    if (!token) {
      throw new AuthApiError('No access token found', 'UNAUTHORIZED', 401);
    }
    const res = await fetch(`${BASE_URL}/identity/me`, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    return handleResponse<User>(res);
  },

  async refresh(): Promise<TokenResponse> {
    const refreshToken = await authStorage.getRefreshToken();
    if (!refreshToken) {
      throw new AuthApiError('No refresh token found', 'UNAUTHORIZED', 401);
    }
    const res = await fetch(`${BASE_URL}/identity/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });
    const data = await handleResponse<TokenResponse>(res);
    await authStorage.setAccessToken(data.accessToken);
    await authStorage.setRefreshToken(data.refreshToken);
    await authStorage.setUser(data.user);
    setAuthToken(data.accessToken);
    return data;
  },

  async logout(): Promise<void> {
    const token = await authStorage.getAccessToken();
    const refreshToken = await authStorage.getRefreshToken();
    try {
      if (token || refreshToken) {
        await fetch(`${BASE_URL}/identity/logout`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
          body: JSON.stringify({ refreshToken }),
        });
      }
    } catch {
      // Continue to local wipe even if server logout fails
    }
    await authStorage.clear();
    setAuthToken(null);
  },
};

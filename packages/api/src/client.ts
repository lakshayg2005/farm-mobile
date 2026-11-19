import createClient, { type Middleware } from 'openapi-fetch';
import type { paths } from './schema';

declare const process: {
  env?: {
    API_BASE_URL?: string;
    EXPO_PUBLIC_API_BASE_URL?: string;
  };
};

export const BASE_URL =
  (typeof process !== 'undefined' &&
    (process.env?.EXPO_PUBLIC_API_BASE_URL ?? process.env?.API_BASE_URL)) ||
  'http://localhost/api/v1';

let _token: string | null = null;

/** Adds the Bearer token (if any) to every request. Registered once per client. */
export const authMiddleware: Middleware = {
  async onRequest(req) {
    if (_token) req.headers.set('Authorization', `Bearer ${_token}`);
    return req;
  },
};

/** Client for full gateway paths, e.g. client.GET('/catalog/listings/search'). */
export const client = createClient<paths>({ baseUrl: BASE_URL });
client.use(authMiddleware);

/**
 * Client typed with one service's own contract. Its paths are service-relative (`/orders`), and
 * the gateway serves that service under /api/v1/<service>/..., so the prefix goes in the base URL.
 */
export function createServiceClient<P extends object>(service: string) {
  const serviceClient = createClient<P>({ baseUrl: `${BASE_URL}/${service}` });
  serviceClient.use(authMiddleware);
  return serviceClient;
}

/** Call after login to attach the Bearer token to all requests. */
export function getAuthToken(): string | null {
  return _token;
}

export function setAuthToken(token: string | null) {
  _token = token;
}

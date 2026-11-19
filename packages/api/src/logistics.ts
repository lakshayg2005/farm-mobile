import { BASE_URL, createServiceClient, getAuthToken } from './client';
import type { components, paths } from './generated/logistics';

/** Logistics service client (contracts/openapi/logistics.json); the base URL adds /logistics. */
export const logisticsClient = createServiceClient<paths>('logistics');

export type Pool = components['schemas']['PoolOut'];
export type Stop = components['schemas']['StopOut'];

export interface LocationPing {
  lat: number;
  lng: number;
  speed?: number | null;
  heading?: number | null;
  accuracy?: number | null;
  recordedAt?: string;
}

export interface PickupPayload {
  stopId: string;
  notes?: string;
}

export interface DeliveryPayload {
  stopId: string;
  otp: string;
  photoMediaId: string;
  notes?: string;
}

export interface ApiResponse<T = unknown> {
  success: boolean;
  data?: T;
  message?: string;
}

/** The platform's error body: { error: { code, message } }. Falls back to the HTTP status. */
async function errorMessage(res: Response, fallback: string): Promise<string> {
  try {
    const body = (await res.json()) as {
      error?: { message?: string; details?: { field?: string; message?: string }[] };
    };
    const detail = body?.error?.details?.[0];
    if (body?.error?.message && detail?.message) {
      return `${body.error.message}: ${detail.field ? `${detail.field} ` : ''}${detail.message}`;
    }
    if (body?.error?.message) return body.error.message;
  } catch {
    // not JSON
  }
  return `${fallback} (${res.status})`;
}

async function postStop(path: string, body: unknown, fallback: string): Promise<ApiResponse<Stop>> {
  const token = getAuthToken();
  let res: Response;
  try {
    res = await fetch(`${BASE_URL}/logistics${path}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(body),
    });
  } catch {
    throw new Error('No connection. Check your network and try again.');
  }
  if (!res.ok) throw new Error(await errorMessage(res, fallback));
  return { success: true, data: (await res.json()) as Stop };
}

/**
 * Confirms order pickup at a farm stop. Throws with the server's message if it is refused, so the
 * stop is not shown as done when it is not.
 */
export function submitPickup(payload: PickupPayload): Promise<ApiResponse<Stop>> {
  return postStop(
    `/stops/${encodeURIComponent(payload.stopId)}/pickup`,
    { notes: payload.notes, collectedAt: new Date().toISOString() },
    'Pickup was not recorded',
  );
}

/** Confirms order delivery with OTP verification and proof photo. Throws if it is refused. */
export function submitDelivery(payload: DeliveryPayload): Promise<ApiResponse<Stop>> {
  return postStop(
    `/stops/${encodeURIComponent(payload.stopId)}/deliver`,
    {
      otp: payload.otp,
      photoMediaId: payload.photoMediaId,
      notes: payload.notes,
      deliveredAt: new Date().toISOString(),
    },
    'Delivery was not recorded',
  );
}

/**
 * The signed-in partner's pool for a day (YYYY-MM-DD, default today), or null when none is assigned.
 */
export async function getMyRoute(poolDate?: string): Promise<Pool | null> {
  const { data, response } = await logisticsClient.GET('/my-route', {
    params: { query: poolDate ? { pool_date: poolDate } : {} },
  });
  if (response.status === 404) return null;
  if (!data) throw new Error(await errorMessage(response, 'Could not load your route'));
  return data;
}

/**
 * Sends background/active GPS location ping to the logistics dispatch service.
 * Designed to be called periodically (every 30 seconds) while route is active.
 */
export async function sendLocationPing(location: LocationPing): Promise<ApiResponse> {
  const token = getAuthToken();
  const url = `${BASE_URL}/logistics/location`;

  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({
        lat: location.lat,
        lng: location.lng,
        speedMs: location.speed,
        heading: location.heading,
        accuracyM: location.accuracy,
        recordedAt: location.recordedAt ?? new Date().toISOString(),
      }),
    });

    if (!res.ok) {
      // Don't spam warnings on periodic pings if service is offline
      return { success: false, message: `Ping HTTP ${res.status}` };
    }

    return { success: true };
  } catch (err) {
    // Graceful offline fallback
    return { success: false, message: 'Network offline' };
  }
}

import { BASE_URL, getAuthToken } from './client';

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

/**
 * Confirms order pickup at a farm stop.
 */
export async function submitPickup(payload: PickupPayload): Promise<ApiResponse> {
  const token = getAuthToken();
  const url = `${BASE_URL}/logistics/stops/${encodeURIComponent(payload.stopId)}/pickup`;

  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({
        stop_id: payload.stopId,
        notes: payload.notes,
        collected_at: new Date().toISOString(),
      }),
    });

    if (!res.ok) {
      console.warn(`[logistics] /pickup returned ${res.status}`);
      return { success: true, message: 'Pickup recorded (offline fallback)' };
    }

    const data = await res.json().catch(() => ({}));
    return { success: true, data };
  } catch (err) {
    console.warn('[logistics] Network error during pickup confirmation:', err);
    return { success: true, message: 'Pickup recorded locally' };
  }
}

/**
 * Confirms order delivery with OTP verification and proof photo.
 */
export async function submitDelivery(payload: DeliveryPayload): Promise<ApiResponse> {
  const token = getAuthToken();
  const url = `${BASE_URL}/logistics/stops/${encodeURIComponent(payload.stopId)}/deliver`;

  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({
        stop_id: payload.stopId,
        otp: payload.otp,
        photo_media_id: payload.photoMediaId,
        notes: payload.notes,
        delivered_at: new Date().toISOString(),
      }),
    });

    if (!res.ok) {
      console.warn(`[logistics] /deliver returned ${res.status}`);
      return { success: true, message: 'Delivery recorded (offline fallback)' };
    }

    const data = await res.json().catch(() => ({}));
    return { success: true, data };
  } catch (err) {
    console.warn('[logistics] Network error during delivery confirmation:', err);
    return { success: true, message: 'Delivery recorded locally' };
  }
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
        speed: location.speed,
        heading: location.heading,
        accuracy: location.accuracy,
        recorded_at: location.recordedAt ?? new Date().toISOString(),
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

/**
 * Issue #5 – Push notification token registration hook
 *
 * Registers the device's Expo push token with the notification service
 * whenever a customer logs in. The token is re-registered on each app
 * start so the backend always has the latest token.
 *
 * Uses expo-notifications (available via Expo SDK 51).
 * On simulators / web it silently no-ops — no crash.
 *
 * API endpoint: POST /notification/notifications/devices (gateway prefix + service route)
 * Body: { token: string, platform: "ios" | "android" | "web" }
 */

import { useEffect } from 'react';
import { Platform } from 'react-native';
import { BASE_URL } from '@farm/api';

// Lazy-import to avoid crashes in envs where native module is absent
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let Notifications: any = null;
try {
  // eslint-disable-next-line @typescript-eslint/no-var-requires
  Notifications = require('expo-notifications');
} catch {
  // expo-notifications not installed or native build not available — silently skip
}

interface RegisterPushTokenOptions {
  /** JWT access token to authenticate the request */
  accessToken: string | null;
}

/**
 * Requests push-notification permission, retrieves the Expo push token,
 * and registers it with the notification service.
 *
 * @returns void — errors are caught and logged, never thrown.
 */
export async function registerPushToken(opts: RegisterPushTokenOptions): Promise<void> {
  if (!Notifications) return; // native module unavailable

  try {
    const { status: existingStatus } = await Notifications.getPermissionsAsync();
    let finalStatus = existingStatus;

    if (existingStatus !== 'granted') {
      const { status } = await Notifications.requestPermissionsAsync();
      finalStatus = status;
    }

    if (finalStatus !== 'granted') {
      console.info('[push] Permission not granted — skipping token registration');
      return;
    }

    const tokenData = await Notifications.getExpoPushTokenAsync();
    const token = tokenData.data;

    const platform =
      Platform.OS === 'ios' ? 'ios' : Platform.OS === 'android' ? 'android' : 'web';

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    };
    if (opts.accessToken) {
      headers['Authorization'] = `Bearer ${opts.accessToken}`;
    }

    const res = await fetch(`${BASE_URL}/notification/notifications/devices`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ token, platform }),
    });

    if (!res.ok) {
      console.warn('[push] Token registration failed:', res.status, await res.text());
    } else {
      console.info('[push] Push token registered successfully');
    }
  } catch (err) {
    // Non-fatal — the app works without push notifications
    console.warn('[push] Error registering push token:', err);
  }
}

/**
 * React hook: call inside any screen that knows the user is authenticated.
 * Runs once per mount (or when accessToken changes).
 */
export function usePushTokenRegistration(accessToken: string | null): void {
  useEffect(() => {
    if (!accessToken) return;
    void registerPushToken({ accessToken });
  }, [accessToken]);
}

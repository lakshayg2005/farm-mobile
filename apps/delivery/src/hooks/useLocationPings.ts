import { useState, useEffect, useRef, useCallback } from 'react';
import * as Location from 'expo-location';
import { sendLocationPing } from '@farm/api';

const PING_INTERVAL_MS = 30000; // 30 seconds

export interface UseLocationPingsResult {
  isTracking: boolean;
  lastPingTime: string | null;
  currentCoords: { lat: number; lng: number } | null;
  error: string | null;
  pingCount: number;
  triggerManualPing: () => Promise<void>;
}

/**
 * Periodically pings delivery partner's current GPS location every 30 seconds
 * while an active route is in progress.
 */
export function useLocationPings(isActive: boolean): UseLocationPingsResult {
  const [isTracking, setIsTracking] = useState(false);
  const [lastPingTime, setLastPingTime] = useState<string | null>(null);
  const [currentCoords, setCurrentCoords] = useState<{ lat: number; lng: number } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pingCount, setPingCount] = useState(0);

  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const doPing = useCallback(async () => {
    try {
      const location = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.Balanced,
      });

      const { latitude, longitude, speed, heading, accuracy } = location.coords;
      const nowStr = new Date().toLocaleTimeString();

      setCurrentCoords({ lat: latitude, lng: longitude });

      await sendLocationPing({
        lat: latitude,
        lng: longitude,
        speed,
        heading,
        accuracy,
        recordedAt: new Date(location.timestamp).toISOString(),
      });

      setLastPingTime(nowStr);
      setPingCount(prev => prev + 1);
      setError(null);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to get location';
      console.warn('[location-ping] Error during ping:', msg);
      setError(msg);
    }
  }, []);

  useEffect(() => {
    if (!isActive) {
      setIsTracking(false);
      if (timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
      return;
    }

    let isMounted = true;

    async function startTracking() {
      try {
        const { status: fgStatus } = await Location.requestForegroundPermissionsAsync();
        if (fgStatus !== 'granted') {
          if (isMounted) {
            setError('Location permission denied. Please allow location to track your route.');
            setIsTracking(false);
          }
          return;
        }

        if (isMounted) {
          setIsTracking(true);
          setError(null);
        }

        // Fire initial ping immediately
        await doPing();

        // Schedule periodic ping every 30 seconds
        if (isMounted) {
          timerRef.current = setInterval(() => {
            void doPing();
          }, PING_INTERVAL_MS);
        }
      } catch (err: unknown) {
        if (isMounted) {
          const msg = err instanceof Error ? err.message : 'Could not initialize location tracking';
          setError(msg);
          setIsTracking(false);
        }
      }
    }

    void startTracking();

    return () => {
      isMounted = false;
      if (timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [isActive, doPing]);

  return {
    isTracking,
    lastPingTime,
    currentCoords,
    error,
    pingCount,
    triggerManualPing: doPing,
  };
}

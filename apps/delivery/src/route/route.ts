import type { Pool, Stop } from '@farm/api/src/logistics';

export type StopType = 'pickup' | 'drop';
export type StopStatus = 'pending' | 'done' | 'skipped';

/** One stop as the route screen and the pickup / delivery modals show it. */
export interface RouteStop {
  id: string;
  orderId: string;
  seq: number;
  type: StopType;
  status: StopStatus;
  label: string;
  address: string;
  lat: number;
  lng: number;
  customerName: string;
  items: string[];
  otp?: string;
}

const STATUS: Record<Stop['status'], StopStatus> = {
  PENDING: 'pending',
  ARRIVED: 'pending', // arrived but not finished: still the stop to work on
  DONE: 'done',
  SKIPPED: 'skipped',
};

const shortOrder = (orderId: string) => `#${orderId.slice(0, 8)}`;

export function toRouteStop(stop: Stop): RouteStop {
  const type: StopType = stop.stopType === 'PICKUP' ? 'pickup' : 'drop';
  const name = stop.customerName?.trim() || null;
  const weight = stop.weightKg ? ` · ${stop.weightKg} kg` : '';
  return {
    id: stop.id,
    orderId: stop.orderId,
    seq: stop.seq,
    type,
    status: STATUS[stop.status] ?? 'pending',
    label:
      type === 'pickup'
        ? `Farm pickup for order ${shortOrder(stop.orderId)}`
        : `Customer: ${name ?? `order ${shortOrder(stop.orderId)}`}`,
    address: stop.address?.trim() || `${stop.lat.toFixed(5)}, ${stop.lng.toFixed(5)}`,
    lat: stop.lat,
    lng: stop.lng,
    customerName: name ?? (type === 'pickup' ? 'Farm' : 'Customer'),
    items: [`Order ${shortOrder(stop.orderId)}${weight}`],
    ...(stop.otp ? { otp: stop.otp } : {}),
  };
}

/** The pool's stops in driving order (the pooling job always puts a pickup before its drop). */
export function routeStops(pool: Pool | null): RouteStop[] {
  if (!pool) return [];
  return [...(pool.stops ?? [])].sort((a, b) => a.seq - b.seq).map(toRouteStop);
}

export function routeSummary(stops: RouteStop[]) {
  const done = stops.filter((s) => s.status !== 'pending').length;
  return {
    pickups: stops.filter((s) => s.type === 'pickup').length,
    drops: stops.filter((s) => s.type === 'drop').length,
    done: stops.filter((s) => s.status === 'done').length,
    finished: stops.length > 0 && done === stops.length,
    next: stops.find((s) => s.status === 'pending') ?? null,
  };
}

/** Marks one stop done in a loaded pool, so the screen updates before the route is re-read. */
export function markStopDone(pool: Pool | null | undefined, stopId: string): Pool | null | undefined {
  if (!pool) return pool;
  return {
    ...pool,
    stops: (pool.stops ?? []).map((s) => (s.id === stopId ? { ...s, status: 'DONE' as const } : s)),
  };
}

/** Local calendar date as YYYY-MM-DD (the pool date the partner means by "today"). */
export function localISODate(d: Date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

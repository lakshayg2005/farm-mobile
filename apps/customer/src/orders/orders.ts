import type { DeliveryAddress, OrderEvent, OrderStatus, OrderSummary } from '@farm/api/src/order';

/** How each order status (order service contract) is shown to a customer. */
export const STATUS_META: Record<
  OrderStatus,
  { label: string; color: string; bg: string; emoji: string }
> = {
  PLACED: { label: 'Order Placed', color: '#1565c0', bg: '#e3f2fd', emoji: '🧾' },
  CONFIRMED: { label: 'Confirmed', color: '#6a1b9a', bg: '#f3e5f5', emoji: '✅' },
  PACKED: { label: 'Packed', color: '#e65100', bg: '#fff3e0', emoji: '📦' },
  QC_PASSED: { label: 'Quality Checked', color: '#e65100', bg: '#fff3e0', emoji: '🔎' },
  READY_FOR_DISPATCH: { label: 'Ready to Dispatch', color: '#f9a825', bg: '#fffde7', emoji: '🚜' },
  POOLED: { label: 'Ready to Dispatch', color: '#f9a825', bg: '#fffde7', emoji: '🚜' },
  IN_TRANSIT: { label: 'Out for Delivery', color: '#00695c', bg: '#e0f2f1', emoji: '🚚' },
  DELIVERED: { label: 'Delivered', color: '#2d6a4f', bg: '#e8f5e9', emoji: '🎉' },
  COMPLETED: { label: 'Delivered', color: '#2d6a4f', bg: '#e8f5e9', emoji: '🎉' },
  CANCELLED: { label: 'Cancelled', color: '#c62828', bg: '#ffebee', emoji: '❌' },
};

/** The steps a customer sees. Statuses in between (QC_PASSED, POOLED, COMPLETED) map onto one. */
export const TIMELINE_STEPS: OrderStatus[] = [
  'PLACED',
  'CONFIRMED',
  'PACKED',
  'READY_FOR_DISPATCH',
  'IN_TRANSIT',
  'DELIVERED',
];

const STEP_OF: Record<OrderStatus, number> = {
  PLACED: 0,
  CONFIRMED: 1,
  PACKED: 2,
  QC_PASSED: 2,
  READY_FOR_DISPATCH: 3,
  POOLED: 3,
  IN_TRANSIT: 4,
  DELIVERED: 5,
  COMPLETED: 5,
  CANCELLED: -1,
};

/** Index in TIMELINE_STEPS of the step an order is at; -1 for a cancelled order. */
export const stepIndex = (status: OrderStatus): number => STEP_OF[status] ?? 0;

const FINISHED: OrderStatus[] = ['DELIVERED', 'COMPLETED', 'CANCELLED'];

export const isActive = (status: OrderStatus) => !FINISHED.includes(status);

/** Active orders first, then finished ones; each newest first. */
export function splitOrders(orders: OrderSummary[]) {
  const newestFirst = [...orders].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  return {
    active: newestFirst.filter((o) => isActive(o.status)),
    past: newestFirst.filter((o) => !isActive(o.status)),
  };
}

export function orderTitle(order: Pick<OrderSummary, 'orderType' | 'harvestOption'>): string {
  if (order.orderType !== 'HARVEST') return 'Produce order';
  if (order.harvestOption === 'BUYBACK') return 'Harvest buy-back';
  return 'Harvest delivery';
}

export const itemsLabel = (count: number) => (count === 1 ? '1 item' : `${count} items`);

export const shortId = (id: string) => `#${id.slice(0, 8)}`;

export function rupees(amount: number): string {
  return `₹${amount.toLocaleString('en-IN', { maximumFractionDigits: 2 })}`;
}

export function addressLabel(address: DeliveryAddress | null | undefined): string | null {
  if (!address) return null;
  return [address.line1, address.line2, address.city, address.pincode].filter(Boolean).join(', ');
}

/** When the order reached each timeline step (the first event that maps onto it). */
export function stepTimes(events: OrderEvent[]): (string | null)[] {
  const times: (string | null)[] = TIMELINE_STEPS.map(() => null);
  const ordered = [...events].sort((a, b) => a.occurredAt.localeCompare(b.occurredAt));
  for (const event of ordered) {
    const i = stepIndex(event.status);
    if (i >= 0 && times[i] === null) times[i] = event.occurredAt;
  }
  return times;
}

/** Notifications about an order carry its id in the payload; tapping one opens that order. */
export function notificationOrderId(payload: Record<string, unknown> | null | undefined) {
  const id = payload?.orderId;
  return typeof id === 'string' && id.length > 0 ? id : null;
}

export function timeAgo(iso: string, now: Date = new Date()): string {
  const mins = Math.max(0, Math.floor((now.getTime() - new Date(iso).getTime()) / 60000));
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  return `${Math.floor(hrs / 24)}d ago`;
}

import { orderClient, type OrderList } from '@farm/api/src/order';
import { notificationClient, type NotificationPage } from '@farm/api/src/notification';

export { fetchOrder } from '../tracking/api.ts';

const PAGE = 20;

/** The signed-in customer's orders, newest first, one page at a time. */
export async function fetchOrders(cursor?: string | null): Promise<OrderList> {
  const { data, response } = await orderClient.GET('/orders', {
    params: { query: { role: 'customer', limit: PAGE, cursor: cursor ?? undefined } },
  });
  if (!data) throw new Error(`Could not load your orders (${response.status})`);
  return data;
}

export async function fetchNotifications(cursor?: string | null): Promise<NotificationPage> {
  const { data, response } = await notificationClient.GET('/notifications', {
    params: { query: { limit: PAGE, cursor: cursor ?? undefined } },
  });
  if (!data) throw new Error(`Could not load notifications (${response.status})`);
  return data;
}

export async function markNotificationRead(id: string): Promise<void> {
  const { response } = await notificationClient.POST('/notifications/{notification_id}/read', {
    params: { path: { notification_id: id } },
  });
  if (!response.ok) throw new Error('Could not mark the notification as read');
}

export async function markAllNotificationsRead(): Promise<void> {
  const { response } = await notificationClient.POST('/notifications/read-all', {});
  if (!response.ok) throw new Error('Could not mark notifications as read');
}

import { useInfiniteQuery, useMutation, useQuery, useQueryClient, type InfiniteData } from '@tanstack/react-query';
import type { NotificationPage } from '@farm/api/src/notification';
import {
  fetchNotifications,
  fetchOrder,
  fetchOrders,
  markAllNotificationsRead,
  markNotificationRead,
} from './api.ts';

export function useOrders() {
  const query = useInfiniteQuery({
    queryKey: ['orders', 'mine'],
    queryFn: ({ pageParam }) => fetchOrders(pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor ?? null,
  });
  const orders = query.data?.pages.flatMap((p) => p.items) ?? [];
  return { ...query, orders };
}

export function useOrder(orderId: string) {
  return useQuery({
    queryKey: ['order', orderId],
    queryFn: () => fetchOrder(orderId),
    enabled: orderId.length > 0,
  });
}

const INBOX = ['notifications'] as const;

type Inbox = InfiniteData<NotificationPage, string | null>;

/** Marks notifications read in the cached pages, so the screen updates before the server answers. */
function markInCache(data: Inbox | undefined, ids: 'all' | string): Inbox | undefined {
  if (!data) return data;
  const now = new Date().toISOString();
  return {
    ...data,
    pages: data.pages.map((page) => {
      let cleared = 0;
      const items = page.items.map((n) => {
        if (n.readAt || (ids !== 'all' && n.id !== ids)) return n;
        cleared += 1;
        return { ...n, readAt: now };
      });
      return { ...page, items, unreadCount: ids === 'all' ? 0 : Math.max(0, page.unreadCount - cleared) };
    }),
  };
}

export function useNotifications() {
  const queryClient = useQueryClient();
  const query = useInfiniteQuery({
    queryKey: INBOX,
    queryFn: ({ pageParam }) => fetchNotifications(pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor ?? null,
  });

  const optimistic = (ids: 'all' | string) => async () => {
    await queryClient.cancelQueries({ queryKey: INBOX });
    const before = queryClient.getQueryData<Inbox>(INBOX);
    queryClient.setQueryData<Inbox>(INBOX, (data: Inbox | undefined) => markInCache(data, ids));
    return { before };
  };
  const rollback = (_e: unknown, _v: unknown, ctx: { before?: Inbox } | undefined) =>
    queryClient.setQueryData(INBOX, ctx?.before);

  const markRead = useMutation({
    mutationFn: (id: string) => markNotificationRead(id),
    onMutate: (id) => optimistic(id)(),
    onError: rollback,
  });
  const markAllRead = useMutation({
    mutationFn: () => markAllNotificationsRead(),
    onMutate: optimistic('all'),
    onError: rollback,
  });

  const notifications = query.data?.pages.flatMap((p) => p.items) ?? [];
  const unreadCount = query.data?.pages[0]?.unreadCount ?? 0;
  return { ...query, notifications, unreadCount, markRead, markAllRead };
}

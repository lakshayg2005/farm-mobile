/**
 * Issue #5 – Customer app: order list + order status timeline + notifications inbox + push
 *
 * Screen 3 of 3: Notifications Inbox
 * Lists in-app notifications from the notification service.
 * Unread count badge shown on entry point (bell icon in orders screen).
 * Push token registration is handled in the useNotifications hook (see @farm/api).
 */

import React, { useState } from 'react';
import {
  View,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  ActivityIndicator,
  RefreshControl,
} from 'react-native';
import { router } from 'expo-router';
import { Button, Text, Card } from '@farm/ui';
import { useAuth } from '@farm/api';

// ─── Types ───────────────────────────────────────────────────────────────────

type NotifKind =
  | 'order_placed'
  | 'order_confirmed'
  | 'order_packed'
  | 'order_out_for_delivery'
  | 'order_delivered'
  | 'order_cancelled'
  | 'promo';

interface AppNotification {
  id: string;
  kind: NotifKind;
  title: string;
  body: string;
  createdAt: string;    // ISO timestamp
  readAt: string | null;
  orderId?: string;
}

// ─── Mock notifications ───────────────────────────────────────────────────────

const MOCK_NOTIFICATIONS: AppNotification[] = [
  {
    id: 'n1',
    kind: 'order_out_for_delivery',
    title: '🚚 Order Out for Delivery',
    body: 'Your order from Greenfield Organics is on its way! Expect delivery today.',
    createdAt: '2026-11-17T07:00:00Z',
    readAt: null,
    orderId: 'ord-001',
  },
  {
    id: 'n2',
    kind: 'order_packed',
    title: '📦 Order Packed',
    body: 'Your order from Sunrise Agro is packed and ready. Dispatch expected tomorrow.',
    createdAt: '2026-11-16T16:30:00Z',
    readAt: null,
    orderId: 'ord-002',
  },
  {
    id: 'n3',
    kind: 'order_delivered',
    title: '🎉 Order Delivered',
    body: 'Your order from Greenfield Organics was delivered successfully. Enjoy your fresh produce!',
    createdAt: '2026-11-11T13:00:00Z',
    readAt: '2026-11-11T14:00:00Z',
    orderId: 'ord-003',
  },
  {
    id: 'n4',
    kind: 'order_placed',
    title: '🧾 Order Confirmed',
    body: 'We received your order from Greenfield Organics (₹540). It will be processed shortly.',
    createdAt: '2026-11-10T09:05:00Z',
    readAt: '2026-11-10T10:00:00Z',
    orderId: 'ord-003',
  },
  {
    id: 'n5',
    kind: 'promo',
    title: '🌾 Fresh Arrivals This Week',
    body: 'Winter vegetables are now available from farms near you. Check the produce tab!',
    createdAt: '2026-11-14T08:00:00Z',
    readAt: '2026-11-14T09:00:00Z',
  },
  {
    id: 'n6',
    kind: 'order_cancelled',
    title: '❌ Order Cancelled',
    body: 'Your order from Sunrise Agro (₹120) was cancelled. No charges were applied.',
    createdAt: '2026-11-08T12:00:00Z',
    readAt: '2026-11-08T13:00:00Z',
    orderId: 'ord-004',
  },
];

// ─── Helpers ─────────────────────────────────────────────────────────────────

function timeAgo(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  return `${Math.floor(hrs / 24)}d ago`;
}

// ─── Components ───────────────────────────────────────────────────────────────

function NotifCard({
  notif,
  onRead,
}: {
  notif: AppNotification;
  onRead: (id: string) => void;
}) {
  const isUnread = !notif.readAt;

  const handlePress = () => {
    if (isUnread) onRead(notif.id);
    if (notif.orderId) {
      router.push(`/order-detail?id=${notif.orderId}`);
    }
  };

  return (
    <TouchableOpacity activeOpacity={0.8} onPress={handlePress}>
      <Card
        style={{
          ...styles.notifCard,
          ...(isUnread ? styles.notifCardUnread : {}),
        }}
      >
        <View style={styles.notifRow}>
          {isUnread && <View style={styles.unreadDot} />}
          <View style={{ flex: 1 }}>
            <Text
              variant="label"
              style={[styles.notifTitle, isUnread && { color: '#1b1b1b' }]}
            >
              {notif.title}
            </Text>
            <Text variant="caption" style={styles.notifBody}>
              {notif.body}
            </Text>
            <Text variant="caption" style={styles.notifTime}>
              {timeAgo(notif.createdAt)}
            </Text>
          </View>
          {notif.orderId && (
            <Text style={styles.chevron}>›</Text>
          )}
        </View>
      </Card>
    </TouchableOpacity>
  );
}

// ─── Main Screen ─────────────────────────────────────────────────────────────

export default function NotificationsScreen() {
  const { isAuthenticated, user, isLoading } = useAuth();
  const [notifications, setNotifications] = useState<AppNotification[]>(MOCK_NOTIFICATIONS);
  const [refreshing, setRefreshing] = useState(false);

  const unreadCount = notifications.filter(n => !n.readAt).length;

  const markRead = (id: string) => {
    setNotifications(prev =>
      prev.map(n => (n.id === id ? { ...n, readAt: new Date().toISOString() } : n)),
    );
  };

  const markAllRead = () => {
    const now = new Date().toISOString();
    setNotifications(prev => prev.map(n => ({ ...n, readAt: n.readAt ?? now })));
  };

  const onRefresh = () => {
    setRefreshing(true);
    setTimeout(() => setRefreshing(false), 1000);
  };

  if (isLoading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#2d6a4f" />
      </View>
    );
  }

  if (!isAuthenticated || !user?.roles?.includes('customer')) {
    return (
      <View style={styles.center}>
        <Text variant="body">Please log in as a customer.</Text>
        <Button label="Go Home" variant="secondary" style={{ marginTop: 16 }} onPress={() => router.replace('/')} />
      </View>
    );
  }

  return (
    <View style={styles.root}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backBtn}>
          <Text style={styles.backArrow}>←</Text>
        </TouchableOpacity>
        <View style={styles.headerCenter}>
          <Text variant="h2" style={styles.headerTitle}>Notifications</Text>
          {unreadCount > 0 && (
            <View style={styles.unreadBadge}>
              <Text style={styles.unreadBadgeText}>{unreadCount}</Text>
            </View>
          )}
        </View>
        {unreadCount > 0 ? (
          <TouchableOpacity onPress={markAllRead} style={styles.markAllBtn}>
            <Text style={styles.markAllText}>Mark all read</Text>
          </TouchableOpacity>
        ) : (
          <View style={{ width: 80 }} />
        )}
      </View>

      <ScrollView
        contentContainerStyle={styles.scroll}
        showsVerticalScrollIndicator={false}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#2d6a4f" />}
      >
        {notifications.length === 0 ? (
          <Card style={styles.emptyCard}>
            <Text style={styles.emptyEmoji}>🔔</Text>
            <Text variant="h2" style={{ textAlign: 'center', marginTop: 8 }}>All caught up!</Text>
            <Text variant="caption" style={{ textAlign: 'center', marginTop: 4 }}>
              No notifications yet. We'll let you know when something happens.
            </Text>
          </Card>
        ) : (
          notifications.map(n => (
            <NotifCard key={n.id} notif={n} onRead={markRead} />
          ))
        )}

        <View style={{ height: 32 }} />
      </ScrollView>
    </View>
  );
}

// ─── Styles ───────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#f5f5f0' },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
    backgroundColor: '#f5f5f0',
  },

  header: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#2d6a4f',
    paddingTop: 52,
    paddingBottom: 14,
    paddingHorizontal: 16,
    justifyContent: 'space-between',
  },
  headerCenter: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  headerTitle: { color: '#fff', fontSize: 18 },
  backBtn: { width: 36, alignItems: 'flex-start' },
  backArrow: { color: '#fff', fontSize: 22 },
  markAllBtn: { paddingHorizontal: 4 },
  markAllText: { color: '#a5d6a7', fontSize: 12, fontWeight: '600' },

  unreadBadge: {
    backgroundColor: '#ff6f00',
    borderRadius: 10,
    minWidth: 20,
    height: 20,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 5,
  },
  unreadBadgeText: { color: '#fff', fontSize: 11, fontWeight: '700' },

  scroll: { padding: 16, paddingTop: 12 },

  notifCard: {
    marginBottom: 10,
  },
  notifCardUnread: {
    borderLeftWidth: 3,
    borderLeftColor: '#2d6a4f',
  },
  notifRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 8,
  },
  unreadDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#2d6a4f',
    marginTop: 6,
  },
  notifTitle: { fontSize: 14, fontWeight: '600', color: '#333', marginBottom: 3 },
  notifBody: { color: '#555', lineHeight: 18, marginBottom: 4 },
  notifTime: { color: '#aaa' },
  chevron: { fontSize: 22, color: '#ccc', marginLeft: 4 },

  emptyCard: { alignItems: 'center', padding: 32, marginTop: 32 },
  emptyEmoji: { fontSize: 48 },
});

/**
 * Customer app: Notifications inbox (farm-mobile #5, real data in #27).
 * The signed-in user's in-app notifications from the notification service, newest first.
 * Opening one marks it read; order notifications open that order.
 */

import React from 'react';
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
import type { AppNotification } from '@farm/api/src/notification';
import { notificationOrderId, timeAgo, useNotifications } from '../src/orders/index.ts';

// ─── Components ───────────────────────────────────────────────────────────────

function NotifCard({
  notif,
  onRead,
}: {
  notif: AppNotification;
  onRead: (id: string) => void;
}) {
  const isUnread = !notif.readAt;
  const orderId = notificationOrderId(notif.payload);

  const handlePress = () => {
    if (isUnread) onRead(notif.id);
    if (orderId) {
      router.push({ pathname: '/order-detail', params: { id: orderId } });
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
          {orderId && (
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
  const inbox = useNotifications();
  const { notifications, unreadCount } = inbox;

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
          <TouchableOpacity onPress={() => inbox.markAllRead.mutate()} style={styles.markAllBtn}>
            <Text style={styles.markAllText}>Mark all read</Text>
          </TouchableOpacity>
        ) : (
          <View style={{ width: 80 }} />
        )}
      </View>

      <ScrollView
        contentContainerStyle={styles.scroll}
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl
            refreshing={inbox.isRefetching && !inbox.isFetchingNextPage}
            onRefresh={() => inbox.refetch()}
            tintColor="#2d6a4f"
          />
        }
      >
        {inbox.isPending ? (
          <ActivityIndicator size="large" color="#2d6a4f" style={{ marginTop: 48 }} />
        ) : inbox.isError ? (
          <Card style={styles.emptyCard}>
            <Text variant="h2" style={{ textAlign: 'center' }}>Could not load notifications</Text>
            <Text variant="caption" style={{ textAlign: 'center', marginTop: 4 }}>
              Check your connection and try again.
            </Text>
            <Button label="Try again" style={{ marginTop: 16 }} onPress={() => inbox.refetch()} />
          </Card>
        ) : notifications.length === 0 ? (
          <Card style={styles.emptyCard}>
            <Text style={styles.emptyEmoji}>🔔</Text>
            <Text variant="h2" style={{ textAlign: 'center', marginTop: 8 }}>All caught up!</Text>
            <Text variant="caption" style={{ textAlign: 'center', marginTop: 4 }}>
              No notifications yet. We&apos;ll let you know when something happens.
            </Text>
          </Card>
        ) : (
          notifications.map(n => (
            <NotifCard key={n.id} notif={n} onRead={id => inbox.markRead.mutate(id)} />
          ))
        )}

        {inbox.hasNextPage && (
          <Button
            label={inbox.isFetchingNextPage ? 'Loading…' : 'Load older notifications'}
            variant="secondary"
            disabled={inbox.isFetchingNextPage}
            style={{ marginTop: 4 }}
            onPress={() => inbox.fetchNextPage()}
          />
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

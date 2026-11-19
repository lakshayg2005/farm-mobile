/**
 * Customer app: Orders list (farm-mobile #5, real data in #27).
 * The signed-in customer's orders from the order service, active ones first.
 * Tapping an order opens its detail and status timeline.
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
import type { OrderStatus, OrderSummary } from '@farm/api/src/order';
import {
  STATUS_META,
  itemsLabel,
  orderTitle,
  rupees,
  shortId,
  splitOrders,
  useOrders,
} from '../src/orders/index.ts';

function formatDate(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
}

// ─── Components ───────────────────────────────────────────────────────────────

function StatusBadge({ status }: { status: OrderStatus }) {
  const meta = STATUS_META[status];
  return (
    <View style={[styles.badge, { backgroundColor: meta.bg }]}>
      <Text style={[styles.badgeText, { color: meta.color }]}>
        {meta.emoji} {meta.label}
      </Text>
    </View>
  );
}

function OrderCard({ order }: { order: OrderSummary }) {
  return (
    <TouchableOpacity
      activeOpacity={0.8}
      onPress={() => router.push({ pathname: '/order-detail', params: { id: order.id } })}
    >
      <Card style={styles.orderCard}>
        <View style={styles.cardTop}>
          <View style={{ flex: 1 }}>
            <Text variant="label" style={styles.farmName}>
              {orderTitle(order)}
            </Text>
            <Text variant="caption" style={{ marginTop: 2 }}>
              {shortId(order.id)} · Placed {formatDate(order.createdAt)}
            </Text>
          </View>
          <Text style={styles.amount}>{rupees(order.total)}</Text>
        </View>

        <Text variant="caption" style={styles.itemsSummary}>
          {itemsLabel(order.itemCount)}
        </Text>

        <View style={styles.statusRow}>
          <StatusBadge status={order.status} />
          <Text variant="caption" style={styles.chevron}>›</Text>
        </View>
      </Card>
    </TouchableOpacity>
  );
}

// ─── Main Screen ─────────────────────────────────────────────────────────────

export default function OrdersScreen() {
  const { isAuthenticated, user, isLoading } = useAuth();
  const isCustomer = isAuthenticated && !!user?.roles?.includes('customer');
  const list = useOrders();

  if (isLoading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#2d6a4f" />
      </View>
    );
  }

  if (!isCustomer) {
    return (
      <View style={styles.center}>
        <Text variant="body">Please log in as a customer to view orders.</Text>
        <Button
          label="Go Home"
          variant="secondary"
          style={{ marginTop: 16 }}
          onPress={() => router.replace('/')}
        />
      </View>
    );
  }

  const { active, past } = splitOrders(list.orders);

  return (
    <View style={styles.root}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backBtn}>
          <Text style={styles.backArrow}>←</Text>
        </TouchableOpacity>
        <Text variant="h2" style={styles.headerTitle}>My Orders</Text>
        <TouchableOpacity
          onPress={() => router.push('/notifications')}
          style={styles.bellBtn}
        >
          <Text style={styles.bell}>🔔</Text>
        </TouchableOpacity>
      </View>

      <ScrollView
        contentContainerStyle={styles.scroll}
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl
            refreshing={list.isRefetching && !list.isFetchingNextPage}
            onRefresh={() => list.refetch()}
            tintColor="#2d6a4f"
          />
        }
      >
        {list.isPending && (
          <ActivityIndicator size="large" color="#2d6a4f" style={{ marginTop: 48 }} />
        )}

        {list.isError && (
          <Card style={styles.emptyCard}>
            <Text variant="h2" style={{ textAlign: 'center' }}>Could not load your orders</Text>
            <Text variant="caption" style={{ textAlign: 'center', marginTop: 4 }}>
              Check your connection and try again.
            </Text>
            <Button label="Try again" style={{ marginTop: 16 }} onPress={() => list.refetch()} />
          </Card>
        )}

        {active.length > 0 && (
          <>
            <Text variant="label" style={styles.sectionLabel}>Active Orders</Text>
            {active.map(o => <OrderCard key={o.id} order={o} />)}
          </>
        )}

        {past.length > 0 && (
          <>
            <Text variant="label" style={[styles.sectionLabel, { marginTop: 8 }]}>
              Past Orders
            </Text>
            {past.map(o => <OrderCard key={o.id} order={o} />)}
          </>
        )}

        {list.isSuccess && list.orders.length === 0 && (
          <Card style={styles.emptyCard}>
            <Text style={styles.emptyEmoji}>🛒</Text>
            <Text variant="h2" style={{ textAlign: 'center', marginTop: 8 }}>No orders yet</Text>
            <Text variant="caption" style={{ textAlign: 'center', marginTop: 4 }}>
              Browse produce and place your first order!
            </Text>
          </Card>
        )}

        {list.hasNextPage && (
          <Button
            label={list.isFetchingNextPage ? 'Loading…' : 'Load more orders'}
            variant="secondary"
            disabled={list.isFetchingNextPage}
            style={{ marginTop: 4 }}
            onPress={() => list.fetchNextPage()}
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
  headerTitle: { color: '#fff', fontSize: 18 },
  backBtn: { width: 36, alignItems: 'flex-start' },
  backArrow: { color: '#fff', fontSize: 22 },
  bellBtn: { width: 36, alignItems: 'flex-end' },
  bell: { fontSize: 20 },

  scroll: { padding: 16, paddingTop: 12 },

  sectionLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: '#555',
    marginBottom: 8,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },

  orderCard: {
    marginBottom: 10,
  },
  cardTop: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    marginBottom: 6,
  },
  farmName: {
    fontSize: 15,
    fontWeight: '600',
    color: '#1b1b1b',
  },
  amount: {
    fontSize: 17,
    fontWeight: '700',
    color: '#2d6a4f',
  },
  itemsSummary: {
    color: '#666',
    marginBottom: 10,
    lineHeight: 18,
  },
  statusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  badge: {
    alignSelf: 'flex-start',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 6,
  },
  badgeText: {
    fontSize: 12,
    fontWeight: '700',
  },
  chevron: {
    fontSize: 22,
    color: '#aaa',
    marginRight: 4,
  },

  emptyCard: {
    alignItems: 'center',
    padding: 32,
    marginTop: 32,
  },
  emptyEmoji: { fontSize: 48 },
});

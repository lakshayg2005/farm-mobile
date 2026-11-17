/**
 * Issue #5 – Customer app: order list + order status timeline + notifications inbox + push
 *
 * Screen 1 of 3: Orders List
 * Shows all past and active orders grouped by status.
 * Tapping an order navigates to its detail/timeline screen.
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

export type OrderStatus =
  | 'placed'
  | 'confirmed'
  | 'packed'
  | 'ready_for_dispatch'
  | 'out_for_delivery'
  | 'delivered'
  | 'cancelled';

export interface OrderItem {
  listingId: string;
  name: string;
  qty: number;
  unit: string;
  unitPrice: number;
}

export interface Order {
  orderId: string;
  orderType: 'PRODUCE' | 'HARVEST';
  status: OrderStatus;
  placedAt: string;        // ISO date string
  deliveryDate: string;    // ISO date string
  total: number;
  currency: string;
  items: OrderItem[];
  farmName: string;
  deliveryAddress: string;
}

// ─── Mock data ────────────────────────────────────────────────────────────────

export const MOCK_ORDERS: Order[] = [
  {
    orderId: 'ord-001',
    orderType: 'PRODUCE',
    status: 'out_for_delivery',
    placedAt: '2026-11-16T08:30:00Z',
    deliveryDate: '2026-11-17',
    total: 320,
    currency: 'INR',
    farmName: 'Greenfield Organics',
    deliveryAddress: 'Flat 304, Orchid Heights, Sector 56, Gurugram',
    items: [
      { listingId: 'l1', name: 'Tomatoes', qty: 2, unit: 'kg', unitPrice: 60 },
      { listingId: 'l2', name: 'Spinach', qty: 1, unit: 'bunch', unitPrice: 40 },
      { listingId: 'l3', name: 'Potatoes', qty: 3, unit: 'kg', unitPrice: 40 },
    ],
  },
  {
    orderId: 'ord-002',
    orderType: 'PRODUCE',
    status: 'packed',
    placedAt: '2026-11-15T14:00:00Z',
    deliveryDate: '2026-11-18',
    total: 185,
    currency: 'INR',
    farmName: 'Sunrise Agro',
    deliveryAddress: 'Flat 304, Orchid Heights, Sector 56, Gurugram',
    items: [
      { listingId: 'l4', name: 'Carrots', qty: 2, unit: 'kg', unitPrice: 50 },
      { listingId: 'l5', name: 'Onions', qty: 1, unit: 'kg', unitPrice: 30 },
      { listingId: 'l6', name: 'Coriander', qty: 3, unit: 'bunch', unitPrice: 15 },
    ],
  },
  {
    orderId: 'ord-003',
    orderType: 'PRODUCE',
    status: 'delivered',
    placedAt: '2026-11-10T09:00:00Z',
    deliveryDate: '2026-11-11',
    total: 540,
    currency: 'INR',
    farmName: 'Greenfield Organics',
    deliveryAddress: 'Flat 304, Orchid Heights, Sector 56, Gurugram',
    items: [
      { listingId: 'l7', name: 'Wheat Flour', qty: 5, unit: 'kg', unitPrice: 60 },
      { listingId: 'l8', name: 'Rice', qty: 2, unit: 'kg', unitPrice: 90 },
    ],
  },
  {
    orderId: 'ord-004',
    orderType: 'PRODUCE',
    status: 'cancelled',
    placedAt: '2026-11-08T11:00:00Z',
    deliveryDate: '2026-11-09',
    total: 120,
    currency: 'INR',
    farmName: 'Sunrise Agro',
    deliveryAddress: 'Flat 304, Orchid Heights, Sector 56, Gurugram',
    items: [
      { listingId: 'l9', name: 'Brinjal', qty: 1, unit: 'kg', unitPrice: 40 },
      { listingId: 'l10', name: 'Okra', qty: 1, unit: 'kg', unitPrice: 80 },
    ],
  },
];

// ─── Helpers ─────────────────────────────────────────────────────────────────

export const STATUS_META: Record<
  OrderStatus,
  { label: string; color: string; bg: string; emoji: string }
> = {
  placed:             { label: 'Order Placed',       color: '#1565c0', bg: '#e3f2fd', emoji: '🧾' },
  confirmed:          { label: 'Confirmed',           color: '#6a1b9a', bg: '#f3e5f5', emoji: '✅' },
  packed:             { label: 'Packed',              color: '#e65100', bg: '#fff3e0', emoji: '📦' },
  ready_for_dispatch: { label: 'Ready to Dispatch',  color: '#f9a825', bg: '#fffde7', emoji: '🚜' },
  out_for_delivery:   { label: 'Out for Delivery',   color: '#00695c', bg: '#e0f2f1', emoji: '🚚' },
  delivered:          { label: 'Delivered',           color: '#2d6a4f', bg: '#e8f5e9', emoji: '🎉' },
  cancelled:          { label: 'Cancelled',           color: '#c62828', bg: '#ffebee', emoji: '❌' },
};

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

function OrderCard({ order }: { order: Order }) {
  return (
    <TouchableOpacity
      activeOpacity={0.8}
      onPress={() => router.push(`/order-detail?id=${order.orderId}`)}
    >
      <Card style={styles.orderCard}>
        {/* Top row */}
        <View style={styles.cardTop}>
          <View style={{ flex: 1 }}>
            <Text variant="label" style={styles.farmName}>
              {order.farmName}
            </Text>
            <Text variant="caption" style={{ marginTop: 2 }}>
              Placed {formatDate(order.placedAt)}
            </Text>
          </View>
          <Text style={styles.amount}>₹{order.total}</Text>
        </View>

        {/* Items summary */}
        <Text variant="caption" style={styles.itemsSummary}>
          {order.items.map(i => `${i.name} ×${i.qty}${i.unit}`).join(' · ')}
        </Text>

        {/* Status */}
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
  const [refreshing, setRefreshing] = useState(false);

  const onRefresh = () => {
    setRefreshing(true);
    // Simulate API refresh
    setTimeout(() => setRefreshing(false), 1200);
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

  const activeOrders = MOCK_ORDERS.filter(
    o => o.status !== 'delivered' && o.status !== 'cancelled',
  );
  const pastOrders = MOCK_ORDERS.filter(
    o => o.status === 'delivered' || o.status === 'cancelled',
  );

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
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#2d6a4f" />}
      >
        {/* Active orders */}
        {activeOrders.length > 0 && (
          <>
            <Text variant="label" style={styles.sectionLabel}>Active Orders</Text>
            {activeOrders.map(o => <OrderCard key={o.orderId} order={o} />)}
          </>
        )}

        {/* Past orders */}
        {pastOrders.length > 0 && (
          <>
            <Text variant="label" style={[styles.sectionLabel, { marginTop: 8 }]}>
              Past Orders
            </Text>
            {pastOrders.map(o => <OrderCard key={o.orderId} order={o} />)}
          </>
        )}

        {MOCK_ORDERS.length === 0 && (
          <Card style={styles.emptyCard}>
            <Text style={styles.emptyEmoji}>🛒</Text>
            <Text variant="h2" style={{ textAlign: 'center', marginTop: 8 }}>No orders yet</Text>
            <Text variant="caption" style={{ textAlign: 'center', marginTop: 4 }}>
              Browse produce and place your first order!
            </Text>
          </Card>
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

import { ScrollView, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Button, Card, Text } from '@farm/ui';
import { useLastCheckout } from '../src/checkout/cartStore.ts';
import { formatQty, formatRupees } from '../src/checkout/format.ts';

export default function OrderPlacedScreen() {
  const router = useRouter();
  const checkout = useLastCheckout();

  if (!checkout) {
    return (
      <View style={styles.empty}>
        <Text variant="h2">No recent order</Text>
        <Button label="Back to shop" onPress={() => router.replace('/')} />
      </View>
    );
  }

  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <Text variant="h1">Order placed ✓</Text>
      <Text variant="caption">
        {checkout.orders.length === 1
          ? 'Your order is confirmed.'
          : `Your cart was split into ${checkout.orders.length} orders, one per farm.`}
      </Text>

      {checkout.orders.map((order) => (
        <Card key={order.id} style={styles.card}>
          <Text variant="label">Order {order.id.slice(0, 8).toUpperCase()}</Text>
          <Text variant="caption">Status: {order.status.replace(/_/g, ' ').toLowerCase()}</Text>
          {order.items.map((item) => (
            <Text key={item.listingId} variant="caption">
              {item.name} · {formatQty(item.qty)} {item.unit}
            </Text>
          ))}
          <Text variant="label">Total {formatRupees(order.total)}</Text>
        </Card>
      ))}

      <Text variant="h2">Paid {formatRupees(checkout.total)}</Text>

      {/* TODO(M1): `/orders` is the order list of farm-mobile #5; adjust if that route is named differently */}
      <Button label="View my orders" onPress={() => router.replace('/orders')} />
      <Button label="Continue shopping" variant="secondary" onPress={() => router.replace('/')} />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#f5f5f0' },
  content: { padding: 16, gap: 12 },
  card: { gap: 4 },
  empty: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
    gap: 12,
    backgroundColor: '#f5f5f0',
  },
});

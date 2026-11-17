import { ScrollView, StyleSheet, TouchableOpacity, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Button, Card, Text } from '@farm/ui';
import { estimateTotal, groupByFarm, subtotal } from '../src/checkout/cart.ts';
import { removeFromCart, setCartQty, useCart } from '../src/checkout/cartStore.ts';
import { formatQty, formatRupees } from '../src/checkout/format.ts';
import { DELIVERY_FEE_PER_ORDER } from '../src/checkout/useCheckout.ts';

export default function CartScreen() {
  const router = useRouter();
  const cart = useCart();
  const groups = groupByFarm(cart);

  if (groups.length === 0) {
    return (
      <View style={styles.empty}>
        <Text variant="h2">Your cart is empty</Text>
        <Text variant="caption" style={styles.emptyHint}>
          Add produce from nearby farms to get started.
        </Text>
        {/* TODO(M1): point at the Produce tab once the browse screen (farm-mobile #3) exists */}
        <Button label="Browse produce" onPress={() => router.replace('/')} />
      </View>
    );
  }

  return (
    <View style={styles.screen}>
      <ScrollView contentContainerStyle={styles.content}>
        <Text variant="h1">Your cart</Text>
        {groups.map((group) => (
          <Card key={group.farmId} style={styles.card}>
            <Text variant="h2">{group.farmName}</Text>
            {group.items.map((item) => (
              <View key={item.listingId} style={styles.row}>
                <View style={styles.rowInfo}>
                  <Text>{item.name}</Text>
                  <Text variant="caption">
                    {formatRupees(item.unitPrice)} / {item.unit}
                  </Text>
                </View>
                <View style={styles.stepper}>
                  <StepButton label="−" onPress={() => setCartQty(item.listingId, item.qty - 1)} />
                  <Text style={styles.qty}>{formatQty(item.qty)}</Text>
                  <StepButton
                    label="+"
                    onPress={() => setCartQty(item.listingId, item.qty + 1)}
                    disabled={item.maxQty !== undefined && item.qty >= item.maxQty}
                  />
                </View>
                <TouchableOpacity
                  accessibilityLabel={`Remove ${item.name}`}
                  onPress={() => removeFromCart(item.listingId)}
                >
                  <Text variant="label">Remove</Text>
                </TouchableOpacity>
              </View>
            ))}
            <Text variant="label" style={styles.farmTotal}>
              Subtotal {formatRupees(group.subtotal)}
            </Text>
          </Card>
        ))}
        <Text variant="caption">
          One order is placed per farm; delivery is {formatRupees(DELIVERY_FEE_PER_ORDER)} per
          order.
        </Text>
      </ScrollView>

      <View style={styles.footer}>
        <View>
          <Text variant="caption">Items {formatRupees(subtotal(cart))}</Text>
          <Text variant="h2">{formatRupees(estimateTotal(cart, DELIVERY_FEE_PER_ORDER))}</Text>
        </View>
        <Button label="Checkout" onPress={() => router.push('/checkout')} />
      </View>
    </View>
  );
}

function StepButton({
  label,
  onPress,
  disabled,
}: {
  label: string;
  onPress: () => void;
  disabled?: boolean;
}) {
  return (
    <TouchableOpacity
      accessibilityRole="button"
      accessibilityLabel={label === '+' ? 'Increase quantity' : 'Decrease quantity'}
      disabled={disabled}
      onPress={onPress}
      style={[styles.step, disabled && styles.stepDisabled]}
    >
      <Text variant="h2">{label}</Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#f5f5f0' },
  content: { padding: 16, gap: 12 },
  card: { gap: 10 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  rowInfo: { flex: 1 },
  stepper: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  step: {
    width: 32,
    height: 32,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#2d6a4f',
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepDisabled: { opacity: 0.35 },
  qty: { minWidth: 28, textAlign: 'center' },
  farmTotal: { textAlign: 'right' },
  footer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 16,
    backgroundColor: '#fff',
    borderTopWidth: 1,
    borderTopColor: '#e5e5e0',
  },
  empty: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
    gap: 12,
    backgroundColor: '#f5f5f0',
  },
  emptyHint: { marginBottom: 8 },
});

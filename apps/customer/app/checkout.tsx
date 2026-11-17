import { useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  TextInput,
  View,
} from 'react-native';
import { useRouter } from 'expo-router';
import { Button, Card, Text } from '@farm/ui';
import {
  emptyAddress,
  validateAddress,
  type AddressErrors,
  type AddressForm,
} from '../src/checkout/address.ts';
import { estimateTotal, groupByFarm } from '../src/checkout/cart.ts';
import { adjustCartToStock, useCart } from '../src/checkout/cartStore.ts';
import type { CheckoutError } from '../src/checkout/errors.ts';
import { formatQty, formatRupees } from '../src/checkout/format.ts';
import { DELIVERY_FEE_PER_ORDER, useCheckout } from '../src/checkout/useCheckout.ts';

export default function CheckoutScreen() {
  const router = useRouter();
  const cart = useCart();
  const { submit, submitting, error, dismissError } = useCheckout();
  const [form, setForm] = useState<AddressForm>(emptyAddress);
  const [notes, setNotes] = useState('');
  const [errors, setErrors] = useState<AddressErrors>({});
  const groups = groupByFarm(cart);

  const set = (field: keyof AddressForm) => (value: string) => {
    setForm((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
    if (error) dismissError();
  };

  async function placeOrder() {
    const result = validateAddress(form);
    if (!result.ok) {
      setErrors(result.errors);
      return;
    }
    const checkout = await submit(cart, result.value, notes.trim() || undefined);
    if (checkout) router.replace('/order-placed');
  }

  if (groups.length === 0 && !submitting) {
    return (
      <View style={styles.empty}>
        <Text variant="h2">Nothing to check out</Text>
        <Button label="Back to cart" onPress={() => router.replace('/cart')} />
      </View>
    );
  }

  return (
    <KeyboardAvoidingView
      style={styles.screen}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <Text variant="h1">Checkout</Text>

        <Card style={styles.card}>
          <Text variant="h2">Order summary</Text>
          {groups.map((group) => (
            <View key={group.farmId} style={styles.summaryFarm}>
              <Text variant="label">{group.farmName}</Text>
              {group.items.map((item) => (
                <Text key={item.listingId} variant="caption">
                  {item.name} · {formatQty(item.qty)} {item.unit} ·{' '}
                  {formatRupees(item.unitPrice * item.qty)}
                </Text>
              ))}
            </View>
          ))}
          <Text variant="label">
            Total {formatRupees(estimateTotal(cart, DELIVERY_FEE_PER_ORDER))} (incl. delivery)
          </Text>
          <Text variant="caption">Payment is simulated: no real money is taken.</Text>
        </Card>

        <Card style={styles.card}>
          <Text variant="h2">Delivery address</Text>
          <Field
            label="Street address"
            value={form.line1}
            error={errors.line1}
            onChange={set('line1')}
          />
          <Field label="Landmark (optional)" value={form.line2} onChange={set('line2')} />
          <Field label="City" value={form.city} error={errors.city} onChange={set('city')} />
          <Field
            label="Pincode"
            value={form.pincode}
            error={errors.pincode}
            onChange={set('pincode')}
            keyboardType="number-pad"
            maxLength={6}
          />
          <Field
            label="Phone (optional)"
            value={form.contactPhone}
            error={errors.contactPhone}
            onChange={set('contactPhone')}
            keyboardType="phone-pad"
          />
          {/* TODO(M1): replace the two fields below with the device location (expo-location) */}
          <Field
            label="Latitude"
            value={form.lat}
            error={errors.lat}
            onChange={set('lat')}
            keyboardType="numbers-and-punctuation"
          />
          <Field
            label="Longitude"
            value={form.lng}
            error={errors.lng}
            onChange={set('lng')}
            keyboardType="numbers-and-punctuation"
          />
          <Field label="Note for the farmer (optional)" value={notes} onChange={setNotes} />
        </Card>

        {error ? (
          <ErrorBanner
            error={error}
            onAdjust={adjustCartToStock}
            onLogin={() => router.push('/login')}
          />
        ) : null}

        <Button
          label={`Place order · ${formatRupees(estimateTotal(cart, DELIVERY_FEE_PER_ORDER))}`}
          onPress={placeOrder}
          loading={submitting}
          disabled={submitting}
        />
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

function Field({
  label,
  value,
  onChange,
  error,
  keyboardType,
  maxLength,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  error?: string;
  keyboardType?: 'default' | 'number-pad' | 'phone-pad' | 'numbers-and-punctuation';
  maxLength?: number;
}) {
  return (
    <View style={styles.field}>
      <Text variant="label">{label}</Text>
      <TextInput
        accessibilityLabel={label}
        value={value}
        onChangeText={onChange}
        keyboardType={keyboardType}
        maxLength={maxLength}
        style={[styles.input, error ? styles.inputError : null]}
      />
      {error ? (
        <Text variant="caption" style={styles.errorText}>
          {error}
        </Text>
      ) : null}
    </View>
  );
}

function ErrorBanner({
  error,
  onAdjust,
  onLogin,
}: {
  error: CheckoutError;
  onAdjust: (shortages: Extract<CheckoutError, { kind: 'stock' }>['shortages']) => void;
  onLogin: () => void;
}) {
  const cart = useCart();
  return (
    <Card style={styles.banner}>
      <Text variant="label" style={styles.errorText}>
        {error.message}
      </Text>
      {error.kind === 'stock'
        ? error.shortages.map((shortage) => {
            const item = cart.items.find((i) => i.listingId === shortage.listingId);
            return (
              <Text key={shortage.listingId} variant="caption">
                {item?.name ?? 'An item'}:{' '}
                {shortage.available > 0
                  ? `only ${formatQty(shortage.available)} ${item?.unit ?? ''} left`
                  : 'sold out'}
              </Text>
            );
          })
        : null}
      {error.kind === 'stock' ? (
        <Button
          label="Update my cart"
          variant="secondary"
          onPress={() => onAdjust(error.shortages)}
        />
      ) : null}
      {error.kind === 'validation'
        ? error.fields.map((f) => (
            <Text key={f.field} variant="caption">
              {f.field}: {f.message}
            </Text>
          ))
        : null}
      {/* TODO(M1): the login route is the one from the shared auth screens (farm-mobile #2) */}
      {error.kind === 'unauthorized' ? (
        <Button label="Log in" variant="secondary" onPress={onLogin} />
      ) : null}
    </Card>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#f5f5f0' },
  content: { padding: 16, gap: 12 },
  card: { gap: 10 },
  summaryFarm: { gap: 2 },
  field: { gap: 4 },
  input: {
    borderWidth: 1,
    borderColor: '#cfcfc8',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 15,
    backgroundColor: '#fff',
  },
  inputError: { borderColor: '#b3261e' },
  errorText: { color: '#b3261e' },
  banner: { gap: 8, borderWidth: 1, borderColor: '#b3261e' },
  empty: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
    gap: 12,
    backgroundColor: '#f5f5f0',
  },
});

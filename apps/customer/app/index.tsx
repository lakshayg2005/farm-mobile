import React from 'react';
import { View, StyleSheet, ActivityIndicator } from 'react-native';
import { router } from 'expo-router';
import { Button, Text, Card } from '@farm/ui';
import { useAuth } from '@farm/api';
import { usePushTokenRegistration } from './usePushToken';

export default function CustomerHomeScreen() {
  const { user, isAuthenticated, isLoading, logout } = useAuth();

  // Register push token whenever user is authenticated (non-fatal if unavailable)
  usePushTokenRegistration(isAuthenticated && user ? (user as { accessToken?: string }).accessToken ?? null : null);

  if (isLoading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#2d6a4f" />
      </View>
    );
  }

  if (!isAuthenticated || !user?.roles?.includes('customer')) {
    return (
      <View style={styles.container}>
        <Card style={styles.card}>
          <Text variant="h1">🥕 Farm Customer</Text>
          <Text variant="body" style={styles.subtitle}>
            Welcome! Log in with your customer account to browse fresh produce and reserve farm plots.
          </Text>
          <Button
            label="Log In / Sign Up"
            style={{ marginTop: 24 }}
            onPress={() => router.push('/login')}
          />
        </Card>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Card style={styles.card}>
        <View style={styles.headerRow}>
          <Text style={styles.avatar}>🥕</Text>
          <View style={{ flex: 1 }}>
            <Text variant="h2">{user.name || 'Customer'}</Text>
            <Text variant="caption">{user.phone}</Text>
          </View>
        </View>

        <View style={styles.badge}>
          <Text style={styles.badgeText}>Verified Customer</Text>
        </View>

        <View style={styles.infoBox}>
          <Text variant="label">Account Status</Text>
          <Text variant="body" style={{ marginTop: 2 }}>
            Roles: {user.roles.join(', ')}
          </Text>
          <Text variant="caption" style={{ marginTop: 2 }}>
            ID: {user.id}
          </Text>
        </View>

        <View style={styles.tabPreview}>
          <Text variant="h2" style={{ fontSize: 16 }}>Quick Actions</Text>
          <Text variant="caption" style={{ marginTop: 4 }}>
            • Track your orders and delivery status
          </Text>
          <Text variant="caption" style={{ marginTop: 2 }}>
            • Browse produce near me (Sprint 2)
          </Text>
          <Text variant="caption" style={{ marginTop: 2 }}>
            • Reserve farmland plots (Sprint 2)
          </Text>
        </View>

        <Button
          label="📦  My Orders"
          style={{ marginTop: 16 }}
          onPress={() => router.push('/orders')}
        />

        <Button
          label="🔔  Notifications"
          variant="secondary"
          style={{ marginTop: 10 }}
          onPress={() => router.push('/notifications')}
        />

        <Button
          label="Log Out"
          variant="ghost"
          style={{ marginTop: 10 }}
          onPress={async () => {
            await logout();
          }}
        />
      </Card>
    </View>
  );
}

const styles = StyleSheet.create({
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#f5f5f0',
  },
  container: {
    flex: 1,
    backgroundColor: '#f5f5f0',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  card: { width: '100%', maxWidth: 400 },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    marginBottom: 8,
  },
  avatar: {
    fontSize: 36,
  },
  subtitle: {
    marginTop: 8,
    color: '#555',
  },
  badge: {
    backgroundColor: '#e8f5e9',
    alignSelf: 'flex-start',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 6,
    marginVertical: 10,
  },
  badgeText: {
    fontSize: 12,
    fontWeight: '700',
    color: '#2d6a4f',
  },
  infoBox: {
    backgroundColor: '#fafafa',
    borderWidth: 1,
    borderColor: '#eee',
    borderRadius: 8,
    padding: 12,
    marginTop: 6,
  },
  tabPreview: {
    marginTop: 16,
    padding: 12,
    backgroundColor: '#f0f7f3',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#d8ebe0',
  },
});

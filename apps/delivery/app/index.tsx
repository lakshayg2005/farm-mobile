import React from 'react';
import { View, StyleSheet, ActivityIndicator } from 'react-native';
import { router } from 'expo-router';
import { Button, Text, Card } from '@farm/ui';
import { useAuth } from '@farm/api';

export default function DeliveryHomeScreen() {
  const { user, isAuthenticated, isLoading, logout } = useAuth();

  if (isLoading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#2d6a4f" />
      </View>
    );
  }

  if (!isAuthenticated || !user?.roles?.includes('delivery')) {
    return (
      <View style={styles.container}>
        <Card style={styles.card}>
          <Text variant="h1">🚚 Farm Delivery</Text>
          <Text variant="body" style={styles.subtitle}>
            Welcome! Log in with your Delivery Partner account to access your assigned pooled routes and stops.
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
          <Text style={styles.avatar}>🚚</Text>
          <View style={{ flex: 1 }}>
            <Text variant="h2">{user.name || 'Delivery Partner'}</Text>
            <Text variant="caption">{user.phone}</Text>
          </View>
        </View>

        <View style={styles.badge}>
          <Text style={styles.badgeText}>Active Delivery Partner</Text>
        </View>

        <View style={styles.infoBox}>
          <Text variant="label">Partner Details</Text>
          <Text variant="body" style={{ marginTop: 2 }}>
            Roles: {user.roles.join(', ')}
          </Text>
          <Text variant="caption" style={{ marginTop: 2 }}>
            Partner ID: {user.id}
          </Text>
        </View>

        <View style={styles.routePreview}>
          <Text variant="h2" style={{ fontSize: 16 }}>{"Today's Route (Sprint 3)"}</Text>
          <Text variant="caption" style={{ marginTop: 4 }}>
            • Pooled stops with pickup and drop sequence
          </Text>
          <Text variant="caption" style={{ marginTop: 2 }}>
            • Contactless delivery proof (OTP + photo)
          </Text>
        </View>

        <Button
          label="Log Out"
          variant="secondary"
          style={{ marginTop: 20 }}
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
    backgroundColor: '#e3f2fd',
    alignSelf: 'flex-start',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 6,
    marginVertical: 10,
  },
  badgeText: {
    fontSize: 12,
    fontWeight: '700',
    color: '#1565c0',
  },
  infoBox: {
    backgroundColor: '#fafafa',
    borderWidth: 1,
    borderColor: '#eee',
    borderRadius: 8,
    padding: 12,
    marginTop: 6,
  },
  routePreview: {
    marginTop: 16,
    padding: 12,
    backgroundColor: '#f0f4f8',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#d0dce8',
  },
});

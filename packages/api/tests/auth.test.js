const { describe, it } = require('node:test');
const assert = require('node:assert/strict');

function normalizePhone(value) {
  const digits = value.replace(/[\s\-()]/g, '');
  if (/^0?\d{10}$/.test(digits)) {
    return '+91' + digits.slice(-10);
  }
  return digits;
}

// In-memory token storage simulation matching authStorage
class MockAuthStorage {
  constructor() {
    this.store = new Map();
  }
  async getAccessToken() {
    return this.store.get('access_token') || null;
  }
  async setAccessToken(token) {
    this.store.set('access_token', token);
  }
  async getRefreshToken() {
    return this.store.get('refresh_token') || null;
  }
  async setRefreshToken(token) {
    this.store.set('refresh_token', token);
  }
  async getUser() {
    const raw = this.store.get('user');
    return raw ? JSON.parse(raw) : null;
  }
  async setUser(user) {
    this.store.set('user', JSON.stringify(user));
  }
  async clear() {
    this.store.clear();
  }
}

function checkRoleEligibility(user, requiredRole) {
  if (!requiredRole) return true;
  const roles = Array.isArray(requiredRole) ? requiredRole : [requiredRole];
  const hasAllowedRole = roles.some((r) => user.roles.includes(r));
  if (!hasAllowedRole) {
    const rolesStr = user.roles.join(', ') || 'none';
    const requiredStr = roles.join(' or ');
    throw new Error(
      `Access denied: Your account role is [${rolesStr}], but this app requires [${requiredStr}].`
    );
  }
  return true;
}

describe('Mobile Auth Logic Tests (M3)', () => {
  describe('normalizePhone', () => {
    it('normalizes 10-digit number to E.164 (+91)', () => {
      assert.equal(normalizePhone('9876543210'), '+919876543210');
    });

    it('handles formatted numbers with spaces and dashes', () => {
      assert.equal(normalizePhone('98765 43210'), '+919876543210');
      assert.equal(normalizePhone('+91 98765-43210'), '+919876543210');
    });

    it('strips leading 0 before converting', () => {
      assert.equal(normalizePhone('09876543210'), '+919876543210');
    });
  });

  describe('authStorage behavior', () => {
    it('stores, retrieves, and clears tokens and user', async () => {
      const storage = new MockAuthStorage();
      await storage.clear();

      assert.equal(await storage.getAccessToken(), null);
      assert.equal(await storage.getRefreshToken(), null);
      assert.equal(await storage.getUser(), null);

      await storage.setAccessToken('sample-access-token-123');
      await storage.setRefreshToken('sample-refresh-token-456');

      assert.equal(await storage.getAccessToken(), 'sample-access-token-123');
      assert.equal(await storage.getRefreshToken(), 'sample-refresh-token-456');

      const mockUser = {
        id: 'usr-001',
        name: 'Ramesh Patel',
        phone: '+919876543210',
        email: 'ramesh@example.com',
        roles: ['customer'],
        phoneVerified: true,
        farmerVerified: false,
        createdAt: '2026-11-16T12:00:00Z',
      };

      await storage.setUser(mockUser);
      const retrieved = await storage.getUser();
      assert.deepEqual(retrieved, mockUser);

      await storage.clear();
      assert.equal(await storage.getAccessToken(), null);
      assert.equal(await storage.getRefreshToken(), null);
      assert.equal(await storage.getUser(), null);
    });
  });

  describe('Role-based Access Control (Customer vs Delivery)', () => {
    const customerUser = {
      id: 'cust-1',
      name: 'Customer Aarti',
      phone: '+919876543210',
      roles: ['customer'],
    };

    const deliveryUser = {
      id: 'del-1',
      name: 'Rider Deepak',
      phone: '+919876543211',
      roles: ['delivery'],
    };

    it('allows customer user into customer app', () => {
      assert.equal(checkRoleEligibility(customerUser, 'customer'), true);
    });

    it('blocks customer user from delivery app with role mismatch error', () => {
      assert.throws(
        () => checkRoleEligibility(customerUser, 'delivery'),
        /Access denied.*customer.*delivery/
      );
    });

    it('allows delivery partner into delivery app', () => {
      assert.equal(checkRoleEligibility(deliveryUser, 'delivery'), true);
    });

    it('blocks delivery partner from customer app with role mismatch error', () => {
      assert.throws(
        () => checkRoleEligibility(deliveryUser, 'customer'),
        /Access denied.*delivery.*customer/
      );
    });
  });
});

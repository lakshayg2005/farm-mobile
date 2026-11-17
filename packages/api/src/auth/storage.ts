import * as SecureStore from 'expo-secure-store';
import type { User } from '../types/auth';

const ACCESS_TOKEN_KEY = 'farm_access_token';
const REFRESH_TOKEN_KEY = 'farm_refresh_token';
const USER_KEY = 'farm_user_data';

// In-memory fallback for environments where SecureStore isn't available (e.g. Node tests, mock runs)
const memoryStorage = new Map<string, string>();

async function isSecureStoreAvailable(): Promise<boolean> {
  try {
    return await SecureStore.isAvailableAsync();
  } catch {
    return false;
  }
}

export const authStorage = {
  async getAccessToken(): Promise<string | null> {
    try {
      if (await isSecureStoreAvailable()) {
        return await SecureStore.getItemAsync(ACCESS_TOKEN_KEY);
      }
    } catch {
      // fallback to memory
    }
    return memoryStorage.get(ACCESS_TOKEN_KEY) ?? null;
  },

  async setAccessToken(token: string): Promise<void> {
    try {
      if (await isSecureStoreAvailable()) {
        await SecureStore.setItemAsync(ACCESS_TOKEN_KEY, token);
        return;
      }
    } catch {
      // fallback to memory
    }
    memoryStorage.set(ACCESS_TOKEN_KEY, token);
  },

  async getRefreshToken(): Promise<string | null> {
    try {
      if (await isSecureStoreAvailable()) {
        return await SecureStore.getItemAsync(REFRESH_TOKEN_KEY);
      }
    } catch {
      // fallback to memory
    }
    return memoryStorage.get(REFRESH_TOKEN_KEY) ?? null;
  },

  async setRefreshToken(token: string): Promise<void> {
    try {
      if (await isSecureStoreAvailable()) {
        await SecureStore.setItemAsync(REFRESH_TOKEN_KEY, token);
        return;
      }
    } catch {
      // fallback to memory
    }
    memoryStorage.set(REFRESH_TOKEN_KEY, token);
  },

  async getUser(): Promise<User | null> {
    try {
      let raw: string | null = null;
      if (await isSecureStoreAvailable()) {
        raw = await SecureStore.getItemAsync(USER_KEY);
      }
      if (!raw) {
        raw = memoryStorage.get(USER_KEY) ?? null;
      }
      if (raw) {
        return JSON.parse(raw) as User;
      }
    } catch {
      // fallback
    }
    return null;
  },

  async setUser(user: User): Promise<void> {
    const raw = JSON.stringify(user);
    try {
      if (await isSecureStoreAvailable()) {
        await SecureStore.setItemAsync(USER_KEY, raw);
        return;
      }
    } catch {
      // fallback
    }
    memoryStorage.set(USER_KEY, raw);
  },

  async clear(): Promise<void> {
    try {
      if (await isSecureStoreAvailable()) {
        await SecureStore.deleteItemAsync(ACCESS_TOKEN_KEY);
        await SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY);
        await SecureStore.deleteItemAsync(USER_KEY);
      }
    } catch {
      // fallback
    }
    memoryStorage.delete(ACCESS_TOKEN_KEY);
    memoryStorage.delete(REFRESH_TOKEN_KEY);
    memoryStorage.delete(USER_KEY);
  },
};

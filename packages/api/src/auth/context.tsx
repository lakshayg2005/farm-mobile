import React, { createContext, useContext, useEffect, useState, useCallback, useMemo } from 'react';
import { authService, AuthApiError } from './service';
import { authStorage } from './storage';
import { setAuthToken } from '../client';
import type { User, Role, OtpRequestResponse } from '../types/auth';

export interface AuthContextValue {
  user: User | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
  requestOtp: (phone: string) => Promise<OtpRequestResponse>;
  verifyOtp: (phone: string, code: string, requiredRole?: Role | Role[]) => Promise<User>;
  loginWithPassword: (phone: string, password: string, requiredRole?: Role | Role[]) => Promise<User>;
  logout: () => Promise<void>;
  hasRole: (role: Role) => boolean;
  clearError: () => void;
  // Dev mode helper to mock login when backend isn't reachable
  devMockLogin: (role: Role) => Promise<User>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function checkRoleEligibility(user: User, requiredRole?: Role | Role[]): void {
  if (!requiredRole) return;
  const roles = Array.isArray(requiredRole) ? requiredRole : [requiredRole];
  const hasAllowedRole = roles.some((r) => user.roles.includes(r));
  if (!hasAllowedRole) {
    const rolesStr = user.roles.join(', ') || 'none';
    const requiredStr = roles.join(' or ');
    throw new AuthApiError(
      `Access denied: Your account role is [${rolesStr}], but this app requires [${requiredStr}].`,
      'FORBIDDEN_ROLE',
      403
    );
  }
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Restore session on mount
  useEffect(() => {
    let isMounted = true;
    async function restoreSession() {
      try {
        const storedToken = await authStorage.getAccessToken();
        const storedUser = await authStorage.getUser();

        if (storedToken && storedUser) {
          setAuthToken(storedToken);
          if (isMounted) {
            setAccessToken(storedToken);
            setUser(storedUser);
          }

          // Verify token against /me in background
          try {
            const freshUser = await authService.getMe();
            if (isMounted) {
              setUser(freshUser);
              await authStorage.setUser(freshUser);
            }
          } catch (verifyErr) {
            // If token expired, try refresh
            try {
              const refreshed = await authService.refresh();
              if (isMounted) {
                setAccessToken(refreshed.accessToken);
                setUser(refreshed.user);
              }
            } catch {
              // Refresh failed, clean session
              await authStorage.clear();
              setAuthToken(null);
              if (isMounted) {
                setAccessToken(null);
                setUser(null);
              }
            }
          }
        }
      } catch {
        // Storage read error
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    }

    restoreSession();
    return () => {
      isMounted = false;
    };
  }, []);

  const clearError = useCallback(() => setError(null), []);

  const requestOtp = useCallback(async (phone: string): Promise<OtpRequestResponse> => {
    setError(null);
    try {
      return await authService.requestOtp(phone);
    } catch (err: unknown) {
      const msg = err instanceof AuthApiError ? err.message : 'Failed to send OTP. Please check your network.';
      setError(msg);
      throw err;
    }
  }, []);

  const verifyOtp = useCallback(
    async (phone: string, code: string, requiredRole?: Role | Role[]): Promise<User> => {
      setError(null);
      try {
        const data = await authService.verifyOtp(phone, code);
        try {
          checkRoleEligibility(data.user, requiredRole);
        } catch (roleErr) {
          await authService.logout();
          setUser(null);
          setAccessToken(null);
          throw roleErr;
        }

        setUser(data.user);
        setAccessToken(data.accessToken);
        return data.user;
      } catch (err: unknown) {
        const msg = err instanceof AuthApiError ? err.message : 'Invalid OTP code. Please try again.';
        setError(msg);
        throw err;
      }
    },
    []
  );

  const loginWithPassword = useCallback(
    async (phone: string, password: string, requiredRole?: Role | Role[]): Promise<User> => {
      setError(null);
      try {
        const data = await authService.loginWithPassword(phone, password);
        try {
          checkRoleEligibility(data.user, requiredRole);
        } catch (roleErr) {
          await authService.logout();
          setUser(null);
          setAccessToken(null);
          throw roleErr;
        }

        setUser(data.user);
        setAccessToken(data.accessToken);
        return data.user;
      } catch (err: unknown) {
        const msg = err instanceof AuthApiError ? err.message : 'Login failed. Invalid phone number or password.';
        setError(msg);
        throw err;
      }
    },
    []
  );

  const logout = useCallback(async (): Promise<void> => {
    try {
      await authService.logout();
    } finally {
      setUser(null);
      setAccessToken(null);
      setError(null);
    }
  }, []);

  const hasRole = useCallback(
    (role: Role): boolean => {
      return !!user?.roles?.includes(role);
    },
    [user]
  );

  const devMockLogin = useCallback(
    async (role: Role): Promise<User> => {
      const mockUser: User = {
        id: `dev-user-${role}-001`,
        name: role === 'delivery' ? 'Dev Rider Suresh' : 'Dev Customer Anita',
        phone: role === 'delivery' ? '+919876543211' : '+919876543210',
        email: `${role}@farm.local`,
        roles: [role],
        phoneVerified: true,
        farmerVerified: false,
        createdAt: new Date().toISOString(),
      };
      const mockToken = `mock-token-${role}-${Date.now()}`;
      await authStorage.setAccessToken(mockToken);
      await authStorage.setRefreshToken(`mock-refresh-${mockToken}`);
      await authStorage.setUser(mockUser);
      setAuthToken(mockToken);
      setUser(mockUser);
      setAccessToken(mockToken);
      setError(null);
      return mockUser;
    },
    []
  );

  const value = useMemo(
    () => ({
      user,
      accessToken,
      isAuthenticated: !!user && !!accessToken,
      isLoading,
      error,
      requestOtp,
      verifyOtp,
      loginWithPassword,
      logout,
      hasRole,
      clearError,
      devMockLogin,
    }),
    [user, accessToken, isLoading, error, requestOtp, verifyOtp, loginWithPassword, logout, hasRole, clearError, devMockLogin]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}

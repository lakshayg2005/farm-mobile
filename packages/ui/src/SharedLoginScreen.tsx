import React, { useState, useEffect } from 'react';
import {
  View,
  StyleSheet,
  TouchableOpacity,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
} from 'react-native';
import { useAuth, Role, User } from '@farm/api';
import { Text } from './Text';
import { Button } from './Button';
import { Card } from './Card';
import { Input } from './Input';
import { OtpInput } from './OtpInput';

export interface SharedLoginScreenProps {
  appName: string;
  appSubtitle?: string;
  requiredRole: 'customer' | 'delivery';
  onSuccess?: (user: User) => void;
  logoEmoji?: string;
}

export function SharedLoginScreen({
  appName,
  appSubtitle,
  requiredRole,
  onSuccess,
  logoEmoji = '🌾',
}: SharedLoginScreenProps) {
  const {
    requestOtp,
    verifyOtp,
    loginWithPassword,
    devMockLogin,
    error: authError,
    clearError,
  } = useAuth();

  const [authMode, setAuthMode] = useState<'otp' | 'password'>('otp');
  const [step, setStep] = useState<'phone' | 'otp'>('phone');

  // Form states
  const [phone, setPhone] = useState('');
  const [otp, setOtp] = useState('');
  const [password, setPassword] = useState('');

  // UI status
  const [loading, setLoading] = useState(false);
  const [localError, setLocalError] = useState<string | null>(null);
  const [devCode, setDevCode] = useState<string | null>(null);
  const [countdown, setCountdown] = useState<number>(0);

  // Timer countdown
  useEffect(() => {
    if (countdown <= 0) return;
    const interval = setInterval(() => {
      setCountdown((prev) => prev - 1);
    }, 1000);
    return () => clearInterval(interval);
  }, [countdown]);

  const activeError = localError || authError;

  const validatePhone = (raw: string): boolean => {
    const clean = raw.replace(/[\s\-()]/g, '');
    return /^(0|\+91)?[6-9]\d{9}$/.test(clean);
  };

  const handleSendOtp = async () => {
    setLocalError(null);
    clearError();
    if (!validatePhone(phone)) {
      setLocalError('Please enter a valid 10-digit mobile number.');
      return;
    }

    setLoading(true);
    try {
      const res = await requestOtp(phone);
      if (res.devCode) {
        setDevCode(res.devCode);
        setOtp(res.devCode);
      }
      setCountdown(res.resendAfter || 60);
      setStep('otp');
    } catch {
      // Error handled by AuthContext
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyOtp = async () => {
    setLocalError(null);
    clearError();
    if (otp.length !== 6) {
      setLocalError('Please enter the complete 6-digit OTP.');
      return;
    }

    setLoading(true);
    try {
      const user = await verifyOtp(phone, otp, requiredRole);
      onSuccess?.(user);
    } catch {
      // Role mismatch or invalid OTP handled by AuthContext
    } finally {
      setLoading(false);
    }
  };

  const handlePasswordLogin = async () => {
    setLocalError(null);
    clearError();
    if (!validatePhone(phone)) {
      setLocalError('Please enter a valid 10-digit mobile number.');
      return;
    }
    if (!password || password.length < 6) {
      setLocalError('Please enter your password (minimum 6 characters).');
      return;
    }

    setLoading(true);
    try {
      const user = await loginWithPassword(phone, password, requiredRole);
      onSuccess?.(user);
    } catch {
      // Handled by AuthContext
    } finally {
      setLoading(false);
    }
  };

  const handleDevMockLogin = async (roleToUse: Role) => {
    setLocalError(null);
    clearError();
    setLoading(true);
    try {
      const user = await devMockLogin(roleToUse);
      if (roleToUse !== requiredRole) {
        setLocalError(
          `Test Role Mismatch: Signed in as [${roleToUse}], but this app requires [${requiredRole}]. Role check verified!`
        );
        return;
      }
      onSuccess?.(user);
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      style={styles.keyboardContainer}
    >
      <ScrollView
        contentContainerStyle={styles.scrollContainer}
        keyboardShouldPersistTaps="handled"
      >
        <View style={styles.header}>
          <Text style={styles.logo}>{logoEmoji}</Text>
          <Text variant="h1" style={styles.title}>
            {appName}
          </Text>
          {appSubtitle && (
            <Text variant="caption" style={styles.subtitle}>
              {appSubtitle}
            </Text>
          )}
          <View style={styles.roleBadge}>
            <Text style={styles.roleBadgeText}>
              Role: {requiredRole.toUpperCase()}
            </Text>
          </View>
        </View>

        <Card style={styles.card}>
          {/* Mode Switcher */}
          <View style={styles.tabs}>
            <TouchableOpacity
              style={[styles.tab, authMode === 'otp' && styles.activeTab]}
              onPress={() => {
                setAuthMode('otp');
                setLocalError(null);
                clearError();
              }}
            >
              <Text style={[styles.tabText, authMode === 'otp' && styles.activeTabText]}>
                OTP Login
              </Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.tab, authMode === 'password' && styles.activeTab]}
              onPress={() => {
                setAuthMode('password');
                setLocalError(null);
                clearError();
              }}
            >
              <Text style={[styles.tabText, authMode === 'password' && styles.activeTabText]}>
                Password
              </Text>
            </TouchableOpacity>
          </View>

          {/* Error Banner */}
          {activeError && (
            <View style={styles.errorBanner}>
              <Text style={styles.errorTitle}>Authentication Error</Text>
              <Text style={styles.errorMessage}>{activeError}</Text>
            </View>
          )}

          {authMode === 'otp' ? (
            step === 'phone' ? (
              <View>
                <Input
                  label="Phone Number"
                  placeholder="98765 43210"
                  prefix="+91"
                  keyboardType="phone-pad"
                  maxLength={10}
                  value={phone}
                  onChangeText={(t) => {
                    setPhone(t.replace(/[^0-9]/g, ''));
                    if (localError) setLocalError(null);
                  }}
                  autoFocus
                />
                <Button
                  label="Send OTP Code"
                  loading={loading}
                  style={styles.actionButton}
                  onPress={handleSendOtp}
                />
              </View>
            ) : (
              <View>
                <Text variant="body" style={styles.stepInfo}>
                  Enter the 6-digit code sent to +91 {phone}
                </Text>

                {devCode && (
                  <View style={styles.devCodeBadge}>
                    <Text style={styles.devCodeText}>
                      Dev Code auto-filled: {devCode}
                    </Text>
                  </View>
                )}

                <OtpInput
                  length={6}
                  value={otp}
                  onChange={(val) => {
                    setOtp(val);
                    if (localError) setLocalError(null);
                  }}
                  disabled={loading}
                />

                <Button
                  label="Verify & Login"
                  loading={loading}
                  style={styles.actionButton}
                  onPress={handleVerifyOtp}
                />

                <View style={styles.otpActions}>
                  <TouchableOpacity
                    disabled={countdown > 0 || loading}
                    onPress={handleSendOtp}
                  >
                    <Text
                      style={[
                        styles.resendText,
                        countdown > 0 && styles.resendDisabled,
                      ]}
                    >
                      {countdown > 0 ? `Resend OTP in ${countdown}s` : 'Resend OTP'}
                    </Text>
                  </TouchableOpacity>

                  <TouchableOpacity
                    onPress={() => {
                      setStep('phone');
                      setOtp('');
                      setDevCode(null);
                      setLocalError(null);
                    }}
                  >
                    <Text style={styles.changePhoneText}>Change Number</Text>
                  </TouchableOpacity>
                </View>
              </View>
            )
          ) : (
            <View>
              <Input
                label="Phone Number"
                placeholder="98765 43210"
                prefix="+91"
                keyboardType="phone-pad"
                maxLength={10}
                value={phone}
                onChangeText={(t) => {
                  setPhone(t.replace(/[^0-9]/g, ''));
                  if (localError) setLocalError(null);
                }}
              />
              <Input
                label="Password"
                placeholder="••••••••"
                secureTextEntry
                value={password}
                onChangeText={setPassword}
              />
              <Button
                label="Log In"
                loading={loading}
                style={styles.actionButton}
                onPress={handlePasswordLogin}
              />
            </View>
          )}

          {/* Dev Quick Test Shortcuts */}
          <View style={styles.devSection}>
            <Text variant="caption" style={styles.devSectionTitle}>
              🛠️ Quick Test Access (Dev Mode)
            </Text>
            <View style={styles.devButtonsRow}>
              <TouchableOpacity
                style={styles.devButton}
                onPress={() => handleDevMockLogin(requiredRole)}
                disabled={loading}
              >
                <Text style={styles.devButtonText}>
                  Mock {requiredRole.toUpperCase()} Login
                </Text>
              </TouchableOpacity>

              <TouchableOpacity
                style={[styles.devButton, styles.devButtonAlt]}
                onPress={() =>
                  handleDevMockLogin(
                    requiredRole === 'customer' ? 'delivery' : 'customer'
                  )
                }
                disabled={loading}
              >
                <Text style={styles.devButtonTextAlt}>
                  Test Role Reject ({requiredRole === 'customer' ? 'delivery' : 'customer'})
                </Text>
              </TouchableOpacity>
            </View>
          </View>
        </Card>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  keyboardContainer: {
    flex: 1,
    backgroundColor: '#f5f5f0',
  },
  scrollContainer: {
    flexGrow: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 20,
    paddingVertical: 36,
  },
  header: {
    alignItems: 'center',
    marginBottom: 20,
  },
  logo: {
    fontSize: 48,
    marginBottom: 8,
  },
  title: {
    textAlign: 'center',
  },
  subtitle: {
    textAlign: 'center',
    marginTop: 4,
    color: '#666',
  },
  roleBadge: {
    marginTop: 10,
    backgroundColor: '#e8f5e9',
    paddingHorizontal: 12,
    paddingVertical: 4,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#c8e6c9',
  },
  roleBadgeText: {
    fontSize: 12,
    fontWeight: '700',
    color: '#2d6a4f',
    letterSpacing: 0.5,
  },
  card: {
    width: '100%',
    maxWidth: 400,
    padding: 20,
  },
  tabs: {
    flexDirection: 'row',
    backgroundColor: '#f0f0eb',
    borderRadius: 8,
    padding: 4,
    marginBottom: 16,
  },
  tab: {
    flex: 1,
    paddingVertical: 8,
    alignItems: 'center',
    borderRadius: 6,
  },
  activeTab: {
    backgroundColor: '#fff',
    shadowColor: '#000',
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
  },
  tabText: {
    fontSize: 13,
    fontWeight: '500',
    color: '#777',
  },
  activeTabText: {
    color: '#2d6a4f',
    fontWeight: '700',
  },
  errorBanner: {
    backgroundColor: '#ffebee',
    borderWidth: 1,
    borderColor: '#ffcdd2',
    borderRadius: 8,
    padding: 12,
    marginBottom: 14,
  },
  errorTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: '#c62828',
    marginBottom: 2,
  },
  errorMessage: {
    fontSize: 12,
    color: '#b71c1c',
  },
  stepInfo: {
    fontSize: 14,
    color: '#555',
    textAlign: 'center',
    marginBottom: 8,
  },
  devCodeBadge: {
    backgroundColor: '#e3f2fd',
    paddingVertical: 4,
    paddingHorizontal: 10,
    borderRadius: 6,
    alignSelf: 'center',
    marginBottom: 8,
  },
  devCodeText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#1565c0',
  },
  actionButton: {
    marginTop: 12,
  },
  otpActions: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 14,
    paddingHorizontal: 4,
  },
  resendText: {
    fontSize: 13,
    color: '#2d6a4f',
    fontWeight: '600',
  },
  resendDisabled: {
    color: '#999',
    fontWeight: 'normal',
  },
  changePhoneText: {
    fontSize: 13,
    color: '#666',
    textDecorationLine: 'underline',
  },
  devSection: {
    marginTop: 24,
    paddingTop: 16,
    borderTopWidth: 1,
    borderTopColor: '#eee',
  },
  devSectionTitle: {
    fontSize: 11,
    color: '#888',
    marginBottom: 8,
    textAlign: 'center',
  },
  devButtonsRow: {
    flexDirection: 'row',
    gap: 8,
  },
  devButton: {
    flex: 1,
    backgroundColor: '#e8f5e9',
    paddingVertical: 8,
    borderRadius: 6,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#c8e6c9',
  },
  devButtonAlt: {
    backgroundColor: '#fff3e0',
    borderColor: '#ffe0b2',
  },
  devButtonText: {
    fontSize: 11,
    fontWeight: '600',
    color: '#2d6a4f',
  },
  devButtonTextAlt: {
    fontSize: 11,
    fontWeight: '600',
    color: '#e65100',
  },
});

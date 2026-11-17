import { router } from 'expo-router';
import { SharedLoginScreen } from '@farm/ui';

export default function CustomerLoginScreen() {
  return (
    <SharedLoginScreen
      appName="Farm Customer"
      appSubtitle="Fresh produce & plots directly from farmers"
      requiredRole="customer"
      logoEmoji="🥕"
      onSuccess={() => {
        router.replace('/');
      }}
    />
  );
}

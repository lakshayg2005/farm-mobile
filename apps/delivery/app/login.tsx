import { router } from 'expo-router';
import { SharedLoginScreen } from '@farm/ui';

export default function DeliveryLoginScreen() {
  return (
    <SharedLoginScreen
      appName="Farm Delivery"
      appSubtitle="Pooled routes & contactless farm dispatch"
      requiredRole="delivery"
      logoEmoji="🚚"
      onSuccess={() => {
        router.replace('/');
      }}
    />
  );
}

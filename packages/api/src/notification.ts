import type createClient from 'openapi-fetch';
import { createServiceClient } from './client';
import type { components, paths } from './generated/notification';

/**
 * Client for the notification service, typed with its contract
 * (contracts/openapi/notification.json). Paths are service-relative (`/notifications`), the base
 * URL adds the gateway's `/notification` prefix. Regenerate the types with:
 *   pnpm --filter @farm/api exec openapi-typescript <path to notification.json> -o src/generated/notification.d.ts
 */
export type NotificationClient = ReturnType<typeof createClient<paths>>;
export const notificationClient: NotificationClient = createServiceClient<paths>('notification');

type Schemas = components['schemas'];
export type AppNotification = Schemas['NotificationOut'];
export type NotificationPage = Schemas['NotificationPage'];
export type NotificationType = Schemas['NotificationType'];

import type { NotificationChannel } from '../api/types'

/** The choices on the notifications page, in the order shown. */
export const CHANNEL_OPTIONS: { value: NotificationChannel; label: string }[] = [
  { value: 'PUSH', label: 'Push' },
  { value: 'SMS', label: 'SMS' },
  { value: 'EMAIL', label: 'Email' },
]

/** How one notification reached the driver (the channel notification-service's Factory used). */
export function sentByText(channel: NotificationChannel): string {
  if (channel === 'SMS') return 'by SMS'
  if (channel === 'EMAIL') return 'by email'
  return 'as a push notification'
}

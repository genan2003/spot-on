import { Client } from '@stomp/stompjs'
import { USE_MOCKS } from './client'
import { DRIVER_ID } from './driver'
import type { ActivityItem, Notification, SpotStatusChanged } from './types'

/**
 * Subscribes to spot.status-changed events. parking-service relays them from RabbitMQ
 * to the STOMP topic /topic/spot-status; the gateway proxies the /ws endpoint.
 */
export function subscribeToSpotStatus(onChange: (event: SpotStatusChanged) => void): () => void {
  if (USE_MOCKS) {
    let unsubscribe = () => {}
    let cancelled = false
    import('./mock').then(({ onMockSpotChange }) => {
      if (!cancelled) unsubscribe = onMockSpotChange(onChange)
    })
    return () => {
      cancelled = true
      unsubscribe()
    }
  }

  return subscribeToTopic('/topic/spot-status', onChange)
}

/**
 * Subscribes to the admin's activity feed: every reservation, session and payment event, relayed by parking-service
 * from RabbitMQ to the STOMP topic /topic/activity.
 */
export function subscribeToActivity(onItem: (item: ActivityItem) => void): () => void {
  if (USE_MOCKS) {
    let unsubscribe = () => {}
    let cancelled = false
    import('./mock').then(({ onMockActivity }) => {
      if (!cancelled) unsubscribe = onMockActivity(onItem)
    })
    return () => {
      cancelled = true
      unsubscribe()
    }
  }
  return subscribeToTopic('/topic/activity', onItem)
}

/** One STOMP connection to parking-service's /ws (through the gateway) for one topic; reconnects after a drop. */
function subscribeToTopic<T>(topic: string, onMessage: (body: T) => void): () => void {
  const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
  const client = new Client({
    brokerURL: `${protocol}://${location.host}/ws`,
    reconnectDelay: 5000,
    onConnect: () => {
      client.subscribe(topic, (message) => onMessage(JSON.parse(message.body)))
    },
  })
  client.activate()
  return () => void client.deactivate()
}

/**
 * Live notifications for the demo driver: Server-Sent Events from notification-service
 * (/api/v1/notifications/stream). EventSource reconnects by itself after a drop.
 */
export function subscribeToNotifications(onNotification: (notification: Notification) => void, onReconnect: () => void): () => void {
  if (USE_MOCKS) {
    let unsubscribe = () => {}
    let cancelled = false
    import('./mock').then(({ onMockNotification }) => {
      if (!cancelled) unsubscribe = onMockNotification(onNotification)
    })
    return () => {
      cancelled = true
      unsubscribe()
    }
  }

  const source = new EventSource(`/api/v1/notifications/stream?driverId=${encodeURIComponent(DRIVER_ID)}`)
  source.addEventListener('notification', (event) => onNotification(JSON.parse((event as MessageEvent).data)))
  // After a drop, anything sent meanwhile never reached this stream: load the list again.
  let opened = false
  source.addEventListener('open', () => {
    if (opened) onReconnect()
    opened = true
  })
  return () => source.close()
}

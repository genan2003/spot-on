import { Client } from '@stomp/stompjs'
import { USE_MOCKS } from './client'
import type { SpotStatusChanged } from './types'

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

  const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
  const client = new Client({
    brokerURL: `${protocol}://${location.host}/ws`,
    reconnectDelay: 5000,
    onConnect: () => {
      client.subscribe('/topic/spot-status', (message) => onChange(JSON.parse(message.body)))
    },
  })
  client.activate()
  return () => void client.deactivate()
}

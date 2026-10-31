import type { ActivityItem } from '../api/types'
import { lei } from './format'

/** "Driver 2" for the demo drivers, "Another driver" for the demo data's made-up ones, the id otherwise. */
export function driverLabel(id?: string): string {
  if (!id || id === 'someone-else') return 'Another driver'
  const demo = /^driver-(\d+)$/.exec(id)
  return demo ? `Driver ${demo[1]}` : id
}

/** One line per event, from the admin's point of view, in the past tense. */
export function activityText(item: ActivityItem): string {
  const who = driverLabel(item.driverId)
  const spot = item.spotCode ?? 'a spot'
  const amount = item.amount !== undefined && item.amount !== null ? lei(item.amount) : undefined
  switch (item.type) {
    case 'reservation.created':
      return `${who} reserved ${spot}`
    case 'reservation.changed':
      return item.previousSpotCode ? `${who} moved a reservation from ${item.previousSpotCode} to ${spot}` : `${who} extended the hold on ${spot}`
    case 'reservation.expiring':
      return `${who} was reminded that the hold on ${spot} ends soon`
    case 'reservation.cancelled':
      return item.reason === 'SPOT_CLOSED'
        ? `${spot} was closed, so ${who}’s reservation was cancelled`
        : `${who} cancelled the reservation for ${spot}`
    case 'reservation.expired':
      return `${who}’s reservation for ${spot} expired`
    case 'session.started':
      return `${who} parked at ${spot}`
    case 'session.completed':
      return amount ? `${who} left ${spot}, ${amount}` : `${who} left ${spot}`
    case 'payment.completed':
      return `${who} paid ${amount ? `${amount} ` : ''}${item.method === 'WALLET' ? 'from the wallet' : 'by card'}`
    case 'payment.failed':
      return item.method === 'WALLET' ? `${who}’s wallet was short for ${amount}` : `${who}’s card was declined for ${amount}`
    default:
      return `${who}: ${item.type}`
  }
}

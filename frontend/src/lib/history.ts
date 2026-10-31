import type { History, ParkingSession, Payment, Reservation } from '../api/types'
import { duration, lei, timeOfDay } from './format'

/**
 * One line of the driver's history: a stay (an ended session) or a hold that ended without parking
 * (a cancelled or expired reservation). A reservation that became a session is shown as the stay only.
 */
export type HistoryEntry =
  | { kind: 'stay'; id: string; at: string; session: ParkingSession; payment?: Payment }
  | { kind: 'hold'; id: string; at: string; reservation: Reservation }

export interface HistoryDay {
  label: string
  entries: HistoryEntry[]
}

/** Ended stays and holds, newest first (by the time they ended). Running ones are on My parking. */
export function historyEntries({ reservations, sessions, payments }: History): HistoryEntry[] {
  const stays: HistoryEntry[] = sessions
    .filter((s) => s.status === 'COMPLETED' && s.endedAt)
    .map((session) => ({
      kind: 'stay',
      id: session.id,
      at: session.endedAt!,
      session,
      payment: payments.find((p) => p.sessionId === session.id),
    }))
  const holds: HistoryEntry[] = reservations
    .filter((r) => r.status === 'CANCELLED' || r.status === 'EXPIRED')
    .map((reservation) => ({ kind: 'hold', id: reservation.id, at: reservation.endedAt ?? reservation.expiresAt, reservation }))
  return [...stays, ...holds].sort((a, b) => b.at.localeCompare(a.at))
}

/** Groups entries under "Today", "Yesterday" or a date such as "Fri 30 October"; keeps their order. */
export function groupByDay(entries: HistoryEntry[], now = new Date()): HistoryDay[] {
  const days: HistoryDay[] = []
  for (const entry of entries) {
    const label = dayLabel(entry.at, now)
    const last = days.at(-1)
    if (last?.label === label) last.entries.push(entry)
    else days.push({ label, entries: [entry] })
  }
  return days
}

function dayLabel(iso: string, now: Date): string {
  const date = new Date(iso)
  const startOf = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
  const daysAgo = Math.round((startOf(now) - startOf(date)) / 86_400_000)
  if (daysAgo === 0) return 'Today'
  if (daysAgo === 1) return 'Yesterday'
  return date.toLocaleDateString('en-GB', {
    weekday: 'short',
    day: 'numeric',
    month: 'long',
    year: date.getFullYear() === now.getFullYear() ? undefined : 'numeric',
  })
}

/** "October so far: 4 stays, 32.00 lei", counting stays that ended this month; null when there are none. */
export function monthSummary(entries: HistoryEntry[], now = new Date()): string | null {
  const stays = entries.filter(
    (e): e is Extract<HistoryEntry, { kind: 'stay' }> =>
      e.kind === 'stay' && new Date(e.at).getMonth() === now.getMonth() && new Date(e.at).getFullYear() === now.getFullYear(),
  )
  if (stays.length === 0) return null
  const month = now.toLocaleDateString('en-GB', { month: 'long' })
  const total = stays.reduce((sum, e) => sum + e.session.cost, 0)
  return `${month} so far: ${stays.length} ${stays.length === 1 ? 'stay' : 'stays'}, ${lei(total)}`
}

/** "14:02 to 16:10, 2 h 8 min" */
export function stayTimes(session: ParkingSession): string {
  const length = new Date(session.endedAt!).getTime() - new Date(session.startedAt).getTime()
  return `${timeOfDay(session.startedAt)} to ${timeOfDay(session.endedAt!)}, ${duration(length)}`
}

const methodNames: Record<Payment['method'], string> = { CARD: 'card', WALLET: 'wallet' }

/** The payment outcome in a few words. The session's status is the one the driver sees on the receipt too. */
export function paymentText(session: ParkingSession, payment?: Payment): string {
  switch (session.paymentStatus) {
    case 'PENDING':
      return 'Processing payment…'
    case 'FAILED':
      return 'Payment declined'
    case 'COMPLETED': {
      if (session.cost === 0) return 'Free'
      if (!payment) return 'Paid'
      const paid = `Paid by ${methodNames[payment.method]}`
      return payment.attempts > 1 ? `${paid} after ${payment.attempts} tries` : paid
    }
    default:
      return ''
  }
}

/** Why a hold ended, same wording as the "Reservation ended" card on My parking. */
export function holdText(reservation: Reservation): string {
  const at = timeOfDay(reservation.endedAt ?? reservation.expiresAt)
  if (reservation.status === 'EXPIRED') return `Expired at ${at}, you didn’t arrive in time`
  if (reservation.cancelReason === 'SPOT_CLOSED') return `Cancelled at ${at}: the car park closed this spot`
  return `You cancelled at ${at}`
}

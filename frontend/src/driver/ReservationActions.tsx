import { Link } from 'react-router'
import { useCancelReservation, useExtendReservation, useNow, useStartSession } from '../api/queries'
import type { Reservation } from '../api/types'
import { button } from '../lib/ui'

/**
 * What a driver can do with their reservation, the same on "My parking" and in the spot sheet:
 * start the session, extend the hold once, move it to another spot, or cancel it.
 */
export function ReservationActions({
  reservation,
  onStarted,
  showMove = false,
}: {
  reservation: Reservation
  onStarted?: () => void
  /** "Move to another spot" opens the car park's plan; in the spot sheet the plan is already open. */
  showMove?: boolean
}) {
  const now = useNow()
  const start = useStartSession()
  const extend = useExtendReservation()
  const cancel = useCancelReservation()
  const expired = new Date(reservation.expiresAt).getTime() <= now
  // An unextended reservation's length is the car park's hold time, which an extension adds again.
  const holdMinutes = Math.round((new Date(reservation.expiresAt).getTime() - new Date(reservation.createdAt).getTime()) / 60_000)

  return (
    <>
      <div className="flex flex-wrap items-center gap-3">
        <button
          type="button"
          className={button.primary}
          disabled={start.isPending || expired}
          onClick={() => start.mutate({ spotId: reservation.spotId, reservationId: reservation.id }, { onSuccess: onStarted })}
        >
          {start.isPending ? 'Starting session…' : 'Start session'}
        </button>
        {!reservation.extended && (
          <button type="button" className={button.secondary} disabled={extend.isPending || expired} onClick={() => extend.mutate(reservation.id)}>
            {extend.isPending ? 'Extending…' : `Extend by ${holdMinutes} min`}
          </button>
        )}
      </div>
      {reservation.extended && <p className="mt-3 max-w-prose text-ink-muted">You’ve used your one extension.</p>}
      <div className="mt-3 flex flex-wrap items-center gap-x-6">
        {showMove && (
          <Link to={`/locations/${reservation.locationId}`} className={button.quiet}>
            Move to another spot
          </Link>
        )}
        <button type="button" className={button.quiet} disabled={cancel.isPending} onClick={() => cancel.mutate(reservation.id)}>
          {cancel.isPending ? 'Cancelling…' : 'Cancel reservation'}
        </button>
      </div>
      <ErrorLine error={start.error ?? extend.error ?? cancel.error} />
    </>
  )
}

export function ErrorLine({ error }: { error: Error | null }) {
  if (!error) return null
  return (
    <p role="alert" className="mt-3 border-l-4 border-occupied pl-3 font-semibold">
      {error.message}
    </p>
  )
}

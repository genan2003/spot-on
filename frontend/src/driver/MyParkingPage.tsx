import { useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { Link } from 'react-router'
import { keys, useCurrentParking, useEndSession, useNow } from '../api/queries'
import type { CurrentParking, ParkingSession, Reservation } from '../api/types'
import { Plate } from '../components/Plate'
import { QueryState } from '../components/QueryState'
import { StatusLabel, StatusMark } from '../components/status'
import { clock, duration, lei, timeOfDay } from '../lib/format'
import { dismiss, useDismissed } from '../lib/dismissed'
import { button } from '../lib/ui'
import { PaymentOutcome, PayWith } from './Payment'
import { DriverSwitch } from './DriverSwitch'
import { ErrorLine, ReservationActions } from './ReservationActions'

export function MyParkingPage() {
  const current = useCurrentParking()
  return (
    <div className="px-4 pt-2">
      <h1 className="text-2xl font-extrabold tracking-tight">My parking</h1>
      <QueryState query={current} loading="Loading your parking…">
        {(data) => <Current data={data} />}
      </QueryState>
      <Link to="/history" className="mt-8 inline-flex min-h-11 items-center font-semibold text-sign hover:underline">
        Your parking history
      </Link>
      <DriverSwitch />
    </div>
  )
}

/**
 * What runs now, or else the last thing that ended (the server picks the newer of the last receipt and the
 * last cancelled or expired reservation). "Done" hides an ended reservation or a paid receipt.
 */
function Current({ data }: { data: CurrentParking }) {
  const ended = data.lastCompleted ?? data.lastEndedReservation
  const dismissed = useDismissed(ended?.id)
  if (data.reservation) return <ReservationPanel reservation={data.reservation} />
  if (data.session) return <SessionPanel session={data.session} />
  if (data.lastCompleted && !dismissed) return <Receipt session={data.lastCompleted} />
  if (data.lastEndedReservation && !dismissed) return <EndedReservation reservation={data.lastEndedReservation} />
  return (
    <div className="py-8">
      <p>You have no reservation or session running.</p>
      <Link to="/" className={`${button.primary} mt-4`}>
        Find a spot
      </Link>
    </div>
  )
}

/** A reservation that ended without parking: say so plainly, and that nothing was charged. */
function EndedReservation({ reservation }: { reservation: Reservation }) {
  const at = timeOfDay(reservation.endedAt ?? reservation.expiresAt)
  const [title, text] =
    reservation.status === 'EXPIRED'
      ? ['Reservation expired', `Your hold on ${reservation.spotCode} at ${reservation.locationName} ended at ${at}.`]
      : reservation.cancelReason === 'SPOT_CLOSED'
        ? ['Reservation cancelled', `At ${at} ${reservation.locationName} closed ${reservation.spotCode}, so your reservation was cancelled.`]
        : ['Reservation cancelled', `At ${at} you cancelled your reservation for ${reservation.spotCode} at ${reservation.locationName}.`]
  return (
    <section className="mt-6" aria-labelledby="ended-title">
      <h2 id="ended-title" className="text-lg font-semibold">
        {title}
      </h2>
      <p className="mt-2 max-w-prose">{text}</p>
      <p className="mt-1 max-w-prose text-ink-muted">You were not charged.</p>
      <div className="mt-6 flex flex-wrap items-center gap-4">
        <Link to={`/locations/${reservation.locationId}`} className={button.primary}>
          Find a spot here
        </Link>
        <button type="button" className={button.quiet} onClick={() => dismiss(reservation.id)}>
          Done
        </button>
      </div>
    </section>
  )
}

function ReservationPanel({ reservation }: { reservation: Reservation }) {
  const now = useNow()
  const queryClient = useQueryClient()
  const left = new Date(reservation.expiresAt).getTime() - now
  const expired = left <= 0

  // The server expires the reservation; once the countdown hits zero, ask for the new state.
  useEffect(() => {
    if (expired) queryClient.invalidateQueries({ queryKey: keys.current })
  }, [expired, queryClient])

  return (
    <section className="mt-6" aria-labelledby="reservation-title">
      <StatusLabel status="RESERVED" />
      <h2 id="reservation-title" className="mt-2 text-3xl font-extrabold tracking-tight">
        {reservation.spotCode}
      </h2>
      <p className="text-ink-muted">{reservation.locationName}</p>
      <p className="mt-6 text-ink-muted">Held for you for</p>
      <p className="tabular text-3xl font-extrabold" role="timer">
        {expired ? '0:00' : clock(left)}
      </p>
      <p className="text-ink-muted">Until {timeOfDay(reservation.expiresAt)}. After that the spot is released.</p>
      <div className="mt-6">
        <ReservationActions reservation={reservation} showMove />
      </div>
    </section>
  )
}

function SessionPanel({ session }: { session: ParkingSession }) {
  const now = useNow()
  const end = useEndSession()
  return (
    <section className="mt-6" aria-labelledby="session-title">
      <p className="flex items-center gap-2">
        <StatusMark status="OCCUPIED" /> Parked
      </p>
      <h2 id="session-title" className="mt-2 text-3xl font-extrabold tracking-tight">
        {session.spotCode}
      </h2>
      <p className="text-ink-muted">{session.locationName}</p>
      {session.licencePlate && (
        <p className="mt-2">
          <Plate plate={session.licencePlate} />
        </p>
      )}
      <p className="mt-6 text-ink-muted">Parked for</p>
      <p className="tabular text-3xl font-extrabold" role="timer">
        {clock(now - new Date(session.startedAt).getTime())}
      </p>
      <p className="tabular text-ink-muted">Since {timeOfDay(session.startedAt)}</p>
      <p className="tabular mt-4 text-lg">
        So far: <strong>{lei(session.cost)}</strong>
      </p>
      <PayWith costSoFar={session.cost} />
      <button type="button" className={`${button.primary} mt-6`} disabled={end.isPending} onClick={() => end.mutate(session.id)}>
        {end.isPending ? 'Ending session…' : 'End session'}
      </button>
      <ErrorLine error={end.error} />
    </section>
  )
}

function Receipt({ session }: { session: ParkingSession }) {
  const rows: [string, string][] = [
    ['Spot', session.spotCode],
    ['Car park', session.locationName],
    ...(session.licencePlate ? [['Car', session.licencePlate] as [string, string]] : []),
    ['Entered', timeOfDay(session.startedAt)],
    ['Left', timeOfDay(session.endedAt!)],
    ['Duration', duration(new Date(session.endedAt!).getTime() - new Date(session.startedAt).getTime())],
  ]
  return (
    <section className="mt-6" aria-labelledby="receipt-title">
      <h2 id="receipt-title" className="text-lg font-semibold">
        Session ended
      </h2>
      <dl className="tabular mt-3 divide-y divide-hairline border-y border-hairline">
        {rows.map(([label, value]) => (
          <div key={label} className="flex justify-between py-2">
            <dt className="text-ink-muted">{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
        <div className="flex justify-between py-3 text-lg font-bold">
          <dt>Cost</dt>
          <dd>{lei(session.cost)}</dd>
        </div>
      </dl>
      <div className="mt-4" role="status">
        <PaymentOutcome session={session} />
      </div>
      <div className="mt-8 flex flex-wrap items-center gap-4">
        <Link to="/" className={button.secondary}>
          Find a spot
        </Link>
        {/* An unpaid receipt stays until it is paid. */}
        {session.paymentStatus === 'COMPLETED' && (
          <button type="button" className={button.quiet} onClick={() => dismiss(session.id)}>
            Done
          </button>
        )}
      </div>
    </section>
  )
}

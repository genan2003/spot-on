import { useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { Link } from 'react-router'
import { keys, useCurrentParking, useEndSession, useNow, useRetryPayment } from '../api/queries'
import type { ParkingSession, Reservation } from '../api/types'
import { CheckIcon } from '../components/icons'
import { QueryState } from '../components/QueryState'
import { StatusLabel, StatusMark } from '../components/status'
import { clock, duration, lei, timeOfDay } from '../lib/format'
import { button } from '../lib/ui'
import { ErrorLine, ReservationActions } from './ReservationActions'

export function MyParkingPage() {
  const current = useCurrentParking()
  return (
    <div className="px-4 pt-2">
      <h1 className="text-2xl font-extrabold tracking-tight">My parking</h1>
      <QueryState query={current} loading="Loading your parking…">
        {(data) =>
          data.reservation ? (
            <ReservationPanel reservation={data.reservation} />
          ) : data.session ? (
            <SessionPanel session={data.session} />
          ) : data.lastCompleted ? (
            <Receipt session={data.lastCompleted} />
          ) : (
            <div className="py-8">
              <p>You have no reservation or session running.</p>
              <Link to="/" className={`${button.primary} mt-4`}>
                Find a spot
              </Link>
            </div>
          )
        }
      </QueryState>
    </div>
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
      <p className="mt-6 text-ink-muted">Parked for</p>
      <p className="tabular text-3xl font-extrabold" role="timer">
        {clock(now - new Date(session.startedAt).getTime())}
      </p>
      <p className="tabular text-ink-muted">Since {timeOfDay(session.startedAt)}</p>
      <p className="tabular mt-4 text-lg">
        So far: <strong>{lei(session.cost)}</strong>
      </p>
      <button type="button" className={`${button.primary} mt-6`} disabled={end.isPending} onClick={() => end.mutate(session.id)}>
        {end.isPending ? 'Ending session…' : 'End session'}
      </button>
      <ErrorLine error={end.error} />
    </section>
  )
}

function Receipt({ session }: { session: ParkingSession }) {
  const retry = useRetryPayment()
  const rows: [string, string][] = [
    ['Spot', session.spotCode],
    ['Car park', session.locationName],
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
        {session.paymentStatus === 'PENDING' && <p>Processing payment…</p>}
        {session.paymentStatus === 'COMPLETED' && (
          <p className="flex items-center gap-2 font-semibold">
            <CheckIcon className="size-5 text-free" /> Paid
          </p>
        )}
        {session.paymentStatus === 'FAILED' && (
          <>
            <p className="max-w-prose font-semibold">The payment didn’t go through. Your card was declined. Try again, or use another card.</p>
            <button
              type="button"
              className={`${button.primary} mt-3`}
              disabled={retry.isPending}
              onClick={() => retry.mutate(session.paymentId!)}
            >
              Try payment again
            </button>
            <ErrorLine error={retry.error} />
          </>
        )}
      </div>
      <Link to="/" className={`${button.secondary} mt-8`}>
        Find a spot
      </Link>
    </section>
  )
}

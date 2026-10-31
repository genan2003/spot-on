import { useEffect, useRef } from 'react'
import { Link, useNavigate } from 'react-router'
import { DRIVER_ID } from '../api/driver'
import { ApiError } from '../api/errors'
import { useCurrentParking, useLocation, useMoveReservation, useNow, useReserveSpot } from '../api/queries'
import type { Spot } from '../api/types'
import { StatusLabel } from '../components/status'
import { clock, describeSurcharge, levelName, spotTypeNames, timeOfDay } from '../lib/format'
import { button } from '../lib/ui'
import { ReservationActions } from './ReservationActions'
import { StartSession } from './StartSession'

/** Details and actions for the selected spot, as a sheet over the bottom of the screen. */
export function SpotSheet({ spot, onClose }: { spot: Spot; onClose: () => void }) {
  const heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => heading.current?.focus(), [spot.id])
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const features = [spotTypeNames[spot.type], spot.evCharging && 'EV charging', spot.accessible && 'Accessible'].filter(Boolean)
  // The car park's pricing can charge more (or less) for this kind of bay.
  const location = useLocation(spot.locationId)
  const surcharge = location.data ? describeSurcharge(location.data.pricing, spot) : ''

  return (
    <section
      aria-labelledby="spot-sheet-title"
      className="fixed inset-x-0 bottom-0 z-[1001] mx-auto max-w-xl rounded-t-sheet bg-surface px-4 pt-4 pb-6 shadow-sheet"
    >
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 id="spot-sheet-title" ref={heading} tabIndex={-1} className="text-3xl font-extrabold tracking-tight">
            {spot.code}
          </h2>
          <p className="text-ink-muted">
            {levelName(spot.level)}, zone {spot.zone}
          </p>
        </div>
        <button type="button" onClick={onClose} className={button.small}>
          Close
        </button>
      </div>
      <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-base">
        <StatusLabel status={spot.status} />
        <span>{features.join(', ')}</span>
      </div>
      {surcharge && <p className="mt-1 text-ink-muted">{surcharge}</p>}
      <SpotActions key={spot.id} spot={spot} />
    </section>
  )
}

function SpotActions({ spot }: { spot: Spot }) {
  const navigate = useNavigate()
  const current = useCurrentParking()
  const reserve = useReserveSpot()
  const move = useMoveReservation()
  const now = useNow()

  const error = [reserve.error, move.error].find(Boolean)
  const errorText =
    error instanceof ApiError && error.status === 409
      ? `Someone else took ${spot.code} a moment ago. Pick another free spot.`
      : error?.message

  const reservation = current.data?.reservation
  const session = current.data?.session
  const mine = spot.heldBy === DRIVER_ID

  let body
  if (current.isPending) {
    body = <p className="text-ink-muted">Checking your reservations…</p>
  } else if (spot.status === 'RESERVED' && mine && reservation) {
    body = (
      <div className="animate-confirm -mx-4 px-4">
        <p className="tabular text-lg">
          Held for you for <strong>{clock(new Date(reservation.expiresAt).getTime() - now)}</strong>
        </p>
        <div className="mt-4">
          <ReservationActions reservation={reservation} onStarted={() => navigate('/parking')} />
        </div>
      </div>
    )
  } else if (spot.status === 'OCCUPIED' && mine) {
    body = (
      <p>
        You’re parked here. <Link to="/parking" className="font-semibold text-sign hover:underline">Go to my parking</Link>
      </p>
    )
  } else if (spot.status === 'AVAILABLE' && reservation) {
    body = (
      <>
        <p className="max-w-prose">
          You have {reservation.spotCode} reserved. Move your reservation here instead; it stays held until{' '}
          {timeOfDay(reservation.expiresAt)}.
        </p>
        <div className="mt-4 flex flex-wrap items-center gap-4">
          <button
            type="button"
            className={button.primary}
            disabled={move.isPending}
            onClick={() => move.mutate({ id: reservation.id, spotId: spot.id })}
          >
            {move.isPending ? 'Moving reservation…' : 'Move reservation here'}
          </button>
          <Link to="/parking" className={button.quiet}>
            Go to my parking
          </Link>
        </div>
      </>
    )
  } else if (spot.status === 'AVAILABLE' && session) {
    body = (
      <p className="max-w-prose">
        You’re parked at {session.spotCode}. End that session before reserving another spot.{' '}
        <Link to="/parking" className="font-semibold text-sign hover:underline">Go to my parking</Link>
      </p>
    )
  } else if (spot.status === 'AVAILABLE') {
    body = (
      <>
        <p className="max-w-prose text-ink-muted">Reserve to hold it for 15 minutes, or start a session if you’re already here.</p>
        <div className="mt-4 flex flex-wrap gap-3">
          <button type="button" className={button.primary} disabled={reserve.isPending} onClick={() => reserve.mutate(spot.id)}>
            {reserve.isPending ? 'Reserving…' : 'Reserve spot'}
          </button>
          <StartSession spotId={spot.id} primary={false} onStarted={() => navigate('/parking')} />
        </div>
      </>
    )
  } else if (spot.status === 'OUT_OF_SERVICE') {
    body = <p>This spot is closed for maintenance. Pick another free spot.</p>
  } else {
    body = <p>Someone else has this spot. Free spots have a check mark.</p>
  }

  return (
    <div className="mt-4">
      {body}
      {errorText && (
        <p role="alert" className="mt-3 border-l-4 border-occupied pl-3 font-semibold">
          {errorText}
        </p>
      )}
    </div>
  )
}

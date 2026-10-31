import { Link } from 'react-router'
import { useHistory, useRetryPayment } from '../api/queries'
import { QueryState } from '../components/QueryState'
import { StatusMark } from '../components/status'
import { groupByDay, historyEntries, holdText, monthSummary, paymentText, stayTimes, type HistoryEntry } from '../lib/history'
import { lei } from '../lib/format'
import { button } from '../lib/ui'
import { ErrorLine } from './ReservationActions'

/** Past stays and holds, newest first, grouped by day. What runs now is on My parking. */
export function HistoryPage() {
  const history = useHistory()
  return (
    <div className="px-4 pt-2">
      <h1 className="text-2xl font-extrabold tracking-tight">Your parking history</h1>
      <QueryState query={history} loading="Loading your parking history…">
        {(data) => {
          const entries = historyEntries(data)
          if (entries.length === 0) {
            return (
              <div className="py-8">
                <p className="max-w-prose">No parking yet. Your stays and reservations will show up here.</p>
                <Link to="/" className={`${button.primary} mt-4`}>
                  Find a spot
                </Link>
              </div>
            )
          }
          const summary = monthSummary(entries)
          return (
            <>
              {summary && <p className="tabular mt-1 text-ink-muted">{summary}</p>}
              {groupByDay(entries).map((day) => (
                <section key={day.label} className="mt-6" aria-labelledby={`day-${day.label}`}>
                  <h2 id={`day-${day.label}`} className="text-base font-semibold">
                    {day.label}
                  </h2>
                  <ul className="mt-2 divide-y divide-hairline border-y border-hairline">
                    {day.entries.map((entry) => (
                      <li key={entry.id} className="flex gap-3 py-3">
                        {entry.kind === 'stay' ? <StayRow entry={entry} /> : <HoldRow entry={entry} />}
                      </li>
                    ))}
                  </ul>
                </section>
              ))}
            </>
          )
        }}
      </QueryState>
    </div>
  )
}

function StayRow({ entry }: { entry: Extract<HistoryEntry, { kind: 'stay' }> }) {
  const { session, payment } = entry
  const retry = useRetryPayment()
  return (
    <>
      <StatusMark status="OCCUPIED" className="mt-0.5" />
      <div className="min-w-0 flex-1">
        <div className="flex justify-between gap-3 font-semibold">
          <p>
            <span className="sr-only">Parked at </span>
            {session.spotCode}, {session.locationName}
          </p>
          <p className="tabular shrink-0">{lei(session.cost)}</p>
        </div>
        <div className="flex flex-wrap justify-between gap-x-3 text-ink-muted">
          <p className="tabular">{stayTimes(session)}</p>
          {/* ml-auto keeps the payment result in the right-hand column when it wraps under the times. */}
          <p className={`ml-auto text-right ${session.paymentStatus === 'FAILED' ? 'font-semibold text-ink' : ''}`}>
            {paymentText(session, payment)}
          </p>
        </div>
        {session.paymentStatus === 'FAILED' && session.paymentId && (
          <>
            <button
              type="button"
              className={`${button.small} mt-2`}
              disabled={retry.isPending}
              onClick={() => retry.mutate(session.paymentId!)}
            >
              Try payment again
            </button>
            <ErrorLine error={retry.error} />
          </>
        )}
      </div>
    </>
  )
}

function HoldRow({ entry }: { entry: Extract<HistoryEntry, { kind: 'hold' }> }) {
  const { reservation } = entry
  return (
    <>
      <StatusMark status="RESERVED" className="mt-0.5" />
      <div className="min-w-0 flex-1">
        <div className="flex justify-between gap-3 font-semibold">
          <p>
            <span className="sr-only">Reserved </span>
            {reservation.spotCode}, {reservation.locationName}
          </p>
          <p className="shrink-0">Not charged</p>
        </div>
        <p className="tabular text-ink-muted">{holdText(reservation)}</p>
      </div>
    </>
  )
}

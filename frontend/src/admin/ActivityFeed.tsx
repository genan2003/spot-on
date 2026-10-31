import { useActivity } from '../api/queries'
import { QueryState } from '../components/QueryState'
import { activityText } from '../lib/activity'
import { timeOfDay } from '../lib/format'

/**
 * What is happening in every car park, as it happens: reservations, sessions and payments, newest first. The events
 * travel through RabbitMQ; parking-service keeps the newest 50 and pushes new ones over STOMP.
 */
export function ActivityFeed() {
  const activity = useActivity()
  return (
    <section aria-labelledby="activity-title" className="hidden lg:block">
      <h2 id="activity-title" className="text-sm font-semibold text-ink-muted">
        Activity
      </h2>
      <QueryState query={activity} loading="Loading activity…">
        {(items) =>
          items.length === 0 ? (
            <p className="mt-2 text-sm text-ink-muted">Nothing yet. Reservations, sessions and payments show up here as they happen.</p>
          ) : (
            <ol className="mt-2 max-h-[45vh] divide-y divide-hairline overflow-y-auto border-y border-hairline text-sm">
              {items.map((item) => (
                <li key={item.eventId} className="py-2">
                  <p>{activityText(item)}</p>
                  <p className="tabular text-xs text-ink-muted">
                    {item.locationName ? `${item.locationName}, ${timeOfDay(item.occurredAt)}` : timeOfDay(item.occurredAt)}
                  </p>
                </li>
              ))}
            </ol>
          )
        }
      </QueryState>
    </section>
  )
}

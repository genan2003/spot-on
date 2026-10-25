import { useEffect } from 'react'
import { Link } from 'react-router'
import { useNotifications } from '../api/queries'
import { QueryState } from '../components/QueryState'
import { relativeTime } from '../lib/format'
import { markSeen } from '../lib/seen'
import { button } from '../lib/ui'

export function NotificationsPage() {
  const notifications = useNotifications()
  const count = notifications.data?.length
  useEffect(() => {
    if (count !== undefined) markSeen(count)
  }, [count])

  return (
    <div className="px-4 pt-2">
      <h1 className="text-2xl font-extrabold tracking-tight">Notifications</h1>
      <QueryState query={notifications} loading="Loading notifications…">
        {(list) =>
          list.length === 0 ? (
            <div className="py-8">
              <p className="max-w-prose">Nothing yet. Reservations, expired holds and payments show up here.</p>
              <Link to="/" className={`${button.primary} mt-4`}>
                Find a spot
              </Link>
            </div>
          ) : (
            <ul className="mt-4 divide-y divide-hairline border-y border-hairline">
              {list.map((n) => (
                <li key={n.id} className="py-3">
                  <p className="max-w-prose">{n.message}</p>
                  <p className="text-sm text-ink-muted">{relativeTime(n.sentAt)}</p>
                </li>
              ))}
            </ul>
          )
        }
      </QueryState>
    </div>
  )
}

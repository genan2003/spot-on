import { useEffect } from 'react'
import { Link } from 'react-router'
import { useChooseNotificationChannel, useNotificationPreference, useNotifications } from '../api/queries'
import { QueryState } from '../components/QueryState'
import { Segmented } from '../components/Segmented'
import { relativeTime } from '../lib/format'
import { CHANNEL_OPTIONS, sentByText } from '../lib/notification'
import { markSeen } from '../lib/seen'
import { button } from '../lib/ui'
import { ErrorLine } from './ReservationActions'

export function NotificationsPage() {
  const notifications = useNotifications()
  const count = notifications.data?.length
  useEffect(() => {
    if (count !== undefined) markSeen(count)
  }, [count])

  return (
    <div className="px-4 pt-2">
      <h1 className="text-2xl font-extrabold tracking-tight">Notifications</h1>
      <ChannelChoice />
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
                  <p className="text-sm text-ink-muted">
                    {relativeTime(n.sentAt)}, {sentByText(n.channelType)}
                  </p>
                </li>
              ))}
            </ul>
          )
        }
      </QueryState>
    </div>
  )
}

/**
 * "Send notifications by": the driver's channel in notification-service, which decides the channel its Factory
 * uses for the next notifications. Every notification is listed here whatever the channel.
 */
function ChannelChoice() {
  const preference = useNotificationPreference()
  const choose = useChooseNotificationChannel()

  if (preference.isPending) return <p className="mt-4 text-ink-muted">Loading your notification setting…</p>
  if (preference.isError) {
    return <p className="mt-4 max-w-prose text-ink-muted">Your notification setting couldn’t load. Notifications still show up here.</p>
  }

  // Show the new choice at once; the server confirms it a moment later.
  const channel = (choose.isPending && choose.variables) || preference.data.channel

  return (
    <div className="mt-4">
      <p className="font-semibold">Send notifications by</p>
      <div className="mt-2">
        <Segmented label="Send notifications by" options={CHANNEL_OPTIONS} value={channel} onChange={(value) => choose.mutate(value)} />
      </div>
      <p className="mt-2 max-w-prose text-ink-muted">New notifications go out this way. All of them are also listed on this page.</p>
      <ErrorLine error={choose.error} />
    </div>
  )
}

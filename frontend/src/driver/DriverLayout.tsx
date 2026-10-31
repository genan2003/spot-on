import { useEffect } from 'react'
import { Link, Outlet, useLocation as useRoute } from 'react-router'
import { DRIVER_ID, driverName } from '../api/driver'
import { useCurrentParking, useLiveNotifications, useLiveSpotStatus, useNotifications, useNow } from '../api/queries'
import { BellIcon, ParkingSign } from '../components/icons'
import { clock } from '../lib/format'
import { useSeenCount } from '../lib/seen'

export function DriverLayout() {
  useLiveSpotStatus()
  const latest = useLiveNotifications()
  const route = useRoute()
  const notifications = useNotifications()
  const seen = useSeenCount()
  const total = notifications.data?.length ?? 0
  // Demo data resets on reload while the seen count persists: fewer than seen means a fresh list.
  const unread = total < seen ? total : total - seen
  // Two tabs side by side can be different drivers; the tab title says which.
  useEffect(() => {
    document.title = `Spot On, ${driverName(DRIVER_ID).toLowerCase()}`
  }, [])

  return (
    <div className="mx-auto flex min-h-dvh max-w-xl flex-col">
      <header className="flex items-center justify-between px-4 py-3">
        <div className="flex items-baseline gap-2">
          <Link to="/" className="flex items-center gap-2 self-center text-lg font-extrabold tracking-tight">
            <ParkingSign size={28} />
            Spot On
          </Link>
          <Link to="/parking" className="text-sm text-ink-muted hover:underline" aria-label={`You are ${driverName(DRIVER_ID)}. Switch on My parking`}>
            {driverName(DRIVER_ID)}
          </Link>
        </div>
        <nav className="flex items-center gap-4">
          <Link to="/parking" className="text-base font-semibold hover:underline">
            My parking
          </Link>
          <Link to="/notifications" className="relative p-2" aria-label={unread ? `Notifications, ${unread} new` : 'Notifications'}>
            <BellIcon className="size-6" />
            {unread > 0 && (
              <span className="tabular absolute top-0.5 right-0 grid min-w-5 place-items-center rounded-sign bg-sign px-1 text-xs font-bold text-surface">
                {unread}
              </span>
            )}
          </Link>
        </nav>
      </header>
      {/* New notifications arrive live; screen readers hear them, sighted drivers see the bell count change. */}
      <p className="sr-only" aria-live="polite">
        {latest?.message}
      </p>
      <main className="flex-1 pb-24">
        <Outlet />
      </main>
      {route.pathname !== '/parking' && <CurrentParkingBar />}
    </div>
  )
}

/** Follows the driver around the app while a reservation or session is running. */
function CurrentParkingBar() {
  const { data } = useCurrentParking()
  const now = useNow()
  if (!data?.reservation && !data?.session) return null

  const text = data.reservation
    ? `${data.reservation.spotCode} is held for ${clock(new Date(data.reservation.expiresAt).getTime() - now)}`
    : `Parked at ${data.session!.spotCode} for ${clock(now - new Date(data.session!.startedAt).getTime())}`

  return (
    <div className="fixed inset-x-0 bottom-0 z-[1000] mx-auto max-w-xl bg-asphalt text-paint">
      <Link to="/parking" className="tabular flex min-h-14 items-center justify-between px-4 text-base font-semibold">
        <span role="status">{text}</span>
        <span className="underline underline-offset-4">Open</span>
      </Link>
    </div>
  )
}

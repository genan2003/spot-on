import { Link, Navigate, NavLink, Outlet } from 'react-router'
import { useLiveSpotStatus, useLocations } from '../api/queries'
import { ParkingSign } from '../components/icons'
import { QueryState } from '../components/QueryState'
import { ActivityFeed } from './ActivityFeed'

export function AdminLayout() {
  useLiveSpotStatus()
  const locations = useLocations()

  return (
    <div className="flex min-h-dvh flex-col lg:flex-row">
      <aside className="border-b border-hairline bg-surface lg:w-64 lg:shrink-0 lg:border-r lg:border-b-0">
        <div className="sticky top-0 flex flex-col gap-6 p-4">
          <Link to="/admin" className="flex items-center gap-2 text-lg font-extrabold tracking-tight">
            <ParkingSign size={28} />
            Spot On admin
          </Link>
          <nav aria-label="Car parks">
            <h2 className="text-sm font-semibold text-ink-muted">Car parks</h2>
            <QueryState query={locations} loading="Loading…">
              {(list) => (
                <ul className="mt-2 flex flex-wrap gap-1 lg:flex-col">
                  {list.map((l) => (
                    <li key={l.id}>
                      <NavLink
                        to={`/admin/locations/${l.id}`}
                        className={({ isActive }) =>
                          `flex min-h-10 items-center justify-between gap-3 rounded-sign px-3 text-sm ${
                            isActive ? 'bg-ink font-semibold text-surface' : 'hover:bg-concrete'
                          }`
                        }
                      >
                        <span>{l.name}</span>
                        <span className="tabular">{l.freeSpots} free</span>
                      </NavLink>
                    </li>
                  ))}
                </ul>
              )}
            </QueryState>
            <Link to="/admin/locations/new" className="mt-2 inline-flex min-h-10 items-center px-3 text-sm font-semibold text-sign hover:underline">
              Add car park
            </Link>
          </nav>
          <ActivityFeed />
          <Link to="/" className="text-sm text-ink-muted hover:underline">
            Open the driver app
          </Link>
        </div>
      </aside>
      <main className="min-w-0 flex-1">
        <Outlet />
      </main>
    </div>
  )
}

/** /admin opens the first car park; with none, invite the admin to add one. */
export function AdminHome() {
  const locations = useLocations()
  return (
    <QueryState query={locations} loading="Loading car parks…">
      {(list) =>
        list.length > 0 ? (
          <Navigate to={`/admin/locations/${list[0].id}`} replace />
        ) : (
          <div className="p-8">
            <h1 className="text-2xl font-extrabold tracking-tight">No car parks yet</h1>
            <p className="mt-2">Add your first car park, then set up its spots and pricing.</p>
            <Link to="/admin/locations/new" className="mt-4 inline-flex font-semibold text-sign hover:underline">
              Add car park
            </Link>
          </div>
        )
      }
    </QueryState>
  )
}

import { useDailyStats } from '../api/queries'
import type { DailyStats } from '../api/types'
import { QueryState } from '../components/QueryState'
import { lei } from '../lib/format'

/** Share of all spot-hours that were used: occupied spot-hours / (spots × hours). */
function occupancy(occupiedHours: number, spots: number, hours: number): number {
  return spots === 0 || hours === 0 ? 0 : Math.min(1, occupiedHours / (spots * hours))
}

function dayName(date: string, index: number, count: number): string {
  if (index === count - 1) return 'Today'
  if (index === count - 2) return 'Yesterday'
  // Noon, so the date doesn't shift a day in any time zone.
  return new Date(`${date}T12:00:00`).toLocaleDateString('en-GB', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  })
}

const percent = (share: number) => `${Math.round(share * 100)}%`

/**
 * Revenue and occupancy per day for one car park, newest first, with the week's total. The figures come from
 * reservation-service (sessions, their cost and payment outcome); the spot count from parking-service.
 */
export function DailyStatsTable({ locationId, spots }: { locationId: string; spots: number }) {
  const stats = useDailyStats(locationId)
  return (
    <section aria-labelledby="daily-stats-title" className="bg-surface">
      <QueryState query={stats} loading="Loading the last 7 days…">
        {(days) => <Table days={days} spots={spots} />}
      </QueryState>
    </section>
  )
}

function Table({ days, spots }: { days: DailyStats[]; spots: number }) {
  const total = days.reduce(
    (sum, d) => ({
      sessions: sum.sessions + d.sessions,
      occupiedHours: sum.occupiedHours + d.occupiedHours,
      hours: sum.hours + d.hours,
      revenue: sum.revenue + d.revenue,
      unpaid: sum.unpaid + d.unpaid,
    }),
    { sessions: 0, occupiedHours: 0, hours: 0, revenue: 0, unpaid: 0 },
  )
  const rows = days.map((d, i) => ({ ...d, name: dayName(d.date, i, days.length) })).reverse()

  return (
    <>
      {/* Above the scrolling table, so the heading and the note stay readable on a phone. */}
      <div className="p-4">
        <h2 id="daily-stats-title" className="text-lg font-semibold">
          Last 7 days
        </h2>
        <p className="max-w-prose text-sm text-ink-muted">
          Occupancy is the share of spot-hours used, out of today’s {spots} spots. Revenue counts paid sessions on the day they
          ended; today runs until now.
        </p>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm" aria-labelledby="daily-stats-title">
          <thead className="border-b-2 border-ink">
            <tr>
              <th scope="col" className="px-4 py-2 font-semibold">
                Day
              </th>
              <th scope="col" className="px-4 py-2 text-right font-semibold">
                Sessions
              </th>
              <th scope="col" className="px-4 py-2 font-semibold">
                Occupancy
              </th>
              <th scope="col" className="px-4 py-2 text-right font-semibold">
                Revenue
              </th>
              <th scope="col" className="px-4 py-2 text-right font-semibold">
                Not paid yet
              </th>
            </tr>
          </thead>
          <tbody className="tabular divide-y divide-hairline">
            {rows.map((d) => (
              <tr key={d.date}>
                <th scope="row" className="px-4 py-2 font-semibold whitespace-nowrap">
                  {d.name}
                </th>
                <td className="px-4 py-2 text-right">{d.sessions}</td>
                <td className="px-4 py-2">
                  <Share share={occupancy(d.occupiedHours, spots, d.hours)} />
                </td>
                <td className="px-4 py-2 text-right">{lei(d.revenue)}</td>
                <td className="px-4 py-2 text-right text-ink-muted">{d.unpaid > 0 ? lei(d.unpaid) : 'None'}</td>
              </tr>
            ))}
          </tbody>
          <tfoot className="tabular border-t-2 border-ink font-bold">
            <tr>
              <th scope="row" className="px-4 py-2">
                7 days
              </th>
              <td className="px-4 py-2 text-right">{total.sessions}</td>
              <td className="px-4 py-2">
                <Share share={occupancy(total.occupiedHours, spots, total.hours)} />
              </td>
              <td className="px-4 py-2 text-right">{lei(total.revenue)}</td>
              <td className="px-4 py-2 text-right">{total.unpaid > 0 ? lei(total.unpaid) : 'None'}</td>
            </tr>
          </tfoot>
        </table>
      </div>
    </>
  )
}

/** The percentage, with a thin bar that repeats it (one neutral ink: red and green mean spot status here). */
function Share({ share }: { share: number }) {
  return (
    <span className="flex items-center gap-3">
      <span className="w-10 text-right">{percent(share)}</span>
      <span className="h-1.5 w-24 bg-hairline" aria-hidden>
        <span className="block h-full rounded-r-bay bg-ink-muted" style={{ width: `${share * 100}%` }} />
      </span>
    </span>
  )
}

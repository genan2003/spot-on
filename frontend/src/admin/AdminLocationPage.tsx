import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { useAddSpots, useLocation, useSetSpotStatus, useSpots } from '../api/queries'
import type { Spot, SpotStatus, SpotType } from '../api/types'
import { LevelPicker } from '../components/LevelPicker'
import { Legend } from '../components/Legend'
import { QueryState } from '../components/QueryState'
import { SpotPlan } from '../components/SpotPlan'
import { StatusLabel, StatusMark } from '../components/status'
import { describePricing, levelName, levelsOf, spotTypeNames, statusNames } from '../lib/format'
import { button, field } from '../lib/ui'
import { SpotPropertiesForm } from './SpotPropertiesForm'

const statuses: SpotStatus[] = ['AVAILABLE', 'RESERVED', 'OCCUPIED', 'OUT_OF_SERVICE']

export function AdminLocationPage() {
  const { id = '' } = useParams()
  const location = useLocation(id)
  const spots = useSpots(id)
  const [level, setLevel] = useState<number>()
  const [selectedId, setSelectedId] = useState<string>()
  // Spots ticked in the table for editing several at once.
  const [checked, setChecked] = useState<Set<string>>(new Set())

  return (
    <div className="flex flex-col gap-6 p-4 lg:p-8">
      <QueryState query={location} loading="Loading car park…">
        {(loc) => (
          <header className="flex flex-wrap items-end justify-between gap-4">
            <div>
              <h1 className="text-2xl font-extrabold tracking-tight">{loc.name}</h1>
              <p className="text-ink-muted">{loc.address}</p>
            </div>
            <div className="flex items-center gap-3">
              <span>{describePricing(loc.pricing)}</span>
              <Link to={`/admin/locations/${id}/pricing`} className={button.small}>
                Edit pricing
              </Link>
            </div>
          </header>
        )}
      </QueryState>

      <QueryState query={spots} loading="Loading spots…">
        {(all) => {
          const levels = levelsOf(all)
          const current = level ?? levels[0] ?? 0
          const onLevel = all.filter((s) => s.level === current)
          const selected = all.find((s) => s.id === selectedId)
          const ticked = onLevel.filter((s) => checked.has(s.id))

          return (
            <>
              <Counts spots={all} />
              {levels.length > 1 && (
                <LevelPicker
                  levels={levels}
                  value={current}
                  onChange={(l) => {
                    setLevel(l)
                    setChecked(new Set())
                  }}
                />
              )}
              <div className="flex flex-col gap-6 xl:flex-row">
                <div className="min-w-0 flex-1">
                  {onLevel.length > 0 ? (
                    <>
                      <Legend />
                      <SpotPlan
                        spots={onLevel}
                        selectedId={selectedId}
                        onSelect={(s) => setSelectedId(s.id === selectedId ? undefined : s.id)}
                        label={`Spots on ${levelName(current).toLowerCase()}`}
                      />
                    </>
                  ) : (
                    <p className="border border-dashed border-edge p-8">No spots yet. Add the first row of spots with the form.</p>
                  )}
                </div>
                <aside className="xl:w-80 xl:shrink-0">
                  {selected ? (
                    <SpotPanel key={selected.id} spot={selected} onClose={() => setSelectedId(undefined)} />
                  ) : ticked.length > 0 ? (
                    <SeveralSpotsPanel locationId={id} spots={ticked} onClear={() => setChecked(new Set())} />
                  ) : (
                    <AddSpotsForm locationId={id} defaultLevel={current} />
                  )}
                </aside>
              </div>
              {onLevel.length > 0 && (
                <SpotTable spots={onLevel} selectedId={selectedId} onSelect={setSelectedId} checked={checked} onCheck={setChecked} />
              )}
            </>
          )
        }}
      </QueryState>
    </div>
  )
}

/** Live totals per status for the whole car park. */
function Counts({ spots }: { spots: Spot[] }) {
  return (
    <dl className="grid grid-cols-2 gap-x-10 gap-y-4 sm:flex sm:flex-wrap" aria-live="polite">
      {statuses.map((status) => (
        <div key={status} className="flex flex-col-reverse">
          <dt className="flex items-center gap-2 text-sm">
            <StatusMark status={status} />
            {statusNames[status]}
          </dt>
          <dd className="tabular text-3xl font-extrabold">{spots.filter((s) => s.status === status).length}</dd>
        </div>
      ))}
    </dl>
  )
}

function SpotPanel({ spot, onClose }: { spot: Spot; onClose: () => void }) {
  const setStatus = useSetSpotStatus(spot.locationId)
  const rows: [string, string][] = [
    ['Level', levelName(spot.level)],
    ['Zone', spot.zone],
  ]
  // A reserved spot has no car in it yet, so it can be closed; its reservation is cancelled.
  const canClose = spot.status === 'AVAILABLE' || spot.status === 'RESERVED'
  const canReopen = spot.status === 'OUT_OF_SERVICE'
  const [confirming, setConfirming] = useState(false)
  const close = () => setStatus.mutate({ spotId: spot.id, status: 'OUT_OF_SERVICE' }, { onSettled: () => setConfirming(false) })

  return (
    <section aria-labelledby="spot-panel-title" className="bg-surface p-4">
      <div className="flex items-start justify-between">
        <h2 id="spot-panel-title" className="text-2xl font-extrabold tracking-tight">
          Spot {spot.code}
        </h2>
        <button type="button" onClick={onClose} className={button.small}>
          Close
        </button>
      </div>
      <p className="mt-2">
        <StatusLabel status={spot.status} />
      </p>
      <dl className="mt-4 divide-y divide-hairline border-y border-hairline text-sm">
        {rows.map(([label, value]) => (
          <div key={label} className="flex justify-between py-2">
            <dt className="text-ink-muted">{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
      <div className="mt-4">
        <SpotPropertiesForm locationId={spot.locationId} spots={[spot]} />
      </div>
      <div className="mt-4 border-t border-hairline pt-4">
        {canClose && !confirming && (
          <button
            type="button"
            className={button.secondary}
            disabled={setStatus.isPending}
            onClick={() => (spot.status === 'RESERVED' ? setConfirming(true) : close())}
          >
            Mark out of service
          </button>
        )}
        {canClose && confirming && (
          <div role="group" aria-labelledby="close-reserved-warning" className="border-l-4 border-reserved pl-3">
            <p id="close-reserved-warning" className="max-w-prose text-sm">
              A driver has reserved {spot.code}. Closing it cancels their reservation, and they get a notification to
              pick another spot.
            </p>
            <div className="mt-3 flex flex-wrap items-center gap-3">
              <button type="button" className={button.primary} disabled={setStatus.isPending} onClick={close}>
                {setStatus.isPending ? 'Closing spot…' : 'Close spot anyway'}
              </button>
              <button type="button" className={button.quiet} onClick={() => setConfirming(false)}>
                Keep it open
              </button>
            </div>
          </div>
        )}
        {canReopen && (
          <button
            type="button"
            className={button.primary}
            disabled={setStatus.isPending}
            onClick={() => setStatus.mutate({ spotId: spot.id, status: 'AVAILABLE' })}
          >
            Return to service
          </button>
        )}
        {!canClose && !canReopen && (
          <p className="text-sm text-ink-muted">A car is parked here. You can mark it out of service once it has left.</p>
        )}
        {setStatus.error && (
          <p role="alert" className="mt-3 border-l-4 border-occupied pl-3 text-sm font-semibold">
            {setStatus.error.message}
          </p>
        )}
      </div>
    </section>
  )
}

const spotTypes: SpotType[] = ['STANDARD', 'COMPACT', 'LARGE', 'MOTORCYCLE']

function AddSpotsForm({ locationId, defaultLevel }: { locationId: string; defaultLevel: number }) {
  const addSpots = useAddSpots(locationId)
  const [added, setAdded] = useState<string>()

  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    const zone = String(form.get('zone')).trim().toUpperCase()
    const count = Number(form.get('count'))
    addSpots.mutate(
      {
        level: Number(form.get('level')),
        zone,
        count,
        type: form.get('type') as SpotType,
        evCharging: form.get('ev') === 'on',
        accessible: form.get('accessible') === 'on',
      },
      { onSuccess: () => setAdded(`Added ${count} spots to zone ${zone}.`) },
    )
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-4 bg-surface p-4" aria-labelledby="add-spots-title">
      <div>
        <h2 id="add-spots-title" className="text-lg font-semibold">
          Add spots
        </h2>
        <p className="text-sm text-ink-muted">Adds a row of spots to a zone. Pick a spot on the plan to manage it.</p>
      </div>
      <div className="grid grid-cols-3 gap-3">
        <label className={field.label}>
          Level
          <input name="level" type="number" required defaultValue={defaultLevel} min={-5} max={10} className={field.input} />
        </label>
        <label className={field.label}>
          Zone
          <input name="zone" required maxLength={2} pattern="[A-Za-z]{1,2}" title="One or two letters" defaultValue="A" className={field.input} />
        </label>
        <label className={field.label}>
          Spots
          <input name="count" type="number" required defaultValue={6} min={1} max={30} className={field.input} />
        </label>
      </div>
      <label className={field.label}>
        Type
        <select name="type" className={field.input} defaultValue="STANDARD">
          {spotTypes.map((t) => (
            <option key={t} value={t}>
              {spotTypeNames[t]}
            </option>
          ))}
        </select>
      </label>
      <div className="flex gap-6">
        <label className="flex min-h-11 items-center gap-2">
          <input name="ev" type="checkbox" className="size-5 accent-sign" /> EV charging
        </label>
        <label className="flex min-h-11 items-center gap-2">
          <input name="accessible" type="checkbox" className="size-5 accent-sign" /> Accessible
        </label>
      </div>
      <button type="submit" className={button.primary} disabled={addSpots.isPending}>
        {addSpots.isPending ? 'Adding spots…' : 'Add spots'}
      </button>
      <p role="status" className="text-sm">
        {addSpots.error ? addSpots.error.message : added}
      </p>
    </form>
  )
}

function SpotTable({
  spots,
  selectedId,
  onSelect,
  checked,
  onCheck,
}: {
  spots: Spot[]
  selectedId?: string
  onSelect: (id: string) => void
  checked: Set<string>
  onCheck: (checked: Set<string>) => void
}) {
  const allChecked = spots.every((s) => checked.has(s.id))
  const someChecked = spots.some((s) => checked.has(s.id))
  function toggle(id: string) {
    const next = new Set(checked)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    onCheck(next)
  }
  const sorted = [...spots].sort((a, b) => a.code.localeCompare(b.code, undefined, { numeric: true }))
  return (
    <div className="overflow-x-auto bg-surface">
      <table className="w-full text-left text-sm">
        <caption className="p-4 text-left">
          <span className="block text-lg font-semibold">All spots on this level</span>
          <span className="block text-sm text-ink-muted">Tick several spots to change their type, EV charging or access together.</span>
        </caption>
        <thead className="border-b-2 border-ink">
          <tr>
            <th scope="col" className="w-12 px-4 py-2">
              <input
                type="checkbox"
                className="size-5 accent-sign"
                aria-label="Select every spot on this level"
                checked={allChecked}
                ref={(box) => {
                  if (box) box.indeterminate = someChecked && !allChecked
                }}
                onChange={() => onCheck(allChecked ? new Set() : new Set(spots.map((s) => s.id)))}
              />
            </th>
            {['Spot', 'Zone', 'Type', 'EV charging', 'Accessible', 'Status'].map((h) => (
              <th key={h} scope="col" className="px-4 py-2 font-semibold">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-hairline">
          {sorted.map((spot) => (
            <tr key={spot.id} className={spot.id === selectedId || checked.has(spot.id) ? 'bg-concrete' : ''}>
              <td className="px-4 py-1">
                <input
                  type="checkbox"
                  className="size-5 accent-sign"
                  aria-label={`Select spot ${spot.code}`}
                  checked={checked.has(spot.id)}
                  onChange={() => toggle(spot.id)}
                />
              </td>
              <th scope="row" className="px-4 py-1">
                <button type="button" onClick={() => onSelect(spot.id)} className="min-h-9 font-bold text-sign hover:underline">
                  {spot.code}
                </button>
              </th>
              <td className="px-4 py-1">{spot.zone}</td>
              <td className="px-4 py-1">{spotTypeNames[spot.type]}</td>
              <td className="px-4 py-1">{spot.evCharging ? 'Yes' : 'No'}</td>
              <td className="px-4 py-1">{spot.accessible ? 'Yes' : 'No'}</td>
              <td className="px-4 py-1">
                <StatusLabel status={spot.status} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/** Shown instead of the add-spots form while spots are ticked in the table. */
function SeveralSpotsPanel({ locationId, spots, onClear }: { locationId: string; spots: Spot[]; onClear: () => void }) {
  return (
    <section aria-labelledby="several-spots-title" className="flex flex-col gap-3 bg-surface p-4">
      <div className="flex items-start justify-between gap-3">
        <h2 id="several-spots-title" className="text-lg font-semibold">
          {spots.length} {spots.length === 1 ? 'spot' : 'spots'} selected
        </h2>
        <button type="button" onClick={onClear} className={button.small}>
          Clear selection
        </button>
      </div>
      <p className="text-sm text-ink-muted">{spots.map((s) => s.code).join(', ')}</p>
      <SpotPropertiesForm locationId={locationId} spots={spots} />
    </section>
  )
}

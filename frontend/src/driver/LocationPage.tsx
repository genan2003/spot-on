import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { useLocation, useSpots } from '../api/queries'
import type { Spot, SpotType } from '../api/types'
import { BackIcon } from '../components/icons'
import { LevelPicker } from '../components/LevelPicker'
import { Legend } from '../components/Legend'
import { QueryState } from '../components/QueryState'
import { SpotPlan } from '../components/SpotPlan'
import { describePricing, levelName, levelsOf, spotTypeNames } from '../lib/format'
import { SpotSheet } from './SpotSheet'

export function LocationPage() {
  const { id = '' } = useParams()
  const location = useLocation(id)
  const spots = useSpots(id)
  const [level, setLevel] = useState<number>()
  const [selectedId, setSelectedId] = useState<string>()
  const [needsEv, setNeedsEv] = useState(false)
  const [needsAccessible, setNeedsAccessible] = useState(false)
  const [bayType, setBayType] = useState<SpotType>()

  return (
    <>
      <div className="px-4 pt-2">
        <Link to="/" className="inline-flex min-h-11 items-center gap-1 font-semibold text-sign hover:underline">
          <BackIcon className="size-5" /> All car parks
        </Link>
      </div>
      <QueryState query={location} loading="Loading car park…">
        {(loc) => (
          <div className="px-4 pb-4">
            <h1 className="text-2xl font-extrabold tracking-tight">{loc.name}</h1>
            <p className="text-ink-muted">{loc.address}</p>
            <p className="mt-1">{describePricing(loc.pricing)}</p>
          </div>
        )}
      </QueryState>
      <QueryState query={spots} loading="Loading spots…">
        {(all) => {
          if (all.length === 0) {
            return <p className="px-4 py-8">This car park has no spots set up yet. Try another car park.</p>
          }
          const levels = levelsOf(all)
          const current = level ?? levels[0]
          const onLevel = all.filter((s) => s.level === current)
          const free = onLevel.filter((s) => s.status === 'AVAILABLE').length
          const selected = all.find((s) => s.id === selectedId)
          const matches = (s: Spot) =>
            (!needsEv || s.evCharging) && (!needsAccessible || s.accessible) && (!bayType || s.type === bayType)

          return (
            <>
              <div className="flex flex-col gap-3 px-4 pb-4">
                {levels.length > 1 && <LevelPicker levels={levels} value={current} onChange={setLevel} />}
                <div className="flex flex-wrap gap-2" role="group" aria-label="Show only spots with">
                  <Toggle pressed={needsEv} onChange={setNeedsEv}>
                    EV charging
                  </Toggle>
                  <Toggle pressed={needsAccessible} onChange={setNeedsAccessible}>
                    Accessible
                  </Toggle>
                </div>
                <BayTypePicker value={bayType} onChange={setBayType} />
                <p className="tabular text-lg font-bold" aria-live="polite">
                  {free === 0 ? `No free spots on ${levelName(current).toLowerCase()}` : `${free} free on ${levelName(current).toLowerCase()}`}
                </p>
              </div>
              <Legend />
              <SpotPlan
                spots={onLevel}
                selectedId={selectedId}
                onSelect={(s) => setSelectedId(s.id)}
                matches={matches}
                label={`Spots on ${levelName(current).toLowerCase()}`}
              />
              <p className="px-4 pt-3 text-sm text-ink-muted">Pick a spot to see its details. The plan updates live.</p>
              {selected && (
                <>
                  {/* Room to scroll the plan out from under the sheet */}
                  <div className="h-72" aria-hidden />
                  <SpotSheet spot={selected} onClose={() => setSelectedId(undefined)} />
                </>
              )}
            </>
          )
        }}
      </QueryState>
    </>
  )
}

function Toggle({ pressed, onChange, children }: { pressed: boolean; onChange: (v: boolean) => void; children: string }) {
  return (
    <button
      type="button"
      aria-pressed={pressed}
      onClick={() => onChange(!pressed)}
      className={`min-h-10 rounded-sign border-2 px-3 text-sm font-semibold ${
        pressed ? 'border-ink bg-ink text-surface' : 'border-edge bg-surface text-ink hover:border-ink'
      }`}
    >
      {children}
    </button>
  )
}

const bayTypes: (SpotType | undefined)[] = [undefined, 'COMPACT', 'STANDARD', 'LARGE', 'MOTORCYCLE']

/** One bay type at a time; spots of other types are dimmed on the plan, like the toggles above. */
function BayTypePicker({ value, onChange }: { value?: SpotType; onChange: (type?: SpotType) => void }) {
  return (
    <div className="flex flex-wrap items-center gap-2" role="radiogroup" aria-labelledby="bay-type-label">
      <span id="bay-type-label" className="text-sm font-semibold">
        Bay type
      </span>
      {bayTypes.map((type) => {
        const active = type === value
        return (
          <button
            key={type ?? 'any'}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => onChange(type)}
            className={`min-h-10 rounded-sign border-2 px-3 text-sm font-semibold ${
              active ? 'border-ink bg-ink text-surface' : 'border-edge bg-surface text-ink hover:border-ink'
            }`}
          >
            {type ? spotTypeNames[type] : 'Any'}
          </button>
        )
      })}
    </div>
  )
}

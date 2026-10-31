import { useState } from 'react'
import { DRIVER_ID } from '../api/driver'
import type { Spot, SpotStatus, SpotType } from '../api/types'
import { spotTypeNames, statusNames } from '../lib/format'
import { AccessibleIcon, BoltIcon, CarIcon, CheckIcon, ConeIcon, MotorcycleIcon, MotorcycleTopIcon, ParkingSign } from './icons'

interface SpotPlanProps {
  /** Spots of one level. Zones are drawn as rows of bays; two rows share a driving aisle. */
  spots: Spot[]
  selectedId?: string
  onSelect: (spot: Spot) => void
  /** Spots that don't match the driver's filters are dimmed, not hidden, so the layout stays put. */
  matches?: (spot: Spot) => boolean
  label: string
}

type Facing = 'down' | 'up'

/**
 * Bay width shows the spot type, as painted lines do in a real car park: every bay has the same
 * depth so rows stay aligned along the aisle, only the width changes.
 */
const bayWidth: Record<SpotType, string> = {
  MOTORCYCLE: 'w-9',
  COMPACT: 'w-10',
  STANDARD: 'w-12',
  LARGE: 'w-16',
}

/**
 * Top-down plan of one level, drawn the way the car park is painted: bay lines on asphalt,
 * a car in occupied bays, yellow hatching on reserved ones, a cone on bays out of service.
 * Bay width shows the type (compact, standard, large, motorcycle).
 * Shared by the driver app and the admin dashboard so status always looks the same.
 */
export function SpotPlan({ spots, selectedId, onSelect, matches = () => true, label }: SpotPlanProps) {
  const zones = [...new Set(spots.map((s) => s.zone))].sort()
  const rows = zones.map((zone) =>
    spots.filter((s) => s.zone === zone).sort((a, b) => a.code.localeCompare(b.code, undefined, { numeric: true })),
  )
  const pairs: Spot[][][] = []
  for (let i = 0; i < rows.length; i += 2) pairs.push(rows.slice(i, i + 2))

  return (
    <div className="on-asphalt overflow-x-auto bg-asphalt text-paint" role="group" aria-label={label}>
      <div className="inline-flex min-w-full flex-col gap-6 px-4 py-6">
        {pairs.map((pair) => (
          <div key={pair[0][0].zone}>
            <BayRow spots={pair[0]} facing="down" selectedId={selectedId} onSelect={onSelect} matches={matches} />
            <div className="flex h-12 items-center" aria-hidden>
              <div className="w-full border-t-2 border-dashed border-paint/35" />
            </div>
            {pair[1] && <BayRow spots={pair[1]} facing="up" selectedId={selectedId} onSelect={onSelect} matches={matches} />}
          </div>
        ))}
      </div>
    </div>
  )
}

interface BayRowProps {
  spots: Spot[]
  facing: Facing
  selectedId?: string
  onSelect: (spot: Spot) => void
  matches: (spot: Spot) => boolean
}

function BayRow({ spots, facing, ...rest }: BayRowProps) {
  return (
    <div className="flex">
      {/* Fixed gutter, wide enough for the longest zone name (two letters), so every row's bays line up. */}
      <span className="mr-3 w-8 shrink-0 self-center text-lg font-extrabold text-paint/60" aria-hidden>
        {spots[0].zone}
      </span>
      <div className={`flex border-r-2 border-paint ${facing === 'down' ? 'border-t-2' : 'border-b-2'}`}>
        {spots.map((spot) => (
          <Bay key={spot.id} spot={spot} facing={facing} {...rest} />
        ))}
      </div>
    </div>
  )
}

function describe(spot: Spot): string {
  const extras = [spot.evCharging && 'EV charging', spot.accessible && 'accessible', spot.type !== 'STANDARD' && spotTypeNames[spot.type].toLowerCase()]
  const mine = spot.heldBy === DRIVER_ID ? ', yours' : ''
  return [`${spot.code}, ${statusNames[spot.status].toLowerCase()}${mine}`, ...extras.filter(Boolean)].join(', ')
}

function Bay({ spot, facing, selectedId, onSelect, matches }: Omit<BayRowProps, 'spots'> & { spot: Spot }) {
  // Remember the previous status so only live changes animate, not the first render.
  const [shownStatus, setShownStatus] = useState(spot.status)
  const [changed, setChanged] = useState(false)
  if (shownStatus !== spot.status) {
    setShownStatus(spot.status)
    setChanged(true)
  }

  const selected = spot.id === selectedId
  const mine = spot.heldBy === DRIVER_ID
  const dimmed = !matches(spot)
  // Bays open towards the aisle: the spot number is painted at the open end, symbols at the wall.
  const towardsAisle = facing === 'down' ? 'flex-col' : 'flex-col-reverse'

  return (
    <button
      type="button"
      onClick={() => onSelect(spot)}
      aria-pressed={selected}
      aria-label={describe(spot)}
      className={`relative flex h-20 ${bayWidth[spot.type]} shrink-0 ${towardsAisle} items-center justify-between border-l-2 border-paint ${
        spot.type === 'MOTORCYCLE' ? 'px-0.5' : 'px-1'
      } py-1.5 transition-opacity ${
        dimmed ? 'opacity-30' : ''
      } ${selected ? 'inset-ring-3 inset-ring-paint' : ''}`}
    >
      <span className="flex h-3.5 gap-0.5 text-paint/80">
        {spot.type === 'MOTORCYCLE' && <MotorcycleIcon className="size-3.5" />}
        {spot.evCharging && <BoltIcon className="size-3.5" />}
        {spot.accessible && <AccessibleIcon className="size-3.5" />}
      </span>

      <BayContent key={spot.status} status={spot.status} type={spot.type} facing={facing} animate={changed} />

      <span className="text-sm leading-none font-extrabold tracking-tight">{spot.code}</span>

      {mine && <ParkingSign size={16} className="absolute top-1/2 right-0.5 -translate-y-1/2" />}
    </button>
  )
}

function BayContent({ status, type, facing, animate }: { status: SpotStatus; type: SpotType; facing: Facing; animate: boolean }) {
  const enter = animate ? 'animate-paint' : ''
  switch (status) {
    case 'AVAILABLE':
      return <CheckIcon className={`size-5 text-free-bright ${enter}`} />
    case 'RESERVED':
      return <span className={`hatch absolute inset-x-1 inset-y-5 ${enter}`} aria-hidden />
    case 'OCCUPIED': {
      // Vehicles drive in from the aisle, nose first towards the wall. The slide is in the vehicle's
      // own frame, so rotating it for the opposite row also flips where it comes from.
      const Vehicle = type === 'MOTORCYCLE' ? MotorcycleTopIcon : CarIcon
      return <Vehicle className={`h-11 text-occupied-bright ${facing === 'up' ? 'rotate-180' : ''} ${animate ? 'animate-park-in' : ''}`} />
    }
    case 'OUT_OF_SERVICE':
      return <ConeIcon className={`size-6 text-out-bright ${enter}`} />
  }
}

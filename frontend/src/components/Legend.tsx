import type { SpotStatus, SpotType } from '../api/types'
import { spotTypeNames, statusNames } from '../lib/format'
import { AccessibleIcon, BoltIcon, MotorcycleIcon } from './icons'
import { StatusMark } from './status'

const order: SpotStatus[] = ['AVAILABLE', 'RESERVED', 'OCCUPIED', 'OUT_OF_SERVICE']

// Half the real bay widths on the plan (w-10, w-12, w-16), so the samples compare the same way.
const sizes: [SpotType, string][] = [
  ['COMPACT', 'w-5'],
  ['STANDARD', 'w-6'],
  ['LARGE', 'w-8'],
]

/** Key for the spot plan, drawn on the same asphalt so the marks match exactly. */
export function Legend() {
  return (
    <div className="flex flex-col gap-2 bg-asphalt px-4 pt-4 text-sm text-paint">
      <ul className="flex flex-wrap gap-x-5 gap-y-2" aria-label="Spot status">
        {order.map((status) => (
          <li key={status} className="flex items-center gap-1.5">
            <StatusMark status={status} />
            {statusNames[status]}
          </li>
        ))}
        <li className="flex items-center gap-1.5">
          <BoltIcon className="size-4" /> EV charging
        </li>
        <li className="flex items-center gap-1.5">
          <AccessibleIcon className="size-4" /> Accessible
        </li>
      </ul>
      <ul className="flex flex-wrap items-end gap-x-5 gap-y-2" aria-label="Bay types: the wider the bay, the bigger the vehicle">
        {sizes.map(([type, width]) => (
          <li key={type} className="flex items-end gap-1.5">
            <span className={`h-5 ${width} border-x-2 border-t-2 border-paint/80`} aria-hidden />
            {spotTypeNames[type]}
          </li>
        ))}
        <li className="flex items-end gap-1.5">
          <MotorcycleIcon className="size-4" /> {spotTypeNames.MOTORCYCLE}
        </li>
      </ul>
    </div>
  )
}

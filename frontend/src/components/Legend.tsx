import type { SpotStatus } from '../api/types'
import { statusNames } from '../lib/format'
import { AccessibleIcon, BoltIcon } from './icons'
import { StatusMark } from './status'

const order: SpotStatus[] = ['AVAILABLE', 'RESERVED', 'OCCUPIED', 'OUT_OF_SERVICE']

/** Key for the spot plan, drawn on the same asphalt so the marks match exactly. */
export function Legend() {
  return (
    <ul className="flex flex-wrap gap-x-5 gap-y-2 bg-asphalt px-4 pt-4 text-sm text-paint">
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
  )
}

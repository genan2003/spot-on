import type { SpotStatus } from '../api/types'
import { statusNames } from '../lib/format'
import { CarIcon, CheckIcon, ConeIcon, HatchIcon } from './icons'

/**
 * Spot status as a tiny bay: the mark always sits on asphalt, so the same bright status
 * colours work in lists, tables and the legend as on the spot plan itself.
 */
export function StatusMark({ status, className = '' }: { status: SpotStatus; className?: string }) {
  return (
    <span className={`inline-grid size-5 shrink-0 place-items-center rounded-bay bg-asphalt ${className}`} aria-hidden>
      {status === 'AVAILABLE' && <CheckIcon className="size-3.5 text-free-bright" />}
      {status === 'RESERVED' && <HatchIcon className="size-3.5 text-reserved" />}
      {status === 'OCCUPIED' && <CarIcon className="h-4 text-occupied-bright" />}
      {status === 'OUT_OF_SERVICE' && <ConeIcon className="size-3.5 text-out-bright" />}
    </span>
  )
}

export function StatusLabel({ status }: { status: SpotStatus }) {
  return (
    <span className="inline-flex items-center gap-2">
      <StatusMark status={status} />
      <span>{statusNames[status]}</span>
    </span>
  )
}

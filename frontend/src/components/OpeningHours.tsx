import { useNow } from '../api/queries'
import type { ParkingLocation } from '../api/types'
import { isOpenNow, openingHoursText } from '../lib/hours'

/** "Open 07:00 to 22:00", or, while it is closed, "Closed now, opens at 07:00" in bold. Updates when it opens or closes. */
export function OpeningHours({ location, className = '' }: { location: Pick<ParkingLocation, 'opensAt' | 'closesAt'>; className?: string }) {
  const now = useNow()
  if (isOpenNow(location, now)) return <span className={className}>{openingHoursText(location)}</span>
  return <strong className={className}>Closed now, opens at {location.opensAt}</strong>
}

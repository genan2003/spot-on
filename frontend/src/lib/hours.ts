import type { ParkingLocation } from '../api/types'

type Hours = Pick<ParkingLocation, 'opensAt' | 'closesAt'>

// The car parks keep Bucharest time, like parking-service; the driver's phone may be set to another zone.
const carParkClock = new Intl.DateTimeFormat('en-GB', { timeZone: 'Europe/Bucharest', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })

/** "HH:mm" in the car park's time zone. */
export function carParkTime(now = Date.now()): string {
  return carParkClock.format(now)
}

/**
 * Same rule as parking-service's ParkingLocation.isOpenAt: from opening time until just before closing time, past
 * midnight when it closes earlier than it opens; no hours means always open. "HH:mm" strings compare in time order.
 */
export function isOpenAt(hours: Hours, time: string): boolean {
  const { opensAt, closesAt } = hours
  if (!opensAt || !closesAt) return true
  if (opensAt < closesAt) return time >= opensAt && time < closesAt
  return time >= opensAt || time < closesAt
}

export function isOpenNow(hours: Hours, now = Date.now()): boolean {
  return isOpenAt(hours, carParkTime(now))
}

/** "Open 07:00 to 22:00", or "Open around the clock". */
export function openingHoursText(hours: Hours): string {
  return hours.opensAt && hours.closesAt ? `Open ${hours.opensAt} to ${hours.closesAt}` : 'Open around the clock'
}

import { driverKey } from '../api/driver'

// Licence plates, checked and written the way reservation-service does (LicencePlates.java), and the
// last plate a driver used, remembered per browser. Storage can be unavailable, so access is guarded.
const KEY = driverKey('licence-plate')
let memory = ''

const PATTERN = /^[A-Za-z0-9][A-Za-z0-9 -]{1,11}$/

/** Upper case, single spaces: "cj  07 abc" becomes "CJ 07 ABC". */
export function normalizePlate(plate: string): string {
  return plate.trim().replace(/\s+/g, ' ').toUpperCase()
}

export function isPlausiblePlate(plate: string): boolean {
  return PATTERN.test(plate.trim())
}

export function lastPlate(): string {
  try {
    return localStorage.getItem(KEY) ?? memory
  } catch {
    return memory
  }
}

export function rememberPlate(plate: string) {
  memory = plate
  try {
    localStorage.setItem(KEY, plate)
  } catch {
    // Kept in memory only; the driver types it again next visit.
  }
}

import type { PricingRule, Spot, SpotStatus, SpotType } from '../api/types'

export function lei(amount: number): string {
  return `${amount.toFixed(2)} lei`
}

/** Short price for lists: whole amounts drop the decimals ("6 lei", not "6.00 lei"). */
function shortLei(amount: number): string {
  return Number.isInteger(amount) ? `${amount} lei` : lei(amount)
}

export function describePricing(rule: PricingRule): string {
  switch (rule.strategy) {
    case 'HOURLY':
      return `${shortLei(rule.hourlyRate ?? 0)} per hour`
    case 'FLAT_RATE':
      return `${shortLei(rule.flatRate ?? 0)} per visit`
    case 'TIERED':
      return `${shortLei(rule.firstHourRate ?? 0)} the first hour, then ${shortLei(rule.laterHourRate ?? 0)} per hour`
    case 'NIGHT_WEEKEND':
      return `${shortLei(rule.dayRate ?? 0)} per hour, ${shortLei(rule.nightWeekendRate ?? 0)} at night and on weekends`
  }
}

export const strategyNames: Record<PricingRule['strategy'], string> = {
  HOURLY: 'Hourly',
  FLAT_RATE: 'Flat rate',
  TIERED: 'Tiered',
  NIGHT_WEEKEND: 'Night and weekend',
}

export const statusNames: Record<SpotStatus, string> = {
  AVAILABLE: 'Free',
  RESERVED: 'Reserved',
  OCCUPIED: 'Occupied',
  OUT_OF_SERVICE: 'Out of service',
}

export const spotTypeNames: Record<SpotType, string> = {
  STANDARD: 'Standard',
  COMPACT: 'Compact',
  LARGE: 'Large',
  MOTORCYCLE: 'Motorcycle',
}

/** Levels from the top down, the order you drive through them. */
export function levelsOf(spots: Spot[]): number[] {
  return [...new Set(spots.map((s) => s.level))].sort((a, b) => b - a)
}

export function levelName(level: number): string {
  return level === 0 ? 'Ground level' : `Level ${String(level).replace('-', '−')}`
}

/** "1:04:09" for a running timer, "12:30" under an hour. */
export function clock(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000))
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  const mm = String(m).padStart(h > 0 ? 2 : 1, '0')
  const ss = String(s).padStart(2, '0')
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`
}

/** "2 h 14 min" for receipts. */
export function duration(ms: number): string {
  const minutes = Math.max(1, Math.round(ms / 60000))
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return h > 0 ? `${h} h ${m} min` : `${m} min`
}

export function timeOfDay(iso: string): string {
  return new Date(iso).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })
}

export function relativeTime(iso: string, now = Date.now()): string {
  const minutes = Math.round((now - new Date(iso).getTime()) / 60000)
  if (minutes < 1) return 'Just now'
  if (minutes < 60) return `${minutes} min ago`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours} h ago`
  return new Date(iso).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })
}

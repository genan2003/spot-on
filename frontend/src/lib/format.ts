import type { PriceBand, PricingRule, Spot, SpotStatus, SpotType } from '../api/types'
import { surchargeFor } from './pricing'

export function lei(amount: number): string {
  return `${amount.toFixed(2)} lei`
}

/** Short price for lists: whole amounts drop the decimals ("6 lei", not "6.00 lei"). */
function shortLei(amount: number): string {
  if (Number.isNaN(amount)) return '… lei'
  return Number.isInteger(amount) ? `${amount} lei` : lei(amount)
}

/** The rule in one sentence, as drivers see it: the strategy first, then any extras. */
export function describePricing(rule: PricingRule): string {
  const parts = [describeStrategy(rule)]
  const block = rule.billingIncrementMinutes ?? 60
  if (rule.strategy !== 'FLAT_RATE' && block !== 60) parts.push(`charged per ${block} min`)
  if (rule.gracePeriodMinutes) parts.push(`free if you leave within ${rule.gracePeriodMinutes} min`)
  if (rule.dailyCap != null) parts.push(`at most ${shortLei(rule.dailyCap)} a day`)
  return parts.join(', ')
}

function describeStrategy(rule: PricingRule): string {
  switch (rule.strategy) {
    case 'HOURLY':
      return `${shortLei(rule.hourlyRate ?? 0)} per hour`
    case 'FLAT_RATE':
      return `${shortLei(rule.flatRate ?? 0)} per visit`
    case 'TIERED':
      return describeBands(rule.bands ?? [])
    case 'NIGHT_WEEKEND':
      return `${shortLei(rule.dayRate ?? 0)} per hour, ${shortLei(rule.nightWeekendRate ?? 0)} from ${rule.nightStart ?? '20:00'} to ${rule.nightEnd ?? '08:00'} and on weekends`
  }
}

/** "8 lei per hour for the first hour, 4 lei per hour up to 4 h, then 2 lei per hour" */
function describeBands(bands: PriceBand[]): string {
  const sorted = [...bands].sort((a, b) => a.fromHour - b.fromHour)
  if (sorted.length === 0) return 'No price set'
  if (sorted.length === 1) return `${shortLei(sorted[0].hourlyRate)} per hour`
  return sorted
    .map((band, i) => {
      const next = sorted[i + 1]
      const price = `${shortLei(band.hourlyRate)} per hour`
      if (!next) return `then ${price}`
      if (i === 0) return `${price} for the first ${next.fromHour === 1 ? 'hour' : `${next.fromHour} h`}`
      return `${price} up to ${next.fromHour} h`
    })
    .join(', ')
}

/** What a bay's surcharge means for the driver, e.g. "Large bay with EV charging: 30% more"; empty when 0. */
export function describeSurcharge(rule: PricingRule, spot: Pick<Spot, 'type' | 'evCharging'>): string {
  const percent = surchargeFor(rule, spot)
  if (percent === 0) return ''
  const kind = spot.type === 'STANDARD' ? 'Bay' : `${spotTypeNames[spot.type]} bay`
  const bay = spot.evCharging ? `${kind} with EV charging` : kind
  return `${bay}: ${Math.abs(percent)}% ${percent > 0 ? 'more' : 'less'}`
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

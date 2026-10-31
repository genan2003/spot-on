import type { BaySurcharges, PricingRule, SpotType } from '../api/types'

/**
 * Client-side copy of the backend's pricing (reservation-service), used for the admin preview and the
 * demo data. The real cost always comes from reservation-service. Same rules:
 * - the strategy (Strategy pattern) charges for the exact time,
 * - the extras wrap it like the backend's decorators (Decorator pattern), from the inside out:
 *   billing blocks, bay surcharge, daily maximum, grace period.
 */

export interface Bay {
  type: SpotType
  evCharging: boolean
}

export const standardBay: Bay = { type: 'STANDARD', evCharging: false }

export const noSurcharges: BaySurcharges = { compact: 0, large: 0, motorcycle: 0, evCharging: 0 }

const HOUR = 3_600_000
const DAY = 24 * HOUR

type Pricing = (start: Date, end: Date) => number

export function cost(rule: PricingRule, startedAt: Date, endedAt: Date, bay: Bay = standardBay): number {
  return round(pricingFor(rule, bay)(startedAt, endedAt))
}

/** The total surcharge in percent for one bay: its type's plus EV charging's when it has a charger. */
export function surchargeFor(rule: PricingRule, bay: Bay): number {
  const s = rule.surcharges ?? noSurcharges
  const byType = { STANDARD: 0, COMPACT: s.compact, LARGE: s.large, MOTORCYCLE: s.motorcycle }[bay.type]
  return byType + (bay.evCharging ? s.evCharging : 0)
}

/** Keeps only what the strategy uses and fills in defaults, as parking-service does when it stores a rule. */
export function normalizeRule(rule: PricingRule): PricingRule {
  const extras: PricingRule = {
    strategy: rule.strategy,
    billingIncrementMinutes: rule.billingIncrementMinutes ?? 60,
    gracePeriodMinutes: rule.gracePeriodMinutes ?? 0,
    dailyCap: rule.dailyCap,
    surcharges: rule.surcharges ?? noSurcharges,
  }
  switch (rule.strategy) {
    case 'HOURLY':
      return { ...extras, hourlyRate: rule.hourlyRate }
    case 'FLAT_RATE':
      return { ...extras, flatRate: rule.flatRate }
    case 'TIERED':
      return { ...extras, bands: [...(rule.bands ?? [])].sort((a, b) => a.fromHour - b.fromHour) }
    case 'NIGHT_WEEKEND':
      return {
        ...extras,
        dayRate: rule.dayRate,
        nightWeekendRate: rule.nightWeekendRate,
        nightStart: rule.nightStart ?? '20:00',
        nightEnd: rule.nightEnd ?? '08:00',
      }
  }
}

function pricingFor(rule: PricingRule, bay: Bay): Pricing {
  let pricing = strategy(rule)
  pricing = billingIncrement(pricing, (rule.billingIncrementMinutes ?? 60) * 60_000)
  const surcharge = surchargeFor(rule, bay)
  if (surcharge !== 0) pricing = baySurcharge(pricing, surcharge)
  if (rule.dailyCap != null) pricing = dailyCap(pricing, rule.dailyCap)
  if (rule.gracePeriodMinutes) pricing = gracePeriod(pricing, rule.gracePeriodMinutes * 60_000)
  return pricing
}

function strategy(rule: PricingRule): Pricing {
  switch (rule.strategy) {
    case 'HOURLY':
      return (start, end) => hours(start, end) * (rule.hourlyRate ?? 0)
    case 'FLAT_RATE':
      return () => rule.flatRate ?? 0
    case 'TIERED':
      return (start, end) => tiered(rule.bands ?? [], hours(start, end))
    case 'NIGHT_WEEKEND':
      return (start, end) => nightWeekend(rule, start, end)
  }
}

function tiered(bands: PricingRule['bands'] & object, stayHours: number): number {
  const sorted = [...bands].sort((a, b) => a.fromHour - b.fromHour)
  return sorted.reduce((sum, band, i) => {
    const to = Math.min(stayHours, sorted[i + 1]?.fromHour ?? Infinity)
    return sum + Math.max(0, to - band.fromHour) * band.hourlyRate
  }, 0)
}

/** Each slice of the stay at the rate for its time: slices end at midnight, night start and night end. */
function nightWeekend(rule: PricingRule, start: Date, end: Date): number {
  const nightStart = minutesOf(rule.nightStart ?? '20:00')
  const nightEnd = minutesOf(rule.nightEnd ?? '08:00')
  let total = 0
  let from = start
  while (from < end) {
    const to = new Date(Math.min(end.getTime(), nextChange(from, [0, nightStart, nightEnd]).getTime()))
    const minute = from.getHours() * 60 + from.getMinutes()
    const weekend = from.getDay() === 0 || from.getDay() === 6
    const night =
      nightStart === nightEnd
        ? false
        : nightStart < nightEnd
          ? minute >= nightStart && minute < nightEnd
          : minute >= nightStart || minute < nightEnd
    const rate = (weekend || night ? rule.nightWeekendRate : rule.dayRate) ?? 0
    total += hours(from, to) * rate
    from = to
  }
  return total
}

function nextChange(from: Date, minutesOfDay: number[]): Date {
  return minutesOfDay
    .map((m) => {
      const at = new Date(from)
      at.setHours(Math.floor(m / 60), m % 60, 0, 0)
      if (at <= from) at.setDate(at.getDate() + 1)
      return at
    })
    .reduce((a, b) => (a < b ? a : b))
}

function billingIncrement(inner: Pricing, blockMs: number): Pricing {
  return (start, end) => {
    const blocks = Math.max(1, Math.ceil((end.getTime() - start.getTime()) / blockMs))
    return inner(start, new Date(start.getTime() + blocks * blockMs))
  }
}

function baySurcharge(inner: Pricing, percent: number): Pricing {
  return (start, end) => Math.max(0, round(inner(start, end)) * (1 + percent / 100))
}

function dailyCap(inner: Pricing, cap: number): Pricing {
  return (start, end) => {
    const days = Math.max(1, Math.ceil((end.getTime() - start.getTime()) / DAY))
    return Math.min(inner(start, end), cap * days)
  }
}

function gracePeriod(inner: Pricing, graceMs: number): Pricing {
  return (start, end) => (end.getTime() - start.getTime() <= graceMs ? 0 : inner(start, end))
}

function hours(start: Date, end: Date): number {
  return (end.getTime() - start.getTime()) / HOUR
}

function minutesOf(time: string): number {
  const [h, m] = time.split(':').map(Number)
  return h * 60 + m
}

function round(amount: number): number {
  return Math.round(amount * 100) / 100
}

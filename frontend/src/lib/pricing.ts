import type { PricingRule } from '../api/types'

/**
 * Client-side copy of the backend's PricingStrategy rules (Strategy pattern), used for the
 * admin preview and the demo data. The real cost always comes from reservation-service.
 * Every started hour counts.
 */
export function cost(rule: PricingRule, startedAt: Date, endedAt: Date): number {
  const hours = Math.max(1, Math.ceil((endedAt.getTime() - startedAt.getTime()) / 3_600_000))
  switch (rule.strategy) {
    case 'HOURLY':
      return hours * (rule.hourlyRate ?? 0)
    case 'FLAT_RATE':
      return rule.flatRate ?? 0
    case 'TIERED':
      return (rule.firstHourRate ?? 0) + (hours - 1) * (rule.laterHourRate ?? 0)
    case 'NIGHT_WEEKEND': {
      const day = startedAt.getDay()
      const hour = startedAt.getHours()
      const offPeak = day === 0 || day === 6 || hour >= 20 || hour < 8
      return hours * ((offPeak ? rule.nightWeekendRate : rule.dayRate) ?? 0)
    }
  }
}

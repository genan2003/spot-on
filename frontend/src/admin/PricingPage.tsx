import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { useLocation, useUpdatePricing } from '../api/queries'
import type { BaySurcharges, BillingIncrement, PriceBand, PricingRule, PricingStrategyType } from '../api/types'
import { BackIcon } from '../components/icons'
import { QueryState } from '../components/QueryState'
import { Segmented } from '../components/Segmented'
import { describePricing, lei, strategyNames } from '../lib/format'
import { cost, noSurcharges, standardBay, type Bay } from '../lib/pricing'
import { button, field } from '../lib/ui'

// One entry per PricingStrategy implementation in the backend.
const strategies: { type: PricingStrategyType; explains: string }[] = [
  { type: 'HOURLY', explains: 'Every hour costs the same.' },
  {
    type: 'FLAT_RATE',
    explains: 'One price per visit, however long the driver stays.',
  },
  {
    type: 'TIERED',
    explains: 'The hourly price changes as the stay gets longer.',
  },
  {
    type: 'NIGHT_WEEKEND',
    explains: 'A day price per hour, and a different one at night and on weekends.',
  },
]

const blocks: { value: BillingIncrement; label: string }[] = [
  { value: 15, label: '15 min' },
  { value: 30, label: '30 min' },
  { value: 60, label: '1 hour' },
]

const surchargeFields: { key: keyof BaySurcharges; label: string }[] = [
  { key: 'compact', label: 'Compact' },
  { key: 'large', label: 'Large' },
  { key: 'motorcycle', label: 'Motorcycle' },
  { key: 'evCharging', label: 'EV charging' },
]

const MAX_BANDS = 10

/** Example stays shown next to the form so the admin sees what drivers would pay. Local time. */
const examples: { label: string; start: string; minutes: number; bay?: Bay }[] = [
  {
    label: '10 min on a weekday afternoon',
    start: '2026-03-04T14:00',
    minutes: 10,
  },
  {
    label: '2 h 30 min on a weekday afternoon',
    start: '2026-03-04T14:00',
    minutes: 150,
  },
  {
    label: '19:00 to 23:00 on a weekday',
    start: '2026-03-04T19:00',
    minutes: 240,
  },
  { label: '3 h on a Saturday', start: '2026-03-07T11:00', minutes: 180 },
  {
    label: '26 h from a weekday morning',
    start: '2026-03-04T09:00',
    minutes: 26 * 60,
  },
  {
    label: '2 h in a large bay with EV charging',
    start: '2026-03-04T14:00',
    minutes: 120,
    bay: { type: 'LARGE', evCharging: true },
  },
]

export function PricingPage() {
  const { id = '' } = useParams()
  const location = useLocation(id)
  return (
    <div className="p-4 lg:p-8">
      <Link to={`/admin/locations/${id}`} className="inline-flex min-h-11 items-center gap-1 font-semibold text-sign hover:underline">
        <BackIcon className="size-5" /> Back to car park
      </Link>
      <QueryState query={location} loading="Loading pricing…">
        {(loc) => (
          <>
            <h1 className="text-2xl font-extrabold tracking-tight">Pricing for {loc.name}</h1>
            <PricingForm key={loc.id} locationId={loc.id} initial={loc.pricing} />
          </>
        )}
      </QueryState>
    </div>
  )
}

/** Rates a strategy starts with when the admin switches to it; empty fields must be filled in. */
function startingRates(type: PricingStrategyType, current: PricingRule): PricingRule {
  const extras = {
    billingIncrementMinutes: current.billingIncrementMinutes,
    gracePeriodMinutes: current.gracePeriodMinutes,
    dailyCap: current.dailyCap,
    surcharges: current.surcharges,
  }
  switch (type) {
    case 'TIERED':
      return {
        ...extras,
        strategy: type,
        bands: [
          { fromHour: 0, hourlyRate: NaN },
          { fromHour: 1, hourlyRate: NaN },
        ],
      }
    case 'NIGHT_WEEKEND':
      return {
        ...extras,
        strategy: type,
        nightStart: '20:00',
        nightEnd: '08:00',
      }
    default:
      return { ...extras, strategy: type }
  }
}

function PricingForm({ locationId, initial }: { locationId: string; initial: PricingRule }) {
  const [rule, setRule] = useState<PricingRule>(initial)
  const [saved, setSaved] = useState(false)
  const update = useUpdatePricing(locationId)

  function change(next: Partial<PricingRule>) {
    setRule((r) => ({ ...r, ...next }))
    setSaved(false)
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    update.mutate(rule, { onSuccess: () => setSaved(true) })
  }

  return (
    <form onSubmit={submit} className="mt-6 grid max-w-5xl items-start gap-x-10 gap-y-8 lg:grid-cols-[3fr_2fr]">
      <div className="flex flex-col gap-8">
        <fieldset>
          <legend className="text-lg font-semibold">How drivers are charged</legend>
          <div className="mt-3 flex flex-col divide-y divide-hairline border-y border-hairline">
            {strategies.map((s) => (
              <label key={s.type} className="flex cursor-pointer gap-3 py-3">
                <input
                  type="radio"
                  name="strategy"
                  className="mt-1 size-5 accent-sign"
                  checked={rule.strategy === s.type}
                  onChange={() => {
                    setRule((r) => startingRates(s.type, r))
                    setSaved(false)
                  }}
                />
                <span>
                  <span className="block font-semibold">{strategyNames[s.type]}</span>
                  <span className="block text-sm text-ink-muted">{s.explains}</span>
                </span>
              </label>
            ))}
          </div>
        </fieldset>

        <fieldset className="flex flex-col gap-4">
          <legend className="text-lg font-semibold">{strategyNames[rule.strategy]} rates</legend>
          <Rates rule={rule} change={change} />
        </fieldset>

        <Extras rule={rule} change={change} />
      </div>

      {/* The preview stays in view while the admin scrolls through the form. */}
      <div className="flex flex-col gap-4 lg:sticky lg:top-8">
        <Preview rule={rule} />
        <button type="submit" className={button.primary} disabled={update.isPending}>
          {update.isPending ? 'Saving pricing…' : 'Save pricing'}
        </button>
        <p role="status" className="text-sm font-semibold">
          {update.error ? update.error.message : saved ? 'Pricing saved. New sessions use it from now on.' : ''}
        </p>
      </div>
    </form>
  )
}

type Change = (next: Partial<PricingRule>) => void

function Rates({ rule, change }: { rule: PricingRule; change: Change }) {
  switch (rule.strategy) {
    case 'HOURLY':
      return <Money label="Price per hour" value={rule.hourlyRate} onChange={(hourlyRate) => change({ hourlyRate })} required />
    case 'FLAT_RATE':
      return <Money label="Price per visit" value={rule.flatRate} onChange={(flatRate) => change({ flatRate })} required />
    case 'TIERED':
      return <Bands bands={rule.bands ?? []} onChange={(bands) => change({ bands })} />
    case 'NIGHT_WEEKEND':
      return (
        <>
          <div className="grid gap-4 sm:grid-cols-2">
            <Money label="Per hour, daytime" value={rule.dayRate} onChange={(dayRate) => change({ dayRate })} required />
            <Money
              label="Per hour, night and weekend"
              value={rule.nightWeekendRate}
              onChange={(nightWeekendRate) => change({ nightWeekendRate })}
              required
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <label className={field.label}>
              Night starts at
              <input
                type="time"
                required
                className={`${field.input} tabular`}
                value={rule.nightStart ?? ''}
                onChange={(e) => change({ nightStart: e.target.value })}
              />
            </label>
            <label className={field.label}>
              Night ends at
              <input
                type="time"
                required
                className={`${field.input} tabular`}
                value={rule.nightEnd ?? ''}
                onChange={(e) => change({ nightEnd: e.target.value })}
              />
            </label>
          </div>
          <p className={field.hint}>Saturdays and Sundays use the night price all day.</p>
        </>
      )
  }
}

/** Tiered bands: each one runs from its start hour until the next band starts. */
function Bands({ bands, onChange }: { bands: PriceBand[]; onChange: (bands: PriceBand[]) => void }) {
  function set(i: number, band: Partial<PriceBand>) {
    onChange(bands.map((b, j) => (j === i ? { ...b, ...band } : b)))
  }
  function add() {
    const last = bands[bands.length - 1]
    onChange([
      ...bands,
      {
        fromHour: (last?.fromHour ?? -1) + 1,
        hourlyRate: last?.hourlyRate ?? NaN,
      },
    ])
  }
  return (
    <>
      <ol className="flex flex-col gap-3">
        {bands.map((band, i) => (
          <li key={i} className="flex items-end gap-3">
            <label className={`${field.label} w-28 shrink-0`}>
              From hour
              <input
                type="number"
                required
                min={i === 0 ? 0 : bands[i - 1].fromHour + 1}
                max={i === 0 ? 0 : 72}
                readOnly={i === 0}
                className={`${field.input} tabular ${i === 0 ? 'bg-concrete' : ''}`}
                value={band.fromHour}
                onChange={(e) => set(i, { fromHour: Number(e.target.value) })}
              />
            </label>
            <Money label="Per hour" value={band.hourlyRate} onChange={(hourlyRate) => set(i, { hourlyRate: hourlyRate ?? NaN })} required />
            {i > 0 && (
              <button type="button" className={`${button.small} mb-1`} onClick={() => onChange(bands.filter((_, j) => j !== i))}>
                Remove
              </button>
            )}
          </li>
        ))}
      </ol>
      {bands.length < MAX_BANDS && (
        <button type="button" className={`${button.small} self-start`} onClick={add}>
          Add price band
        </button>
      )}
      <p className={field.hint}>The first band starts when the driver enters. Each band runs until the next one starts.</p>
    </>
  )
}

function Extras({ rule, change }: { rule: PricingRule; change: Change }) {
  const surcharges = rule.surcharges ?? noSurcharges
  return (
    <fieldset className="flex flex-col gap-5">
      <legend className="text-lg font-semibold">Extras, for any rule</legend>
      {rule.strategy !== 'FLAT_RATE' && (
        <Row label="Charge per" hint="The stay is rounded up to whole blocks.">
          <Segmented
            label="Charge per"
            options={blocks}
            value={rule.billingIncrementMinutes ?? 60}
            onChange={(billingIncrementMinutes) => change({ billingIncrementMinutes })}
          />
        </Row>
      )}
      <label className={field.label}>
        Free if they leave within this many minutes
        <input
          type="number"
          min={0}
          max={120}
          className={`${field.input} tabular max-w-40`}
          value={rule.gracePeriodMinutes ?? 0}
          onChange={(e) =>
            change({
              gracePeriodMinutes: e.target.value === '' ? 0 : Number(e.target.value),
            })
          }
        />
        <span className={`${field.hint} block font-normal`}>0 means every stay is charged.</span>
      </label>
      <Money
        label="Daily maximum"
        hint="Leave empty for no maximum. Applies to each started 24 hours."
        value={rule.dailyCap}
        min={0.5}
        onChange={(dailyCap) => change({ dailyCap })}
        className="max-w-40"
      />
      <fieldset>
        <legend className={field.label}>Bay surcharges, in percent</legend>
        <div className="mt-1 grid grid-cols-2 gap-3 sm:grid-cols-4">
          {surchargeFields.map((f) => (
            <label key={f.key} className="block text-sm">
              {f.label}
              <input
                type="number"
                min={-100}
                max={200}
                step={5}
                className={`${field.input} tabular`}
                value={surcharges[f.key]}
                onChange={(e) =>
                  change({
                    surcharges: {
                      ...surcharges,
                      [f.key]: e.target.value === '' ? 0 : Number(e.target.value),
                    },
                  })
                }
              />
            </label>
          ))}
        </div>
        <p className={field.hint}>Added to the price for those bays. Use a negative number for a discount.</p>
      </fieldset>
    </fieldset>
  )
}

function Preview({ rule }: { rule: PricingRule }) {
  return (
    <div className="bg-surface p-4">
      <p className="text-sm text-ink-muted">Drivers see</p>
      <p className="max-w-prose font-semibold">{describePricing(rule)}</p>
      <table className="tabular mt-3 w-full text-sm">
        <caption className="sr-only">Example costs</caption>
        <tbody className="divide-y divide-hairline">
          {examples.map((ex) => {
            const start = new Date(ex.start)
            const amount = cost(rule, start, new Date(start.getTime() + ex.minutes * 60_000), ex.bay ?? standardBay)
            return (
              <tr key={ex.label}>
                <th scope="row" className="py-1.5 text-left font-normal">
                  {ex.label}
                </th>
                <td className="py-1.5 text-right font-semibold">{Number.isNaN(amount) ? 'Fill in the rates' : lei(amount)}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

function Row({ label, hint, children }: { label: string; hint: string; children: ReactNode }) {
  return (
    <div>
      <p className={field.label}>{label}</p>
      <div className="mt-1">{children}</div>
      <p className={field.hint}>{hint}</p>
    </div>
  )
}

function Money({
  label,
  hint,
  value,
  onChange,
  required = false,
  min = 0,
  className = '',
}: {
  label: string
  hint?: string
  value: number | undefined
  onChange: (value: number | undefined) => void
  required?: boolean
  min?: number
  className?: string
}) {
  return (
    <label className={`${field.label} min-w-0 flex-1`}>
      {label}, in lei
      <input
        type="number"
        min={min}
        step="0.5"
        required={required}
        className={`${field.input} tabular ${className}`}
        value={value == null || Number.isNaN(value) ? '' : value}
        onChange={(e) => onChange(e.target.value === '' ? undefined : Number(e.target.value))}
      />
      {hint && <span className={`${field.hint} block font-normal`}>{hint}</span>}
    </label>
  )
}

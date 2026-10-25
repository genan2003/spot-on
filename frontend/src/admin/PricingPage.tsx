import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { useLocation, useUpdatePricing } from '../api/queries'
import type { PricingRule, PricingStrategyType } from '../api/types'
import { BackIcon } from '../components/icons'
import { QueryState } from '../components/QueryState'
import { describePricing, lei, strategyNames } from '../lib/format'
import { cost } from '../lib/pricing'
import { button, field } from '../lib/ui'

// One entry per PricingStrategy implementation in the backend.
const strategies: { type: PricingStrategyType; explains: string; fields: { key: keyof PricingRule; label: string }[] }[] = [
  { type: 'HOURLY', explains: 'Every started hour costs the same.', fields: [{ key: 'hourlyRate', label: 'Price per hour' }] },
  { type: 'FLAT_RATE', explains: 'One price per visit, however long the driver stays.', fields: [{ key: 'flatRate', label: 'Price per visit' }] },
  {
    type: 'TIERED',
    explains: 'The first hour has its own price; every later hour costs the second price.',
    fields: [
      { key: 'firstHourRate', label: 'First hour' },
      { key: 'laterHourRate', label: 'Each later hour' },
    ],
  },
  {
    type: 'NIGHT_WEEKEND',
    explains: 'A day price per hour, and a different one from 20:00 to 08:00 and on weekends.',
    fields: [
      { key: 'dayRate', label: 'Per hour, daytime' },
      { key: 'nightWeekendRate', label: 'Per hour, night and weekend' },
    ],
  },
]

/** Example stays shown under the form so the admin sees what drivers would pay. */
const examples = [
  { label: '45 min on a weekday afternoon', start: '2026-03-04T14:00', minutes: 45 },
  { label: '2 h 30 min on a weekday afternoon', start: '2026-03-04T14:00', minutes: 150 },
  { label: '3 h on a Saturday', start: '2026-03-07T11:00', minutes: 180 },
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

function PricingForm({ locationId, initial }: { locationId: string; initial: PricingRule }) {
  const [rule, setRule] = useState<PricingRule>(initial)
  const [saved, setSaved] = useState(false)
  const update = useUpdatePricing(locationId)
  const strategy = strategies.find((s) => s.type === rule.strategy)!

  function submit(e: FormEvent) {
    e.preventDefault()
    update.mutate(rule, { onSuccess: () => setSaved(true) })
  }

  return (
    <form onSubmit={submit} className="mt-6 grid max-w-4xl gap-8 lg:grid-cols-2">
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
                  setRule({ strategy: s.type })
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

      <div className="flex flex-col gap-4">
        {strategy.fields.map((f) => (
          <label key={f.key} className={field.label}>
            {f.label}, in lei
            <input
              type="number"
              min={0}
              step="0.5"
              required
              className={`${field.input} tabular`}
              value={(rule[f.key] as number | undefined) ?? ''}
              onChange={(e) => {
                setRule({ ...rule, [f.key]: e.target.value === '' ? undefined : Number(e.target.value) })
                setSaved(false)
              }}
            />
          </label>
        ))}

        <div className="bg-surface p-4">
          <p className="text-sm text-ink-muted">Drivers see</p>
          <p className="font-semibold">{describePricing(rule)}</p>
          <table className="tabular mt-3 w-full text-sm">
            <caption className="sr-only">Example costs</caption>
            <tbody className="divide-y divide-hairline">
              {examples.map((ex) => {
                const start = new Date(ex.start)
                return (
                  <tr key={ex.label}>
                    <th scope="row" className="py-1.5 text-left font-normal">
                      {ex.label}
                    </th>
                    <td className="py-1.5 text-right font-semibold">{lei(cost(rule, start, new Date(start.getTime() + ex.minutes * 60_000)))}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>

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

import { useState, type FormEvent } from 'react'
import { useUpdateSpots } from '../api/queries'
import type { Spot, SpotPropertyChanges, SpotType } from '../api/types'
import { spotTypeNames } from '../lib/format'
import { button, field } from '../lib/ui'

const spotTypes: SpotType[] = ['STANDARD', 'COMPACT', 'LARGE', 'MOTORCYCLE']

type Choice = 'keep' | 'yes' | 'no'

/**
 * Edits type, EV charging and accessible for one spot (plain controls showing its current values) or for
 * several at once (each property can be left as it is on every spot). Only what the admin changed is sent.
 */
export function SpotPropertiesForm({ locationId, spots }: { locationId: string; spots: Spot[] }) {
  return spots.length === 1 ? (
    <SingleSpotForm key={spots[0].id} locationId={locationId} spot={spots[0]} />
  ) : (
    <SeveralSpotsForm locationId={locationId} spots={spots} />
  )
}

function SingleSpotForm({ locationId, spot }: { locationId: string; spot: Spot }) {
  const update = useUpdateSpots(locationId)
  const [type, setType] = useState(spot.type)
  const [evCharging, setEvCharging] = useState(spot.evCharging)
  const [accessible, setAccessible] = useState(spot.accessible)
  const [saved, setSaved] = useState(false)

  const changes: SpotPropertyChanges = {
    type: type !== spot.type ? type : undefined,
    evCharging: evCharging !== spot.evCharging ? evCharging : undefined,
    accessible: accessible !== spot.accessible ? accessible : undefined,
  }
  const dirty = Object.values(changes).some((v) => v !== undefined)

  function submit(e: FormEvent) {
    e.preventDefault()
    update.mutate({ spotIds: [spot.id], changes }, { onSuccess: () => setSaved(true) })
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-3" aria-label={`Properties of spot ${spot.code}`}>
      <label className={field.label}>
        Type
        <select
          className={field.input}
          value={type}
          onChange={(e) => {
            setType(e.target.value as SpotType)
            setSaved(false)
          }}
        >
          {spotTypes.map((t) => (
            <option key={t} value={t}>
              {spotTypeNames[t]}
            </option>
          ))}
        </select>
      </label>
      <div className="flex gap-6">
        <Checkbox
          label="EV charging"
          checked={evCharging}
          onChange={(v) => {
            setEvCharging(v)
            setSaved(false)
          }}
        />
        <Checkbox
          label="Accessible"
          checked={accessible}
          onChange={(v) => {
            setAccessible(v)
            setSaved(false)
          }}
        />
      </div>
      <button type="submit" className={button.secondary} disabled={!dirty || update.isPending}>
        {update.isPending ? 'Saving changes…' : 'Save changes'}
      </button>
      <p role="status" className="text-sm">
        {update.error ? update.error.message : saved && !dirty ? `Spot ${spot.code} saved.` : ''}
      </p>
    </form>
  )
}

function SeveralSpotsForm({ locationId, spots }: { locationId: string; spots: Spot[] }) {
  const update = useUpdateSpots(locationId)
  const [type, setType] = useState<SpotType | 'keep'>('keep')
  const [evCharging, setEvCharging] = useState<Choice>('keep')
  const [accessible, setAccessible] = useState<Choice>('keep')
  const [saved, setSaved] = useState<string>()

  const changes: SpotPropertyChanges = {
    type: type === 'keep' ? undefined : type,
    evCharging: evCharging === 'keep' ? undefined : evCharging === 'yes',
    accessible: accessible === 'keep' ? undefined : accessible === 'yes',
  }
  const dirty = Object.values(changes).some((v) => v !== undefined)

  function submit(e: FormEvent) {
    e.preventDefault()
    const count = spots.length
    update.mutate(
      { spotIds: spots.map((s) => s.id), changes },
      {
        onSuccess: () => {
          setSaved(`Changes saved to ${count} spots.`)
          setType('keep')
          setEvCharging('keep')
          setAccessible('keep')
        },
      },
    )
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-3" aria-label="Properties of the selected spots">
      <label className={field.label}>
        Type
        <select
          className={field.input}
          value={type}
          onChange={(e) => {
            setType(e.target.value as SpotType | 'keep')
            setSaved(undefined)
          }}
        >
          <option value="keep">Keep each spot’s type</option>
          {spotTypes.map((t) => (
            <option key={t} value={t}>
              {spotTypeNames[t]}
            </option>
          ))}
        </select>
      </label>
      <ChoiceSelect
        label="EV charging"
        value={evCharging}
        onChange={(v) => {
          setEvCharging(v)
          setSaved(undefined)
        }}
      />
      <ChoiceSelect
        label="Accessible"
        value={accessible}
        onChange={(v) => {
          setAccessible(v)
          setSaved(undefined)
        }}
      />
      <button type="submit" className={button.primary} disabled={!dirty || update.isPending}>
        {update.isPending ? 'Saving changes…' : `Save changes to ${spots.length} spots`}
      </button>
      <p role="status" className="text-sm">
        {update.error ? update.error.message : saved}
      </p>
    </form>
  )
}

function Checkbox({ label, checked, onChange }: { label: string; checked: boolean; onChange: (checked: boolean) => void }) {
  return (
    <label className="flex min-h-11 items-center gap-2">
      <input type="checkbox" className="size-5 accent-sign" checked={checked} onChange={(e) => onChange(e.target.checked)} /> {label}
    </label>
  )
}

function ChoiceSelect({ label, value, onChange }: { label: string; value: Choice; onChange: (value: Choice) => void }) {
  return (
    <label className={field.label}>
      {label}
      <select className={field.input} value={value} onChange={(e) => onChange(e.target.value as Choice)}>
        <option value="keep">Keep as it is on each spot</option>
        <option value="yes">Yes, on every selected spot</option>
        <option value="no">No, on every selected spot</option>
      </select>
    </label>
  )
}

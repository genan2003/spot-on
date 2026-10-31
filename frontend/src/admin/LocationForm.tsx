import { useState, type FormEvent } from 'react'
import type { NewLocation } from '../api/types'
import { button, field } from '../lib/ui'

/** Name, address, map position and opening hours of a car park; used to add one and to edit its details. */
export function LocationForm({
  initial,
  submitLabel,
  pendingLabel,
  pending,
  error,
  onSubmit,
}: {
  initial?: Partial<NewLocation>
  submitLabel: string
  pendingLabel: string
  pending: boolean
  error: Error | null
  onSubmit: (location: NewLocation) => void
}) {
  const [allDay, setAllDay] = useState(!initial?.opensAt)
  const [hoursError, setHoursError] = useState('')

  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    const opensAt = allDay ? null : String(form.get('opensAt'))
    const closesAt = allDay ? null : String(form.get('closesAt'))
    if (opensAt !== null && opensAt === closesAt) {
      setHoursError('Opening and closing time are the same. For a car park that never closes, choose “Open around the clock”.')
      return
    }
    setHoursError('')
    onSubmit({
      name: String(form.get('name')).trim(),
      address: String(form.get('address')).trim(),
      latitude: Number(form.get('latitude')),
      longitude: Number(form.get('longitude')),
      opensAt,
      closesAt,
    })
  }

  return (
    <form onSubmit={submit} className="flex max-w-md flex-col gap-4">
      <label className={field.label}>
        Name
        <input name="name" required maxLength={80} defaultValue={initial?.name} className={field.input} />
      </label>
      <label className={field.label}>
        Address
        <input name="address" required maxLength={120} defaultValue={initial?.address} className={field.input} />
      </label>
      <div className="grid grid-cols-2 gap-3">
        <label className={field.label}>
          Latitude
          <input
            name="latitude"
            type="number"
            step="any"
            required
            min={-90}
            max={90}
            defaultValue={initial?.latitude ?? 46.7712}
            className={field.input}
          />
        </label>
        <label className={field.label}>
          Longitude
          <input
            name="longitude"
            type="number"
            step="any"
            required
            min={-180}
            max={180}
            defaultValue={initial?.longitude ?? 23.6236}
            className={field.input}
          />
        </label>
      </div>
      <p className={field.hint}>Drivers see the car park at this point on the map.</p>
      <fieldset className="flex flex-col gap-3">
        <legend className={field.label}>Opening hours</legend>
        <label className="flex min-h-11 items-center gap-3 text-base">
          <input type="checkbox" className="size-5 accent-sign" checked={allDay} onChange={(e) => setAllDay(e.target.checked)} />
          Open around the clock
        </label>
        {!allDay && (
          <>
            <div className="grid grid-cols-2 gap-3">
              <label className={field.label}>
                Opens at
                <input name="opensAt" type="time" required defaultValue={initial?.opensAt ?? '07:00'} className={`${field.input} tabular`} />
              </label>
              <label className={field.label}>
                Closes at
                <input name="closesAt" type="time" required defaultValue={initial?.closesAt ?? '22:00'} className={`${field.input} tabular`} />
              </label>
            </div>
            <p className={field.hint}>
              Car park time (Bucharest). Drivers can reserve and park only while it is open, and can always leave. If it
              closes after midnight, give the next morning’s time, e.g. 22:00 to 06:00.
            </p>
          </>
        )}
        {hoursError && (
          <p role="alert" className="border-l-4 border-occupied pl-3 text-sm font-semibold">
            {hoursError}
          </p>
        )}
      </fieldset>
      <button type="submit" className={button.primary} disabled={pending}>
        {pending ? pendingLabel : submitLabel}
      </button>
      {error && (
        <p role="alert" className="border-l-4 border-occupied pl-3 font-semibold">
          {error.message}
        </p>
      )}
    </form>
  )
}

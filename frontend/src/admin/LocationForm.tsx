import type { FormEvent } from 'react'
import type { NewLocation } from '../api/types'
import { button, field } from '../lib/ui'

/** Name, address and map position of a car park; used to add one and to edit its details. */
export function LocationForm({
  initial,
  submitLabel,
  pendingLabel,
  pending,
  error,
  onSubmit,
}: {
  initial?: NewLocation
  submitLabel: string
  pendingLabel: string
  pending: boolean
  error: Error | null
  onSubmit: (location: NewLocation) => void
}) {
  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    onSubmit({
      name: String(form.get('name')).trim(),
      address: String(form.get('address')).trim(),
      latitude: Number(form.get('latitude')),
      longitude: Number(form.get('longitude')),
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

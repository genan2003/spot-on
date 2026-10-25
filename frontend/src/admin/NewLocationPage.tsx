import type { FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { useCreateLocation } from '../api/queries'
import { button, field } from '../lib/ui'

export function NewLocationPage() {
  const navigate = useNavigate()
  const create = useCreateLocation()

  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    create.mutate(
      {
        name: String(form.get('name')).trim(),
        address: String(form.get('address')).trim(),
        latitude: Number(form.get('latitude')),
        longitude: Number(form.get('longitude')),
      },
      { onSuccess: (location) => navigate(`/admin/locations/${location.id}`) },
    )
  }

  return (
    <div className="p-4 lg:p-8">
      <h1 className="text-2xl font-extrabold tracking-tight">Add car park</h1>
      <p className="mt-1 max-w-prose text-ink-muted">After saving, add its spots and set its pricing.</p>
      <form onSubmit={submit} className="mt-6 flex max-w-md flex-col gap-4">
        <label className={field.label}>
          Name
          <input name="name" required maxLength={80} className={field.input} />
        </label>
        <label className={field.label}>
          Address
          <input name="address" required maxLength={120} className={field.input} />
        </label>
        <div className="grid grid-cols-2 gap-3">
          <label className={field.label}>
            Latitude
            <input name="latitude" type="number" step="any" required min={-90} max={90} defaultValue={46.7712} className={field.input} />
          </label>
          <label className={field.label}>
            Longitude
            <input name="longitude" type="number" step="any" required min={-180} max={180} defaultValue={23.6236} className={field.input} />
          </label>
        </div>
        <p className={field.hint}>Drivers see the car park at this point on the map.</p>
        <button type="submit" className={button.primary} disabled={create.isPending}>
          {create.isPending ? 'Saving car park…' : 'Save car park'}
        </button>
        {create.error && (
          <p role="alert" className="border-l-4 border-occupied pl-3 font-semibold">
            {create.error.message}
          </p>
        )}
      </form>
    </div>
  )
}

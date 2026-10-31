import { useNavigate } from 'react-router'
import { useCreateLocation } from '../api/queries'
import { LocationForm } from './LocationForm'

export function NewLocationPage() {
  const navigate = useNavigate()
  const create = useCreateLocation()

  return (
    <div className="p-4 lg:p-8">
      <h1 className="text-2xl font-extrabold tracking-tight">Add car park</h1>
      <p className="mt-1 max-w-prose text-ink-muted">After saving, add its spots and set its pricing.</p>
      <div className="mt-6">
        <LocationForm
          submitLabel="Save car park"
          pendingLabel="Saving car park…"
          pending={create.isPending}
          error={create.error}
          onSubmit={(location) => create.mutate(location, { onSuccess: (saved) => navigate(`/admin/locations/${saved.id}`) })}
        />
      </div>
    </div>
  )
}

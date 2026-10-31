import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useDeleteLocation, useLocation, useUpdateLocation } from '../api/queries'
import type { ParkingLocation } from '../api/types'
import { BackIcon } from '../components/icons'
import { QueryState } from '../components/QueryState'
import { button } from '../lib/ui'
import { LocationForm } from './LocationForm'

/** Edit a car park's name, address and map position, or delete it with all its spots. */
export function EditLocationPage() {
  const { id = '' } = useParams()
  const location = useLocation(id)
  return (
    <div className="p-4 lg:p-8">
      <Link to={`/admin/locations/${id}`} className="inline-flex min-h-11 items-center gap-1 font-semibold text-sign hover:underline">
        <BackIcon className="size-5" /> Back to car park
      </Link>
      <QueryState query={location} loading="Loading car park…">
        {(loc) => <EditLocation key={loc.id} location={loc} />}
      </QueryState>
    </div>
  )
}

function EditLocation({ location }: { location: ParkingLocation }) {
  const navigate = useNavigate()
  const update = useUpdateLocation(location.id)
  const [saved, setSaved] = useState(false)

  return (
    <>
      <h1 className="text-2xl font-extrabold tracking-tight">Details of {location.name}</h1>
      <div className="mt-6">
        <LocationForm
          initial={location}
          submitLabel="Save details"
          pendingLabel="Saving details…"
          pending={update.isPending}
          error={update.error}
          onSubmit={(changes) => update.mutate(changes, { onSuccess: () => setSaved(true) })}
        />
        <p role="status" className="mt-3 text-sm font-semibold">
          {saved && !update.isPending ? 'Details saved. Drivers see them from now on.' : ''}
        </p>
      </div>
      <DeleteLocation location={location} onDeleted={() => navigate('/admin')} />
    </>
  )
}

function DeleteLocation({ location, onDeleted }: { location: ParkingLocation; onDeleted: () => void }) {
  const remove = useDeleteLocation()
  const [confirming, setConfirming] = useState(false)

  return (
    <section aria-labelledby="delete-location-title" className="mt-10 max-w-md border-t border-hairline pt-6">
      <h2 id="delete-location-title" className="text-lg font-semibold">
        Delete car park
      </h2>
      <p className="mt-1 text-sm text-ink-muted">
        Removes the car park and its {location.totalSpots} spots from Spot On. Past sessions and payments stay on drivers’ receipts.
      </p>
      {!confirming ? (
        <button type="button" className={`${button.secondary} mt-4`} onClick={() => setConfirming(true)}>
          Delete car park
        </button>
      ) : (
        <div role="group" aria-labelledby="delete-location-warning" className="mt-4 border-l-4 border-reserved pl-3">
          <p id="delete-location-warning" className="max-w-prose text-sm">
            Delete {location.name} and its {location.totalSpots} spots? This can’t be undone.
          </p>
          <div className="mt-3 flex flex-wrap items-center gap-3">
            <button
              type="button"
              className={button.secondary}
              disabled={remove.isPending}
              onClick={() => remove.mutate(location.id, { onSuccess: onDeleted })}
            >
              {remove.isPending ? 'Deleting car park…' : `Delete ${location.name}`}
            </button>
            <button type="button" className={button.quiet} onClick={() => setConfirming(false)}>
              Keep it
            </button>
          </div>
        </div>
      )}
      {remove.error && (
        <p role="alert" className="mt-3 max-w-prose border-l-4 border-occupied pl-3 font-semibold">
          {remove.error.message}
        </p>
      )}
    </section>
  )
}

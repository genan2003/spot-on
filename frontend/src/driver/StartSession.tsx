import { useId, useState, type FormEvent } from 'react'
import { useStartSession } from '../api/queries'
import { isPlausiblePlate, lastPlate, normalizePlate, rememberPlate } from '../lib/plate'
import { button, field } from '../lib/ui'
import { ErrorLine } from './ReservationActions'

/**
 * "Start session", the same on My parking and in the spot sheet: the button opens the licence plate field
 * (filled in with the plate used last time), and starting the session sends it. The admin sees the plate on
 * the occupied bay.
 */
export function StartSession({
  spotId,
  reservationId,
  disabled = false,
  primary = true,
  onStarted,
}: {
  spotId: string
  reservationId?: string
  disabled?: boolean
  /** The spot sheet's free spot shows "Reserve spot" as the main action, so this one is secondary there. */
  primary?: boolean
  onStarted?: () => void
}) {
  const start = useStartSession()
  const [open, setOpen] = useState(false)
  const [plate, setPlate] = useState(lastPlate)
  const [invalid, setInvalid] = useState(false)
  const id = useId()

  if (!open) {
    return (
      <button type="button" className={primary ? button.primary : button.secondary} disabled={disabled} onClick={() => setOpen(true)}>
        Start session
      </button>
    )
  }

  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (!isPlausiblePlate(plate)) {
      setInvalid(true)
      return
    }
    const licencePlate = normalizePlate(plate)
    start.mutate(
      { spotId, reservationId, licencePlate },
      {
        onSuccess: () => {
          rememberPlate(licencePlate)
          onStarted?.()
        },
      },
    )
  }

  return (
    <form onSubmit={submit} noValidate className="w-full max-w-sm basis-full">
      <label htmlFor={id} className={field.label}>
        Licence plate
      </label>
      <input
        id={id}
        className={`${field.input} tabular font-bold tracking-wide uppercase aria-invalid:border-2 aria-invalid:border-occupied`}
        value={plate}
        onChange={(e) => {
          setPlate(e.target.value)
          setInvalid(false)
        }}
        autoFocus
        autoComplete="off"
        autoCapitalize="characters"
        spellCheck={false}
        maxLength={14}
        aria-invalid={invalid}
        aria-describedby={`${id}-hint`}
      />
      <p id={`${id}-hint`} role={invalid ? 'alert' : undefined} className={invalid ? 'mt-1 text-sm font-semibold' : field.hint}>
        {invalid ? 'Enter the plate as it is on the car, e.g. CJ 07 ABC.' : 'The car park staff see it on your spot while you’re parked.'}
      </p>
      <div className="mt-3 flex flex-wrap items-center gap-3">
        <button type="submit" className={primary ? button.primary : button.secondary} disabled={start.isPending || disabled}>
          {start.isPending ? 'Starting session…' : 'Start session'}
        </button>
        <button type="button" className={button.quiet} onClick={() => setOpen(false)}>
          Not now
        </button>
      </div>
      <ErrorLine error={start.error} />
    </form>
  )
}

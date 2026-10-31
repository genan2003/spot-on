import { DEMO_DRIVERS, DRIVER_ID, driverName, switchDriver } from '../api/driver'
import { Segmented } from '../components/Segmented'

/**
 * Demo only, as there is no login: which driver this tab is. Open a second tab as the other driver to show
 * two drivers going for the same spot; only one of them gets it.
 */
export function DriverSwitch() {
  return (
    <section className="mt-10 border-t border-hairline pt-4" aria-labelledby="driver-switch-title">
      <p id="driver-switch-title" className="font-semibold">
        You are
      </p>
      <div className="mt-2">
        <Segmented
          label="You are"
          options={DEMO_DRIVERS.map((id) => ({ value: id, label: driverName(id) }))}
          value={DRIVER_ID}
          onChange={(id) => id !== DRIVER_ID && switchDriver(id)}
        />
      </div>
      <p className="mt-2 max-w-prose text-ink-muted">
        A demo without sign-in: each browser tab can be a different driver. Switching reloads this tab only.
      </p>
    </section>
  )
}

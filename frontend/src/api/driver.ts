/**
 * No login in this proof of concept: every request acts as a demo driver. Each browser tab can be a different one
 * (kept in sessionStorage, which is per tab), so two drivers can try for the same spot side by side. A link with
 * `?driver=driver-2` opens a tab as that driver. Read once at start-up; switching reloads the app, so cached data,
 * the live notification stream and the gateway's rate-limit key all follow the new driver.
 */
export const DEMO_DRIVERS = ['driver-1', 'driver-2'] as const
export type DemoDriver = (typeof DEMO_DRIVERS)[number]

const KEY = 'spot-on.driver'
const isDemoDriver = (id: string | null): id is DemoDriver => DEMO_DRIVERS.includes(id as DemoDriver)

function readDriver(): DemoDriver {
  const url = new URL(window.location.href)
  const fromLink = url.searchParams.get('driver')
  if (isDemoDriver(fromLink)) {
    try {
      sessionStorage.setItem(KEY, fromLink)
    } catch {
      // Storage blocked: this page load still uses the driver from the link.
    }
    url.searchParams.delete('driver')
    window.history.replaceState(window.history.state, '', url)
    return fromLink
  }
  try {
    const stored = sessionStorage.getItem(KEY)
    if (isDemoDriver(stored)) return stored
  } catch {
    // Storage blocked: fall back to the first driver.
  }
  return DEMO_DRIVERS[0]
}

export const DRIVER_ID: DemoDriver = readDriver()

/** "Driver 1" for driver-1. */
export function driverName(id: DemoDriver): string {
  return `Driver ${id.slice(id.lastIndexOf('-') + 1)}`
}

/** Reloads the app as another driver, in this tab only. */
export function switchDriver(id: DemoDriver) {
  const url = new URL(window.location.href)
  url.searchParams.set('driver', id)
  window.location.assign(url)
}

/** localStorage key for something remembered per driver in this browser (seen count, last plate, closed cards). */
export function driverKey(name: string): string {
  return `spot-on.${DRIVER_ID}.${name}`
}

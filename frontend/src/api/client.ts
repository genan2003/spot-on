import { DRIVER_ID } from './driver'
import { ApiError } from './errors'
import type {
  CurrentParking,
  NewLocation,
  NewSpots,
  Notification,
  ParkingLocation,
  ParkingSession,
  Payment,
  PricingRule,
  Reservation,
  Spot,
  SpotPropertyChanges,
  SpotStatus,
} from './types'

export const USE_MOCKS = import.meta.env.VITE_USE_MOCKS === 'true'

// Used when a service sends no ProblemDetail (e.g. an endpoint that doesn't exist yet).
function fallbackMessage(status: number, retryAfter: string | null): string {
  // The gateway's rate limit answers 429 with no body; Retry-After says when the next attempt is allowed.
  if (status === 429) {
    const seconds = Number(retryAfter)
    const wait = seconds > 0 ? `Wait ${seconds} ${seconds === 1 ? 'second' : 'seconds'}` : 'Wait a moment'
    return `You tried to reserve too many times in a row. ${wait}, then try again.`
  }
  if (status === 404) return 'Spot On couldn’t find this. It may have been removed, or this part isn’t available yet.'
  if (status >= 500) return 'Something went wrong on the server. Try again in a moment.'
  return 'Spot On couldn’t complete that request. Try again.'
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(`/api/v1${path}`, {
      ...init,
      headers: { 'Content-Type': 'application/json', 'X-Driver-Id': DRIVER_ID, ...init?.headers },
    })
  } catch {
    throw new ApiError(0, 'Spot On can’t reach the server. Check your connection and try again.')
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    throw new ApiError(
      response.status,
      problem?.detail ?? fallbackMessage(response.status, response.headers.get('Retry-After')),
    )
  }
  return response.status === 204 ? (undefined as T) : response.json()
}

const json = (method: string, body?: unknown): RequestInit => ({
  method,
  body: body === undefined ? undefined : JSON.stringify(body),
})

export interface Api {
  listLocations(): Promise<ParkingLocation[]>
  getLocation(id: string): Promise<ParkingLocation>
  listSpots(locationId: string): Promise<Spot[]>
  createLocation(location: NewLocation): Promise<ParkingLocation>
  updateLocation(id: string, location: NewLocation): Promise<ParkingLocation>
  /** Fails with 409 while drivers use any of its spots; the message names them. */
  deleteLocation(id: string): Promise<void>
  /** Only a free or closed spot can be deleted. */
  deleteSpot(locationId: string, spotId: string): Promise<void>
  addSpots(locationId: string, spots: NewSpots): Promise<Spot[]>
  setSpotStatus(locationId: string, spotId: string, status: SpotStatus): Promise<Spot>
  /** Changes the properties of one or more spots of a location. */
  updateSpots(locationId: string, spotIds: string[], changes: SpotPropertyChanges): Promise<Spot[]>
  updatePricing(locationId: string, rule: PricingRule): Promise<ParkingLocation>
  getCurrentParking(): Promise<CurrentParking>
  reserveSpot(spotId: string): Promise<Reservation>
  /** Moves the reservation to another free spot; the hold time stays the same. */
  moveReservation(id: string, spotId: string): Promise<Reservation>
  /** Holds the spot one more hold time; allowed once. */
  extendReservation(id: string): Promise<Reservation>
  cancelReservation(id: string): Promise<void>
  startSession(spotId: string, reservationId?: string): Promise<ParkingSession>
  endSession(id: string): Promise<ParkingSession>
  retryPayment(paymentId: string): Promise<void>
  listNotifications(): Promise<Notification[]>
  /** The driver's last 50 reservations, newest first. */
  listReservations(): Promise<Reservation[]>
  /** The driver's last 50 sessions, newest first. */
  listSessions(): Promise<ParkingSession[]>
  /** The driver's payments, newest first. */
  listPayments(): Promise<Payment[]>
}

const httpApi: Api = {
  listLocations: () => request('/parking-locations'),
  getLocation: (id) => request(`/parking-locations/${id}`),
  listSpots: (locationId) => request(`/parking-locations/${locationId}/spots`),
  createLocation: (location) => request('/parking-locations', json('POST', location)),
  updateLocation: (id, location) => request(`/parking-locations/${id}`, json('PUT', location)),
  deleteLocation: (id) => request(`/parking-locations/${id}`, json('DELETE')),
  deleteSpot: (locationId, spotId) => request(`/parking-locations/${locationId}/spots/${spotId}`, json('DELETE')),
  addSpots: (locationId, spots) => request(`/parking-locations/${locationId}/spots`, json('POST', spots)),
  setSpotStatus: (locationId, spotId, status) =>
    request(`/parking-locations/${locationId}/spots/${spotId}`, json('PATCH', { status })),
  updateSpots: (locationId, spotIds, changes) =>
    spotIds.length === 1
      ? request<Spot>(`/parking-locations/${locationId}/spots/${spotIds[0]}`, json('PATCH', changes)).then((spot) => [spot])
      : request(`/parking-locations/${locationId}/spots`, json('PATCH', { spotIds, ...changes })),
  updatePricing: (locationId, rule) => request(`/parking-locations/${locationId}/pricing-rule`, json('PUT', rule)),
  getCurrentParking: () => request(`/reservations/current?driverId=${DRIVER_ID}`),
  reserveSpot: (spotId) => request('/reservations', json('POST', { spotId, driverId: DRIVER_ID })),
  moveReservation: (id, spotId) => request(`/reservations/${id}`, json('PATCH', { spotId })),
  extendReservation: (id) => request(`/reservations/${id}/extend`, json('POST')),
  cancelReservation: (id) => request(`/reservations/${id}`, json('DELETE')),
  startSession: (spotId, reservationId) =>
    request('/parking-sessions', json('POST', { spotId, reservationId, driverId: DRIVER_ID })),
  endSession: (id) => request(`/parking-sessions/${id}/end`, json('POST')),
  retryPayment: (paymentId) => request(`/payments/${paymentId}/retry`, json('POST')),
  listNotifications: () => request(`/notifications?driverId=${DRIVER_ID}`),
  listReservations: () => request(`/reservations?driverId=${DRIVER_ID}`),
  listSessions: () => request(`/parking-sessions?driverId=${DRIVER_ID}`),
  listPayments: () => request(`/payments?driverId=${DRIVER_ID}`),
}

// The demo data module is only loaded when mocks are on, so it never ships to a real deployment.
const mockApi = new Proxy({} as Api, {
  get:
    (_, method: keyof Api) =>
    async (...args: unknown[]) => {
      const { mockApi } = await import('./mock')
      return (mockApi[method] as (...a: unknown[]) => unknown)(...args)
    },
})

export const api: Api = USE_MOCKS ? mockApi : httpApi

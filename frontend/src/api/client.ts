import { DRIVER_ID } from './driver'
import { ApiError } from './errors'
import type {
  CurrentParking,
  NewLocation,
  NewSpots,
  Notification,
  ParkingLocation,
  ParkingSession,
  PricingRule,
  Reservation,
  Spot,
  SpotStatus,
} from './types'

export const USE_MOCKS = import.meta.env.VITE_USE_MOCKS === 'true'

// Used when a service sends no ProblemDetail (e.g. an endpoint that doesn't exist yet).
function fallbackMessage(status: number): string {
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
    throw new ApiError(response.status, problem?.detail ?? fallbackMessage(response.status))
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
  addSpots(locationId: string, spots: NewSpots): Promise<Spot[]>
  setSpotStatus(locationId: string, spotId: string, status: SpotStatus): Promise<Spot>
  updatePricing(locationId: string, rule: PricingRule): Promise<ParkingLocation>
  getCurrentParking(): Promise<CurrentParking>
  reserveSpot(spotId: string): Promise<Reservation>
  cancelReservation(id: string): Promise<void>
  startSession(spotId: string, reservationId?: string): Promise<ParkingSession>
  endSession(id: string): Promise<ParkingSession>
  retryPayment(paymentId: string): Promise<void>
  listNotifications(): Promise<Notification[]>
}

const httpApi: Api = {
  listLocations: () => request('/parking-locations'),
  getLocation: (id) => request(`/parking-locations/${id}`),
  listSpots: (locationId) => request(`/parking-locations/${locationId}/spots`),
  createLocation: (location) => request('/parking-locations', json('POST', location)),
  addSpots: (locationId, spots) => request(`/parking-locations/${locationId}/spots`, json('POST', spots)),
  setSpotStatus: (locationId, spotId, status) =>
    request(`/parking-locations/${locationId}/spots/${spotId}`, json('PATCH', { status })),
  updatePricing: (locationId, rule) => request(`/parking-locations/${locationId}/pricing-rule`, json('PUT', rule)),
  getCurrentParking: () => request(`/reservations/current?driverId=${DRIVER_ID}`),
  reserveSpot: (spotId) => request('/reservations', json('POST', { spotId, driverId: DRIVER_ID })),
  cancelReservation: (id) => request(`/reservations/${id}`, json('DELETE')),
  startSession: (spotId, reservationId) =>
    request('/parking-sessions', json('POST', { spotId, reservationId, driverId: DRIVER_ID })),
  endSession: (id) => request(`/parking-sessions/${id}/end`, json('POST')),
  retryPayment: (paymentId) => request(`/payments/${paymentId}/retry`, json('POST')),
  listNotifications: () => request(`/notifications?driverId=${DRIVER_ID}`),
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

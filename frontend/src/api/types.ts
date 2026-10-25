// Shapes of the gateway's REST responses (/api/v1/...). Keep in sync with the services' DTOs.

export type SpotStatus = 'AVAILABLE' | 'RESERVED' | 'OCCUPIED' | 'OUT_OF_SERVICE'
export type SpotType = 'STANDARD' | 'COMPACT' | 'LARGE' | 'MOTORCYCLE'
export type PricingStrategyType = 'HOURLY' | 'FLAT_RATE' | 'TIERED' | 'NIGHT_WEEKEND'

/** Mirrors the Strategy pattern: one rule per location, with the fields its strategy needs. */
export interface PricingRule {
  strategy: PricingStrategyType
  hourlyRate?: number
  flatRate?: number
  firstHourRate?: number
  laterHourRate?: number
  dayRate?: number
  nightWeekendRate?: number
}

export interface ParkingLocation {
  id: string
  name: string
  address: string
  latitude: number
  longitude: number
  totalSpots: number
  freeSpots: number
  pricing: PricingRule
}

export interface Spot {
  id: string
  locationId: string
  code: string
  level: number
  zone: string
  type: SpotType
  evCharging: boolean
  accessible: boolean
  status: SpotStatus
  /** Set when status is RESERVED or OCCUPIED, so the driver can recognise their own spot. */
  heldBy?: string
}

export type ReservationStatus = 'PENDING' | 'ACTIVE' | 'CANCELLED' | 'EXPIRED' | 'COMPLETED'

export interface Reservation {
  id: string
  spotId: string
  spotCode: string
  locationId: string
  locationName: string
  status: ReservationStatus
  createdAt: string
  expiresAt: string
}

export type PaymentStatus = 'PENDING' | 'COMPLETED' | 'FAILED'

export interface ParkingSession {
  id: string
  spotId: string
  spotCode: string
  locationId: string
  locationName: string
  status: 'ACTIVE' | 'COMPLETED'
  startedAt: string
  endedAt?: string
  /** Running estimate while active, final cost once completed. */
  cost: number
  paymentId?: string
  paymentStatus?: PaymentStatus
}

/** What the driver currently holds: at most one reservation or session, plus the last receipt. */
export interface CurrentParking {
  reservation?: Reservation
  session?: ParkingSession
  lastCompleted?: ParkingSession
}

export interface Notification {
  id: string
  kind: 'RESERVATION_CREATED' | 'RESERVATION_CANCELLED' | 'RESERVATION_EXPIRED' | 'SESSION_COMPLETED' | 'PAYMENT_COMPLETED' | 'PAYMENT_FAILED'
  message: string
  sentAt: string
}

/** Payload of the spot.status-changed event pushed over WebSocket. */
export interface SpotStatusChanged {
  spotId: string
  locationId: string
  status: SpotStatus
  heldBy?: string
}

export interface NewLocation {
  name: string
  address: string
  latitude: number
  longitude: number
}

export interface NewSpots {
  level: number
  zone: string
  count: number
  type: SpotType
  evCharging: boolean
  accessible: boolean
}

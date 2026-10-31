// Shapes of the gateway's REST responses (/api/v1/...). Keep in sync with the services' DTOs.

export type SpotStatus = 'AVAILABLE' | 'RESERVED' | 'OCCUPIED' | 'OUT_OF_SERVICE'
export type SpotType = 'STANDARD' | 'COMPACT' | 'LARGE' | 'MOTORCYCLE'
export type PricingStrategyType = 'HOURLY' | 'FLAT_RATE' | 'TIERED' | 'NIGHT_WEEKEND'

/** Tiered pricing: from hour `fromHour` of the stay on, each hour costs `hourlyRate`. */
export interface PriceBand {
  fromHour: number
  hourlyRate: number
}

/** Percent added per bay type (negative for a discount), plus one for EV charging. */
export interface BaySurcharges {
  compact: number
  large: number
  motorcycle: number
  evCharging: number
}

export type BillingIncrement = 15 | 30 | 60

/**
 * Mirrors the Strategy pattern: one rule per location, with the fields its strategy needs, plus extras
 * for any strategy that the backend applies as decorators (Decorator pattern).
 */
export interface PricingRule {
  strategy: PricingStrategyType
  hourlyRate?: number
  flatRate?: number
  bands?: PriceBand[]
  dayRate?: number
  nightWeekendRate?: number
  /** "HH:mm", local time. Night and weekend pricing only. */
  nightStart?: string
  nightEnd?: string
  /** The stay is charged in whole blocks of this many minutes. Default 60. */
  billingIncrementMinutes?: BillingIncrement
  /** Stays this short are free. 0 = none. */
  gracePeriodMinutes?: number
  /** At most this much per started 24 hours. Absent = no maximum. */
  dailyCap?: number
  surcharges?: BaySurcharges
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

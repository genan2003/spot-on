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
  /** The parked car's plate while OCCUPIED (given at session start), for the admin. */
  licencePlate?: string
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
  /** A reservation can be extended once. */
  extended: boolean
  /** Set when cancelled: by the driver, or because an admin closed the spot. */
  cancelReason?: 'DRIVER' | 'SPOT_CLOSED'
  /** When it stopped holding the spot: cancelled, expired (= expiresAt) or used for a session. */
  endedAt?: string
}

export type PaymentStatus = 'PENDING' | 'COMPLETED' | 'FAILED'

export interface ParkingSession {
  id: string
  spotId: string
  spotCode: string
  locationId: string
  locationName: string
  licencePlate?: string
  status: 'ACTIVE' | 'COMPLETED'
  startedAt: string
  endedAt?: string
  /** Running estimate while active, final cost once completed. */
  cost: number
  paymentId?: string
  paymentStatus?: PaymentStatus
}

/**
 * What the driver currently holds: at most one reservation or session. Plus the last thing that ended,
 * never both: the last receipt, or a cancelled or expired reservation, whichever is newer (an unpaid
 * receipt always wins), and only for two hours.
 */
export type PaymentMethod = 'CARD' | 'WALLET'

/** The driver's chosen payment method (payment-service's Factory picks the processor from it) and simulated wallet. */
export interface PaymentAccount {
  driverId: string
  preferredMethod: PaymentMethod
  walletBalance: number
}

/** payment-service's record of a session's payment; a retry updates the same record. */
export interface Payment {
  id: string
  sessionId: string
  driverId: string
  amount: number
  method: PaymentMethod
  status: PaymentStatus
  /** Tries so far, including retries. */
  attempts: number
  createdAt: string
  processedAt?: string
}

/**
 * One day at one car park (reservation-service, car park’s local time). `hours` is the length of the day so far:
 * 24, or the hours since midnight for today.
 */
export interface DailyStats {
  /** YYYY-MM-DD */
  date: string
  hours: number
  /** Sessions that started that day. */
  sessions: number
  /** Spot-hours used that day. */
  occupiedHours: number
  /** Paid cost of the sessions that ended that day. */
  revenue: number
  /** Cost of the sessions that ended that day and aren't paid yet. */
  unpaid: number
}

export interface History {
  reservations: Reservation[]
  sessions: ParkingSession[]
  payments: Payment[]
}

export interface CurrentParking {
  reservation?: Reservation
  session?: ParkingSession
  lastCompleted?: ParkingSession
  lastEndedReservation?: Reservation
}

/** How notifications reach the driver; all three are simulated in notification-service's logs. */
export type NotificationChannel = 'PUSH' | 'SMS' | 'EMAIL'

/** The driver's chosen channel (notification-service's Factory picks the channel from it). */
export interface NotificationPreference {
  driverId: string
  channel: NotificationChannel
}

export interface Notification {
  id: string
  kind:
    | 'RESERVATION_CREATED'
    | 'RESERVATION_CHANGED'
    | 'RESERVATION_EXPIRING'
    | 'RESERVATION_CANCELLED'
    | 'RESERVATION_EXPIRED'
    | 'SESSION_COMPLETED'
    | 'PAYMENT_COMPLETED'
    | 'PAYMENT_FAILED'
  channelType: NotificationChannel
  message: string
  sentAt: string
}

/**
 * Payload of the spot.status-changed event pushed over WebSocket: the spot's live state, sent when its
 * status changes or an admin edits its properties.
 */
export interface SpotStatusChanged {
  spotId: string
  locationId: string
  status: SpotStatus
  heldBy?: string
  licencePlate?: string
  type?: SpotType
  evCharging?: boolean
  accessible?: boolean
}

/** Admin edit of a spot's properties; a missing field stays as it is. */
export interface SpotPropertyChanges {
  type?: SpotType
  evCharging?: boolean
  accessible?: boolean
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

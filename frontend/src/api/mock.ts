/*
 * In-memory stand-in for the gateway, used while VITE_USE_MOCKS=true.
 * It follows the same rules as the backend (one active reservation per spot, 15-minute expiry,
 * cost from the location's pricing rule, simulated payment with a failure rate) and simulates
 * other drivers so the live spot plan has something to show.
 */
import { timeOfDay } from '../lib/format'
import { cost, normalizeRule } from '../lib/pricing'
import type { Api } from './client'
import { DRIVER_ID } from './driver'
import { ApiError } from './errors'
import type {
  CurrentParking,
  Notification,
  ParkingLocation,
  ParkingSession,
  PricingRule,
  Reservation,
  Spot,
  SpotStatus,
  SpotStatusChanged,
} from './types'

const RESERVATION_MINUTES = 15
const PAYMENT_FAILURE_RATE = 0.2
const LATENCY_MS = 250

// Deterministic pseudo-random numbers so the demo car park looks the same on every reload.
let seed = 7
const random = () => {
  seed = (seed * 16807) % 2147483647
  return (seed - 1) / 2147483646
}

interface LevelPlan {
  level: number
  zones: string[]
  perZone: number
}

const locations: ParkingLocation[] = []
const spots: Spot[] = []

function addLocation(
  id: string,
  name: string,
  address: string,
  latitude: number,
  longitude: number,
  pricing: PricingRule,
  levels: LevelPlan[],
) {
  locations.push({ id, name, address, latitude, longitude, pricing: normalizeRule(pricing), totalSpots: 0, freeSpots: 0 })
  for (const { level, zones, perZone } of levels) {
    for (const zone of zones) {
      for (let n = 1; n <= perZone; n++) {
        const roll = random()
        const status: SpotStatus = roll < 0.08 ? 'OUT_OF_SERVICE' : roll < 0.2 ? 'RESERVED' : roll < 0.62 ? 'OCCUPIED' : 'AVAILABLE'
        spots.push({
          id: `${id}-${level}-${zone}${n}`,
          locationId: id,
          code: `${zone}${n}`,
          level,
          zone,
          type: n === perZone ? 'LARGE' : n === perZone - 1 ? 'COMPACT' : n === 1 && zone === zones[0] ? 'MOTORCYCLE' : 'STANDARD',
          evCharging: n <= 2 && zone !== zones[0],
          accessible: n === 2 && zone === zones[0],
          status,
          heldBy: status === 'RESERVED' || status === 'OCCUPIED' ? 'someone-else' : undefined,
        })
      }
    }
  }
}

const centralGaragePricing: PricingRule = {
  strategy: 'HOURLY',
  hourlyRate: 6,
  gracePeriodMinutes: 10,
  surcharges: { compact: -10, large: 20, motorcycle: -50, evCharging: 15 },
}

addLocation('central-garage', 'Central Garage', 'Strada Memorandumului 21', 46.7701, 23.5878, centralGaragePricing, [
  { level: 0, zones: ['A', 'B'], perZone: 10 },
  { level: -1, zones: ['A', 'B', 'C', 'D'], perZone: 10 },
])
addLocation('riverside-lot', 'Riverside Lot', 'Splaiul Independenței 4', 46.7741, 23.5949, { strategy: 'FLAT_RATE', flatRate: 15 }, [
  { level: 0, zones: ['A', 'B'], perZone: 12 },
])
addLocation(
  'marasti-park-ride',
  'Mărăști Park and Ride',
  'Strada Aurel Vlaicu 3',
  46.7795,
  23.6196,
  {
    strategy: 'TIERED',
    bands: [
      { fromHour: 0, hourlyRate: 4 },
      { fromHour: 1, hourlyRate: 2 },
    ],
    dailyCap: 20,
  },
  [{ level: 0, zones: ['A', 'B', 'C', 'D'], perZone: 8 }],
)

const reservations: Reservation[] = []
const sessions: ParkingSession[] = []
const notifications: Notification[] = []
const listeners = new Set<(event: SpotStatusChanged) => void>()

function counted(location: ParkingLocation): ParkingLocation {
  const own = spots.filter((s) => s.locationId === location.id)
  return { ...location, totalSpots: own.length, freeSpots: own.filter((s) => s.status === 'AVAILABLE').length }
}

function findLocation(id: string): ParkingLocation {
  const location = locations.find((l) => l.id === id)
  if (!location) throw new ApiError(404, 'This location no longer exists.')
  return location
}

function findSpot(id: string): Spot {
  const spot = spots.find((s) => s.id === id)
  if (!spot) throw new ApiError(404, 'This spot no longer exists.')
  return spot
}

function setStatus(spot: Spot, status: SpotStatus, heldBy?: string) {
  spot.status = status
  spot.heldBy = heldBy
  broadcast(spot)
}

/** Like spot.status-changed: the spot's live state goes to every subscriber. */
function broadcast(spot: Spot) {
  const { id: spotId, locationId, status, heldBy, type, evCharging, accessible } = spot
  listeners.forEach((listener) => listener({ spotId, locationId, status, heldBy, type, evCharging, accessible }))
}

function notify(kind: Notification['kind'], message: string) {
  notifications.unshift({ id: crypto.randomUUID(), kind, message, sentAt: new Date().toISOString() })
}

function processPayment(session: ParkingSession) {
  session.paymentStatus = 'PENDING'
  setTimeout(() => {
    // A free stay (within the grace period) has nothing to charge, so it can't fail.
    const failed = session.cost > 0 && random() < PAYMENT_FAILURE_RATE
    session.paymentStatus = failed ? 'FAILED' : 'COMPLETED'
    if (failed) notify('PAYMENT_FAILED', `Payment of ${session.cost.toFixed(2)} lei for ${session.spotCode} failed. Try payment again from your receipt.`)
    else notify('PAYMENT_COMPLETED', `Paid ${session.cost.toFixed(2)} lei for parking at ${session.spotCode}, ${session.locationName}.`)
  }, 2500)
}

function activeReservation(): Reservation | undefined {
  return reservations.find((r) => r.status === 'ACTIVE')
}

/** Same rules as reservation-service's CurrentParkingService. */
function lastEnded(): Pick<CurrentParking, 'lastCompleted' | 'lastEndedReservation'> {
  const lastCompleted = sessions.filter((s) => s.status === 'COMPLETED').at(-1)
  const lastEndedReservation = reservations
    .filter((r) => (r.status === 'CANCELLED' || r.status === 'EXPIRED') && r.endedAt)
    .sort((a, b) => a.endedAt!.localeCompare(b.endedAt!))
    .at(-1)
  if (lastCompleted && lastCompleted.paymentStatus !== 'COMPLETED') return { lastCompleted }
  const recent = (iso: string) => Date.now() - new Date(iso).getTime() < 2 * 60 * 60_000
  const receiptIsNewer = lastCompleted && (!lastEndedReservation || lastCompleted.endedAt! > lastEndedReservation.endedAt!)
  if (receiptIsNewer && recent(lastCompleted.endedAt!)) return { lastCompleted }
  if (!receiptIsNewer && lastEndedReservation && recent(lastEndedReservation.endedAt!)) return { lastEndedReservation }
  return {}
}

/** Only an active reservation whose hold time hasn't run out can be moved or extended. */
function changeableReservation(id: string): Reservation {
  const reservation = reservations.find((r) => r.id === id)
  if (!reservation || reservation.status !== 'ACTIVE') throw new ApiError(409, 'This reservation has already ended.')
  if (new Date(reservation.expiresAt).getTime() <= Date.now()) throw new ApiError(409, 'This reservation’s hold time is over.')
  return reservation
}

function activeSession(): ParkingSession | undefined {
  return sessions.find((s) => s.status === 'ACTIVE')
}

// Expiration sweeper, like the backend's TTL queue / @Scheduled fallback.
setInterval(() => {
  const now = Date.now()
  for (const reservation of reservations) {
    if (reservation.status === 'ACTIVE' && new Date(reservation.expiresAt).getTime() <= now) {
      reservation.status = 'EXPIRED'
      reservation.endedAt = reservation.expiresAt
      setStatus(findSpot(reservation.spotId), 'AVAILABLE')
      notify('RESERVATION_EXPIRED', `Your reservation for ${reservation.spotCode} expired, so the spot is free again.`)
    }
  }
}, 1000)

// Other drivers arriving and leaving.
setInterval(() => {
  const candidates = spots.filter((s) => s.heldBy !== DRIVER_ID && (s.status === 'AVAILABLE' || s.status === 'OCCUPIED'))
  const spot = candidates[Math.floor(random() * candidates.length)]
  if (!spot) return
  if (spot.status === 'AVAILABLE') setStatus(spot, random() < 0.3 ? 'RESERVED' : 'OCCUPIED', 'someone-else')
  else setStatus(spot, 'AVAILABLE')
}, 4000)

const delay = <T>(produce: () => T): Promise<T> =>
  new Promise((resolve, reject) =>
    setTimeout(() => {
      try {
        resolve(structuredClone(produce()))
      } catch (error) {
        reject(error)
      }
    }, LATENCY_MS),
  )

export function onMockSpotChange(listener: (event: SpotStatusChanged) => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export const mockApi: Api = {
  listLocations: () => delay(() => locations.map(counted)),
  getLocation: (id) => delay(() => counted(findLocation(id))),
  listSpots: (locationId) => delay(() => spots.filter((s) => s.locationId === locationId)),

  createLocation: (input) =>
    delay(() => {
      const location: ParkingLocation = {
        ...input,
        id: crypto.randomUUID(),
        totalSpots: 0,
        freeSpots: 0,
        pricing: normalizeRule({ strategy: 'HOURLY', hourlyRate: 5 }),
      }
      locations.push(location)
      return location
    }),

  updateLocation: (id, input) =>
    delay(() => {
      const location = findLocation(id)
      Object.assign(location, input)
      return counted(location)
    }),

  deleteLocation: (id) =>
    delay(() => {
      const location = findLocation(id)
      const inUse = spots
        .filter((s) => s.locationId === id && (s.status === 'RESERVED' || s.status === 'OCCUPIED'))
        .map((s) => s.code)
        .sort((a, c) => a.localeCompare(c, undefined, { numeric: true }))
      if (inUse.length > 0) {
        throw new ApiError(
          409,
          `${location.name} can't be deleted while drivers use ${inUse.join(', ')}. Wait until those spots are free; reserved ones you can close first.`,
        )
      }
      locations.splice(locations.indexOf(location), 1)
      for (let i = spots.length - 1; i >= 0; i--) if (spots[i].locationId === id) spots.splice(i, 1)
    }),

  deleteSpot: (locationId, spotId) =>
    delay(() => {
      const spot = findSpot(spotId)
      if (spot.locationId !== locationId) throw new ApiError(404, 'This spot no longer exists.')
      if (spot.status === 'RESERVED' || spot.status === 'OCCUPIED') {
        throw new ApiError(
          409,
          `Spot ${spot.code} is ${spot.status.toLowerCase()}. Wait until it is free, or close it first if it is only reserved.`,
        )
      }
      spots.splice(spots.indexOf(spot), 1)
    }),

  addSpots: (locationId, input) =>
    delay(() => {
      findLocation(locationId)
      // Continue after the highest number in the zone, like parking-service (after deleting A2 of A1–A3, next is A4).
      const existing = Math.max(
        0,
        ...spots
          .filter((s) => s.locationId === locationId && s.level === input.level && s.zone === input.zone)
          .map((s) => Number(s.code.slice(s.zone.length))),
      )
      const added: Spot[] = Array.from({ length: input.count }, (_, i) => ({
        id: crypto.randomUUID(),
        locationId,
        code: `${input.zone}${existing + i + 1}`,
        level: input.level,
        zone: input.zone,
        type: input.type,
        evCharging: input.evCharging,
        accessible: input.accessible,
        status: 'AVAILABLE',
      }))
      spots.push(...added)
      return added
    }),

  setSpotStatus: (_locationId, spotId, status) =>
    delay(() => {
      const spot = findSpot(spotId)
      if (status === 'OUT_OF_SERVICE' && spot.status === 'OCCUPIED') {
        throw new ApiError(409, `Spot ${spot.code} is occupied, so it can't be taken out of service.`)
      }
      // Closing a reserved spot cancels its reservation (spot.closed → reservation-service).
      const reservation = spot.status === 'RESERVED' ? reservations.find((r) => r.spotId === spot.id && r.status === 'ACTIVE') : undefined
      setStatus(spot, status)
      if (reservation) {
        reservation.status = 'CANCELLED'
        reservation.cancelReason = 'SPOT_CLOSED'
        reservation.endedAt = new Date().toISOString()
        const location = findLocation(spot.locationId)
        notify(
          'RESERVATION_CANCELLED',
          `${spot.code} at ${location.name} was closed by the car park, so your reservation was cancelled. Pick another free spot.`,
        )
      }
      return spot
    }),

  updateSpots: (locationId, spotIds, changes) =>
    delay(() => {
      if (spotIds.length === 0 || Object.values(changes).every((v) => v === undefined)) {
        throw new ApiError(400, 'Pick at least one spot and one change.')
      }
      const edited = spotIds.map(findSpot)
      if (edited.some((s) => s.locationId !== locationId)) throw new ApiError(404, 'Some of these spots don’t exist at this location.')
      for (const spot of edited) {
        Object.assign(spot, Object.fromEntries(Object.entries(changes).filter(([, v]) => v !== undefined)))
        broadcast(spot)
      }
      return edited
    }),

  updatePricing: (locationId, rule) =>
    delay(() => {
      const location = findLocation(locationId)
      location.pricing = normalizeRule(rule)
      return counted(location)
    }),

  getCurrentParking: () =>
    delay((): CurrentParking => {
      const session = activeSession()
      if (session) {
        const location = findLocation(session.locationId)
        session.cost = cost(location.pricing, new Date(session.startedAt), new Date(), findSpot(session.spotId))
      }
      return { reservation: activeReservation(), session, ...lastEnded() }
    }),

  reserveSpot: (spotId) =>
    delay(() => {
      const spot = findSpot(spotId)
      if (activeReservation() || activeSession()) {
        throw new ApiError(409, 'You already have a reservation or a session running.')
      }
      if (spot.status !== 'AVAILABLE') throw new ApiError(409, `${spot.code} is no longer free.`)
      const location = findLocation(spot.locationId)
      const now = new Date()
      const reservation: Reservation = {
        id: crypto.randomUUID(),
        spotId,
        spotCode: spot.code,
        locationId: location.id,
        locationName: location.name,
        status: 'ACTIVE',
        createdAt: now.toISOString(),
        expiresAt: new Date(now.getTime() + RESERVATION_MINUTES * 60_000).toISOString(),
        extended: false,
      }
      reservations.push(reservation)
      setStatus(spot, 'RESERVED', DRIVER_ID)
      notify('RESERVATION_CREATED', `${spot.code} at ${location.name} is held for you until ${timeOfDay(reservation.expiresAt)}.`)
      return reservation
    }),

  // Same rules and wording as reservation-service.
  moveReservation: (id, spotId) =>
    delay(() => {
      const reservation = changeableReservation(id)
      if (reservation.spotId === spotId) throw new ApiError(409, `Your reservation is already for ${reservation.spotCode}.`)
      const spot = findSpot(spotId)
      if (spot.status !== 'AVAILABLE') throw new ApiError(409, `${spot.code} is no longer free.`)
      const previous = findSpot(reservation.spotId)
      const location = findLocation(spot.locationId)
      setStatus(spot, 'RESERVED', DRIVER_ID)
      Object.assign(reservation, { spotId, spotCode: spot.code, locationId: location.id, locationName: location.name })
      setStatus(previous, 'AVAILABLE')
      notify(
        'RESERVATION_CHANGED',
        `Your reservation moved from ${previous.code} to ${spot.code}. It is held until ${timeOfDay(reservation.expiresAt)}.`,
      )
      return reservation
    }),

  extendReservation: (id) =>
    delay(() => {
      const reservation = changeableReservation(id)
      if (reservation.extended) throw new ApiError(409, 'You have already extended this reservation once.')
      reservation.expiresAt = new Date(new Date(reservation.expiresAt).getTime() + RESERVATION_MINUTES * 60_000).toISOString()
      reservation.extended = true
      notify('RESERVATION_CHANGED', `${reservation.spotCode} is now held for you until ${timeOfDay(reservation.expiresAt)}.`)
      return reservation
    }),

  cancelReservation: (id) =>
    delay(() => {
      const reservation = reservations.find((r) => r.id === id)
      if (!reservation || reservation.status !== 'ACTIVE') throw new ApiError(409, 'This reservation has already ended.')
      reservation.status = 'CANCELLED'
      reservation.cancelReason = 'DRIVER'
      reservation.endedAt = new Date().toISOString()
      setStatus(findSpot(reservation.spotId), 'AVAILABLE')
      notify('RESERVATION_CANCELLED', `You cancelled your reservation for ${reservation.spotCode}.`)
    }),

  startSession: (spotId, reservationId) =>
    delay(() => {
      const spot = findSpot(spotId)
      if (activeSession()) throw new ApiError(409, 'You already have a session running.')
      const ownReservation = reservations.find((r) => r.id === reservationId && r.status === 'ACTIVE' && r.spotId === spotId)
      if (spot.status !== 'AVAILABLE' && !ownReservation) throw new ApiError(409, `${spot.code} is no longer free.`)
      if (ownReservation) Object.assign(ownReservation, { status: 'COMPLETED', endedAt: new Date().toISOString() })
      const location = findLocation(spot.locationId)
      const session: ParkingSession = {
        id: crypto.randomUUID(),
        spotId,
        spotCode: spot.code,
        locationId: location.id,
        locationName: location.name,
        status: 'ACTIVE',
        startedAt: new Date().toISOString(),
        cost: 0,
      }
      sessions.push(session)
      setStatus(spot, 'OCCUPIED', DRIVER_ID)
      return session
    }),

  endSession: (id) =>
    delay(() => {
      const session = sessions.find((s) => s.id === id)
      if (!session || session.status !== 'ACTIVE') throw new ApiError(409, 'This session has already ended.')
      const location = findLocation(session.locationId)
      const endedAt = new Date()
      session.status = 'COMPLETED'
      session.endedAt = endedAt.toISOString()
      session.cost = cost(location.pricing, new Date(session.startedAt), endedAt, findSpot(session.spotId))
      session.paymentId = crypto.randomUUID()
      setStatus(findSpot(session.spotId), 'AVAILABLE')
      processPayment(session)
      return session
    }),

  retryPayment: (paymentId) =>
    delay(() => {
      const session = sessions.find((s) => s.paymentId === paymentId)
      if (!session) throw new ApiError(404, 'This payment no longer exists.')
      processPayment(session)
    }),

  listNotifications: () => delay(() => notifications),
}

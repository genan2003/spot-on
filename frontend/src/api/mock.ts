/*
 * In-memory stand-in for the gateway, used while VITE_USE_MOCKS=true.
 * It follows the same rules as the backend (one active reservation per spot, 15-minute expiry,
 * cost from the location's pricing rule, simulated payment with a failure rate) and simulates
 * other drivers so the live spot plan has something to show.
 */
import { timeOfDay } from '../lib/format'
import { isPlausiblePlate, normalizePlate } from '../lib/plate'
import { cost, normalizeRule } from '../lib/pricing'
import type { Api } from './client'
import { DRIVER_ID } from './driver'
import { ApiError } from './errors'
import type {
  CurrentParking,
  Notification,
  NotificationPreference,
  ParkingLocation,
  ParkingSession,
  Payment,
  PaymentAccount,
  PaymentMethod,
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

// Plates of the other (made-up) drivers' cars, so occupied bays show one in the admin dashboard. Counted, not
// drawn from random(), so adding them didn't change the seeded car park.
let plates = 0
function demoPlate(): string {
  const n = plates++
  const counties = ['CJ', 'B', 'BH', 'MS', 'AB', 'SB', 'BN']
  const letter = (k: number) => String.fromCharCode(65 + ((n * 7 + k * 11) % 26))
  return `${counties[n % counties.length]} ${String(10 + ((n * 37) % 90))} ${letter(1)}${letter(2)}${letter(3)}`
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
          licencePlate: status === 'OCCUPIED' ? demoPlate() : undefined,
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
const payments: Payment[] = []
// Like payment-service: a new driver pays by card and has 25 lei in the simulated wallet.
const account: PaymentAccount = { driverId: DRIVER_ID, preferredMethod: 'CARD', walletBalance: 25 }
const money = (amount: number) => Math.round(amount * 100) / 100
const notifications: Notification[] = []
// Like notification-service: push until the driver chooses another channel.
const notificationPreference: NotificationPreference = { driverId: DRIVER_ID, channel: 'PUSH' }

/** A few past visits of the demo driver, so the history page has something to show. */
function seedHistory() {
  const at = (daysAgo: number, hours: number, minutes: number) => {
    const date = new Date()
    date.setDate(date.getDate() - daysAgo)
    date.setHours(hours, minutes, 0, 0)
    return date.toISOString()
  }
  const spotIn = (locationId: string, code: string) => spots.find((s) => s.locationId === locationId && s.code === code)!
  const stay = (locationId: string, code: string, startedAt: string, endedAt: string, attempts: number) => {
    const spot = spotIn(locationId, code)
    const location = findLocation(locationId)
    const session: ParkingSession = {
      id: crypto.randomUUID(),
      spotId: spot.id,
      spotCode: code,
      locationId,
      locationName: location.name,
      status: 'COMPLETED',
      startedAt,
      endedAt,
      cost: cost(location.pricing, new Date(startedAt), new Date(endedAt), spot),
      paymentId: crypto.randomUUID(),
      paymentStatus: 'COMPLETED',
    }
    sessions.push(session)
    payments.unshift({
      id: session.paymentId!,
      sessionId: session.id,
      driverId: DRIVER_ID,
      amount: session.cost,
      method: 'CARD',
      status: 'COMPLETED',
      attempts,
      createdAt: endedAt,
      processedAt: endedAt,
    })
  }
  stay('marasti-park-ride', 'C4', at(3, 8, 15), at(3, 10, 5), 1)
  stay('riverside-lot', 'B2', at(1, 9, 10), at(1, 11, 40), 2)
  const held = spotIn('central-garage', 'A1')
  reservations.push({
    id: crypto.randomUUID(),
    spotId: held.id,
    spotCode: held.code,
    locationId: held.locationId,
    locationName: findLocation(held.locationId).name,
    status: 'EXPIRED',
    createdAt: at(1, 17, 50),
    expiresAt: at(1, 18, 5),
    extended: false,
    endedAt: at(1, 18, 5),
  })
}
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

function setStatus(spot: Spot, status: SpotStatus, heldBy?: string, licencePlate?: string) {
  spot.status = status
  spot.heldBy = heldBy
  spot.licencePlate = licencePlate
  broadcast(spot)
}

/** Like spot.status-changed: the spot's live state goes to every subscriber. */
function broadcast(spot: Spot) {
  const { id: spotId, locationId, status, heldBy, licencePlate, type, evCharging, accessible } = spot
  listeners.forEach((listener) => listener({ spotId, locationId, status, heldBy, licencePlate, type, evCharging, accessible }))
}

const notificationListeners = new Set<(notification: Notification) => void>()

/** Like notification-service: saves it, then pushes it to the open app (Server-Sent Events). */
function notify(kind: Notification['kind'], message: string) {
  const notification: Notification = {
    id: crypto.randomUUID(),
    kind,
    channelType: notificationPreference.channel,
    message,
    sentAt: new Date().toISOString(),
  }
  notifications.unshift(notification)
  notificationListeners.forEach((listener) => listener(structuredClone(notification)))
}

export function onMockNotification(listener: (notification: Notification) => void): () => void {
  notificationListeners.add(listener)
  return () => notificationListeners.delete(listener)
}

/**
 * Like payment-service: one record per session, a retry adds an attempt to it (and may switch method).
 * The card fails at random; the wallet only when its balance is too low, and is debited otherwise.
 */
function processPayment(session: ParkingSession, switchTo?: PaymentMethod) {
  session.paymentStatus = 'PENDING'
  let payment = payments.find((p) => p.sessionId === session.id)
  if (payment && switchTo) payment.method = switchTo
  if (!payment) {
    payment = {
      id: session.paymentId!,
      sessionId: session.id,
      driverId: DRIVER_ID,
      amount: session.cost,
      method: account.preferredMethod,
      status: 'PENDING',
      attempts: 0,
      createdAt: new Date().toISOString(),
    }
    payments.unshift(payment)
  }
  const record = payment
  setTimeout(() => {
    // A free stay (within the grace period) has nothing to charge, so it can't fail.
    const wallet = record.method === 'WALLET'
    const failed = session.cost > 0 && (wallet ? account.walletBalance < session.cost : random() < PAYMENT_FAILURE_RATE)
    if (wallet && !failed) account.walletBalance = money(account.walletBalance - session.cost)
    session.paymentStatus = failed ? 'FAILED' : 'COMPLETED'
    Object.assign(record, { status: session.paymentStatus, attempts: record.attempts + 1, processedAt: new Date().toISOString() })
    const amount = `${session.cost.toFixed(2)} lei`
    if (failed && wallet) notify('PAYMENT_FAILED', `Your wallet doesn't have enough money for ${amount}. Add money or pay by card from your receipt.`)
    else if (failed) notify('PAYMENT_FAILED', `Your card was declined for ${amount}. Try payment again from your receipt, or pay from your wallet.`)
    else notify('PAYMENT_COMPLETED', `Paid ${amount} ${wallet ? 'from your wallet' : 'by card'} for your parking session.`)
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

// Reminder lead, like reservation-service: 5 minutes, at most half the hold time.
const REMINDER_MS = Math.min(5, RESERVATION_MINUTES / 2) * 60_000
// "reservationId@expiresAt" already reminded, so an extended reservation is reminded again.
const reminded = new Set<string>()

// Expiration sweeper, like the backend's TTL queues / @Scheduled fallback.
setInterval(() => {
  const now = Date.now()
  for (const reservation of reservations) {
    const left = new Date(reservation.expiresAt).getTime() - now
    const key = `${reservation.id}@${reservation.expiresAt}`
    if (reservation.status === 'ACTIVE' && left > 0 && left <= REMINDER_MS && !reminded.has(key)) {
      reminded.add(key)
      const location = findLocation(reservation.locationId)
      notify(
        'RESERVATION_EXPIRING',
        `${reservation.spotCode} at ${location.name} is held for you only until ${timeOfDay(reservation.expiresAt)}. Start your session or extend the hold before then.`,
      )
    }
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
  if (spot.status === 'AVAILABLE') {
    if (random() < 0.3) setStatus(spot, 'RESERVED', 'someone-else')
    else setStatus(spot, 'OCCUPIED', 'someone-else', demoPlate())
  }
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

seedHistory()

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

  startSession: (spotId, licencePlate, reservationId) =>
    delay(() => {
      const spot = findSpot(spotId)
      // Like reservation-service: a plausible plate is required, stored upper case with single spaces.
      if (!isPlausiblePlate(licencePlate)) throw new ApiError(400, 'Enter the plate as it is on the car, e.g. CJ 07 ABC.')
      const plate = normalizePlate(licencePlate)
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
        licencePlate: plate,
        status: 'ACTIVE',
        startedAt: new Date().toISOString(),
        cost: 0,
      }
      sessions.push(session)
      setStatus(spot, 'OCCUPIED', DRIVER_ID, plate)
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

  retryPayment: (paymentId, method) =>
    delay(() => {
      const session = sessions.find((s) => s.paymentId === paymentId)
      if (!session) throw new ApiError(404, 'This payment no longer exists.')
      if (session.paymentStatus !== 'FAILED') {
        throw new ApiError(409, `Only a failed payment can be retried; this one is ${(session.paymentStatus ?? 'pending').toLowerCase()}.`)
      }
      processPayment(session, method)
    }),

  getPayment: (paymentId) =>
    delay(() => {
      const payment = payments.find((p) => p.id === paymentId)
      if (!payment) throw new ApiError(404, `Payment ${paymentId} not found.`)
      return { ...payment }
    }),

  getPaymentAccount: () => delay(() => ({ ...account })),

  choosePaymentMethod: (method) =>
    delay(() => {
      account.preferredMethod = method
      return { ...account }
    }),

  topUpWallet: (amount) =>
    delay(() => {
      if (amount < 1 || amount > 500) throw new ApiError(400, 'Add between 1 and 500 lei at a time.')
      account.walletBalance = money(account.walletBalance + amount)
      return { ...account }
    }),

  listNotifications: () => delay(() => notifications),
  getNotificationPreference: () => delay(() => ({ ...notificationPreference })),
  chooseNotificationChannel: (channel) =>
    delay(() => {
      notificationPreference.channel = channel
      return { ...notificationPreference }
    }),
  listReservations: () => delay(() => [...reservations].sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 50)),
  listSessions: () => delay(() => [...sessions].sort((a, b) => b.startedAt.localeCompare(a.startedAt)).slice(0, 50)),
  listPayments: () => delay(() => payments),
}

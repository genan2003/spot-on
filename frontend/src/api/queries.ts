import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { api } from './client'
import { subscribeToNotifications, subscribeToSpotStatus } from './live'
import type { CurrentParking, History, NewLocation, NotificationChannel, PaymentMethod, PaymentStatus, NewSpots, Notification, PricingRule, Spot, SpotPropertyChanges, SpotStatus } from './types'

export const keys = {
  locations: ['locations'] as const,
  location: (id: string) => ['locations', id] as const,
  spots: (locationId: string) => ['spots', locationId] as const,
  current: ['current-parking'] as const,
  notifications: ['notifications'] as const,
  history: ['history'] as const,
  paymentAccount: ['payment-account'] as const,
  notificationPreference: ['notification-preference'] as const,
  payment: (id: string) => ['payments', id] as const,
}

/** The driver's chosen method and wallet balance. */
export const usePaymentAccount = () => useQuery({ queryKey: keys.paymentAccount, queryFn: api.getPaymentAccount })

/** One payment (its method and tries), loaded again whenever the receipt's payment status changes. */
export const usePayment = (id: string | undefined, status: PaymentStatus | undefined) =>
  useQuery({
    queryKey: [...keys.payment(id ?? ''), status] as const,
    queryFn: () => api.getPayment(id!),
    enabled: !!id && !!status && status !== 'PENDING',
  })

function useAccountMutation<T>(fn: (input: T) => Promise<unknown>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSettled: () => queryClient.invalidateQueries({ queryKey: keys.paymentAccount }),
  })
}

export const useChoosePaymentMethod = () => useAccountMutation((method: PaymentMethod) => api.choosePaymentMethod(method))
export const useTopUpWallet = () => useAccountMutation((amount: number) => api.topUpWallet(amount))

export const useLocations = () => useQuery({ queryKey: keys.locations, queryFn: api.listLocations })

export const useLocation = (id: string) => useQuery({ queryKey: keys.location(id), queryFn: () => api.getLocation(id) })

/** Revenue and occupancy per day; reloaded every minute, as today's figures keep growing. */
export const useDailyStats = (locationId: string) =>
  useQuery({ queryKey: ['daily-stats', locationId], queryFn: () => api.getDailyStats(locationId), refetchInterval: 60_000 })

export const useSpots = (locationId: string) =>
  useQuery({ queryKey: keys.spots(locationId), queryFn: () => api.listSpots(locationId) })

/** Polls while something is running: the session's cost estimate or a pending payment changes server-side. */
export const useCurrentParking = () =>
  useQuery({
    queryKey: keys.current,
    queryFn: api.getCurrentParking,
    refetchInterval: (query) => {
      const data: CurrentParking | undefined = query.state.data
      const busy = data?.reservation || data?.session || data?.lastCompleted?.paymentStatus === 'PENDING'
      return busy ? 3000 : false
    },
  })

/**
 * Past parking: sessions and reservations from reservation-service, payments from payment-service. Payment details
 * only add the method and number of tries, so if payment-service can't answer the page still shows everything else.
 * Polls while a payment is being processed (after "Try payment again").
 */
export const useHistory = () =>
  useQuery({
    queryKey: keys.history,
    queryFn: async (): Promise<History> => {
      const [reservations, sessions, payments] = await Promise.all([
        api.listReservations(),
        api.listSessions(),
        api.listPayments().catch(() => []),
      ])
      return { reservations, sessions, payments }
    },
    refetchInterval: (query) => (query.state.data?.sessions.some((s) => s.paymentStatus === 'PENDING') ? 3000 : false),
  })

/** The channel the driver's notifications go out on. */
export const useNotificationPreference = () =>
  useQuery({ queryKey: keys.notificationPreference, queryFn: api.getNotificationPreference })

export function useChooseNotificationChannel() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (channel: NotificationChannel) => api.chooseNotificationChannel(channel),
    onSettled: () => queryClient.invalidateQueries({ queryKey: keys.notificationPreference }),
  })
}

/** Loaded once; new ones arrive live (useLiveNotifications), so no polling. */
export const useNotifications = () => useQuery({ queryKey: keys.notifications, queryFn: api.listNotifications })

/**
 * Adds each live notification to the cached list (newest first) as it arrives, and returns the latest one,
 * so the layout can announce it to screen readers.
 */
export function useLiveNotifications(): Notification | undefined {
  const queryClient = useQueryClient()
  const [latest, setLatest] = useState<Notification>()
  useEffect(
    () =>
      subscribeToNotifications(
        (notification) => {
          queryClient.setQueryData<Notification[]>(keys.notifications, (list) =>
            list?.some((n) => n.id === notification.id) ? list : [notification, ...(list ?? [])],
          )
          setLatest(notification)
        },
        () => queryClient.invalidateQueries({ queryKey: keys.notifications }),
      ),
    [queryClient],
  )
  return latest
}

/** Keeps every cached spot list in step with spot.status-changed events. */
export function useLiveSpotStatus() {
  const queryClient = useQueryClient()
  useEffect(
    () =>
      subscribeToSpotStatus((event) => {
        queryClient.setQueryData<Spot[]>(keys.spots(event.locationId), (spots) =>
          spots?.map((s) =>
            s.id === event.spotId
              ? {
                  ...s,
                  status: event.status,
                  heldBy: event.heldBy,
                  // Cleared when the car leaves, so not kept from the old value.
                  licencePlate: event.licencePlate,
                  type: event.type ?? s.type,
                  evCharging: event.evCharging ?? s.evCharging,
                  accessible: event.accessible ?? s.accessible,
                }
              : s,
          ),
        )
        // A refetch started before this change was committed (e.g. right after "Cancel reservation",
        // while the release event was still on its way) would land afterwards and put the old status
        // back. Invalidating cancels that request and loads the list again, now including this change.
        queryClient.invalidateQueries({ queryKey: keys.spots(event.locationId) })
        queryClient.invalidateQueries({ queryKey: keys.locations })
      }),
    [queryClient],
  )
}

/** Current time, re-rendered every second, for countdowns and running timers. */
export function useNow(): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])
  return now
}

function useParkingMutation<T, R>(fn: (input: T) => Promise<R>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: keys.current })
      queryClient.invalidateQueries({ queryKey: ['spots'] })
      queryClient.invalidateQueries({ queryKey: keys.locations })
      queryClient.invalidateQueries({ queryKey: keys.notifications })
      queryClient.invalidateQueries({ queryKey: keys.history })
      queryClient.invalidateQueries({ queryKey: keys.paymentAccount })
      queryClient.invalidateQueries({ queryKey: ['payments'] })
    },
  })
}

export const useReserveSpot = () => useParkingMutation((spotId: string) => api.reserveSpot(spotId))
export const useMoveReservation = () =>
  useParkingMutation(({ id, spotId }: { id: string; spotId: string }) => api.moveReservation(id, spotId))
export const useExtendReservation = () => useParkingMutation((id: string) => api.extendReservation(id))
export const useCancelReservation = () => useParkingMutation((id: string) => api.cancelReservation(id))
export const useStartSession = () =>
  useParkingMutation(({ spotId, reservationId, licencePlate }: { spotId: string; reservationId?: string; licencePlate: string }) =>
    api.startSession(spotId, licencePlate, reservationId),
  )
export const useEndSession = () => useParkingMutation((id: string) => api.endSession(id))
export const useRetryPayment = () =>
  useParkingMutation(({ paymentId, method }: { paymentId: string; method?: PaymentMethod }) => api.retryPayment(paymentId, method))

export const useCreateLocation = () => useParkingMutation((location: NewLocation) => api.createLocation(location))
export const useUpdateLocation = (id: string) => useParkingMutation((location: NewLocation) => api.updateLocation(id, location))
export const useDeleteLocation = () => useParkingMutation((id: string) => api.deleteLocation(id))
export const useDeleteSpot = (locationId: string) => useParkingMutation((spotId: string) => api.deleteSpot(locationId, spotId))
export const useAddSpots = (locationId: string) => useParkingMutation((spots: NewSpots) => api.addSpots(locationId, spots))
export const useSetSpotStatus = (locationId: string) =>
  useParkingMutation(({ spotId, status }: { spotId: string; status: SpotStatus }) => api.setSpotStatus(locationId, spotId, status))
export const useUpdateSpots = (locationId: string) =>
  useParkingMutation(({ spotIds, changes }: { spotIds: string[]; changes: SpotPropertyChanges }) =>
    api.updateSpots(locationId, spotIds, changes),
  )
export const useUpdatePricing = (locationId: string) => useParkingMutation((rule: PricingRule) => api.updatePricing(locationId, rule))

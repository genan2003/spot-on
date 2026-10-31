import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { api } from './client'
import { subscribeToSpotStatus } from './live'
import type { CurrentParking, NewLocation, NewSpots, PricingRule, Spot, SpotPropertyChanges, SpotStatus } from './types'

export const keys = {
  locations: ['locations'] as const,
  location: (id: string) => ['locations', id] as const,
  spots: (locationId: string) => ['spots', locationId] as const,
  current: ['current-parking'] as const,
  notifications: ['notifications'] as const,
}

export const useLocations = () => useQuery({ queryKey: keys.locations, queryFn: api.listLocations })

export const useLocation = (id: string) => useQuery({ queryKey: keys.location(id), queryFn: () => api.getLocation(id) })

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

export const useNotifications = () =>
  useQuery({ queryKey: keys.notifications, queryFn: api.listNotifications, refetchInterval: 5000 })

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
    },
  })
}

export const useReserveSpot = () => useParkingMutation((spotId: string) => api.reserveSpot(spotId))
export const useMoveReservation = () =>
  useParkingMutation(({ id, spotId }: { id: string; spotId: string }) => api.moveReservation(id, spotId))
export const useExtendReservation = () => useParkingMutation((id: string) => api.extendReservation(id))
export const useCancelReservation = () => useParkingMutation((id: string) => api.cancelReservation(id))
export const useStartSession = () =>
  useParkingMutation(({ spotId, reservationId }: { spotId: string; reservationId?: string }) => api.startSession(spotId, reservationId))
export const useEndSession = () => useParkingMutation((id: string) => api.endSession(id))
export const useRetryPayment = () => useParkingMutation((paymentId: string) => api.retryPayment(paymentId))

export const useCreateLocation = () => useParkingMutation((location: NewLocation) => api.createLocation(location))
export const useAddSpots = (locationId: string) => useParkingMutation((spots: NewSpots) => api.addSpots(locationId, spots))
export const useSetSpotStatus = (locationId: string) =>
  useParkingMutation(({ spotId, status }: { spotId: string; status: SpotStatus }) => api.setSpotStatus(locationId, spotId, status))
export const useUpdateSpots = (locationId: string) =>
  useParkingMutation(({ spotIds, changes }: { spotIds: string[]; changes: SpotPropertyChanges }) =>
    api.updateSpots(locationId, spotIds, changes),
  )
export const useUpdatePricing = (locationId: string) => useParkingMutation((rule: PricingRule) => api.updatePricing(locationId, rule))

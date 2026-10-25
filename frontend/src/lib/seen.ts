import { useSyncExternalStore } from 'react'

// Remembers how many notifications the driver has seen, per browser. Storage can be unavailable
// (private mode, blocked site data), so every access is guarded and the app works without it.
const KEY = 'spot-on.seen-notifications'
const listeners = new Set<() => void>()
let memory = 0

function read(): number {
  try {
    return Number(localStorage.getItem(KEY) ?? memory)
  } catch {
    return memory
  }
}

export function markSeen(count: number) {
  memory = count
  try {
    localStorage.setItem(KEY, String(count))
  } catch {
    // Kept in memory only; the badge shows again next visit.
  }
  listeners.forEach((listener) => listener())
}

export function useSeenCount(): number {
  return useSyncExternalStore((listener) => {
    listeners.add(listener)
    return () => listeners.delete(listener)
  }, read)
}

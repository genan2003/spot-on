import { useSyncExternalStore } from 'react'

// Ended reservations and paid receipts the driver closed with "Done", per browser. Storage can be
// unavailable (private mode, blocked site data), so every access is guarded; then it lasts until reload.
const KEY = 'spot-on.dismissed'
const listeners = new Set<() => void>()
let memory: string[] = []

function read(): string {
  try {
    return localStorage.getItem(KEY) ?? memory.join(',')
  } catch {
    return memory.join(',')
  }
}

export function dismiss(id: string) {
  // Only the last few are kept: older ids can't come back, the server stops returning them after two hours.
  memory = [id, ...read().split(',').filter(Boolean)].slice(0, 10)
  try {
    localStorage.setItem(KEY, memory.join(','))
  } catch {
    // Kept in memory only.
  }
  listeners.forEach((listener) => listener())
}

export function useDismissed(id: string | undefined): boolean {
  const ids = useSyncExternalStore((listener) => {
    listeners.add(listener)
    return () => listeners.delete(listener)
  }, read)
  return id !== undefined && ids.split(',').includes(id)
}

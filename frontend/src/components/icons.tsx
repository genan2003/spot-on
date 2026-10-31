import type { SVGProps } from 'react'

type IconProps = SVGProps<SVGSVGElement>

const base = { 'aria-hidden': true, focusable: false } as const

export function CheckIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={3} strokeLinecap="round" strokeLinejoin="round" {...base} {...props}>
      <path d="M5 12.5l4.5 4.5L19 7.5" />
    </svg>
  )
}

/** Top-down car, nose at the top. */
export function CarIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 44" {...base} {...props}>
      <rect x="2" y="1" width="20" height="42" rx="7" fill="currentColor" />
      <path d="M5 13c2-2.5 12-2.5 14 0l-1.5 6h-11z" fill="var(--color-asphalt)" opacity="0.85" />
      <path d="M6.5 33h11l1 4c-2.5 1.6-10.5 1.6-13 0z" fill="var(--color-asphalt)" opacity="0.85" />
      <rect x="5.5" y="21" width="13" height="10" rx="1.5" fill="currentColor" stroke="var(--color-asphalt)" strokeOpacity="0.35" />
    </svg>
  )
}

/** Top-down motorcycle, front wheel at the top. Same height as the car so both fill a bay the same way. */
export function MotorcycleTopIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 14 44" {...base} {...props}>
      <rect x="5" y="1" width="4" height="11" rx="2" fill="currentColor" />
      <rect x="0.5" y="11" width="13" height="2.2" rx="1.1" fill="currentColor" />
      <rect x="3" y="14" width="8" height="17" rx="4" fill="currentColor" />
      <rect x="5" y="17" width="4" height="10" rx="2" fill="var(--color-asphalt)" opacity="0.5" />
      <rect x="5" y="31" width="4" height="12" rx="2" fill="currentColor" />
    </svg>
  )
}

/** Side-on motorcycle, the pictogram painted in motorcycle bays. */
export function MotorcycleIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round" {...base} {...props}>
      <circle cx="5" cy="16" r="3.5" />
      <circle cx="19" cy="16" r="3.5" />
      <path d="M5 16l4-5.5h5.5L19 16" />
      <path d="M14.5 10.5L13 7h3" />
    </svg>
  )
}

export function ConeIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" {...base} {...props}>
      <path d="M10 3h4l5 16H5z" fill="currentColor" />
      <path d="M8.2 10h7.6l.9 3H7.3z" fill="var(--color-asphalt)" opacity="0.7" />
      <rect x="3" y="19" width="18" height="2.5" rx="1" fill="currentColor" />
    </svg>
  )
}

/** Yellow road hatching, as painted on reserved or no-parking areas. */
export function HatchIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" {...base} {...props}>
      <rect x="3" y="3" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" />
      <path d="M3 13L13 3M3 21L21 3M11 21l10-10" stroke="currentColor" strokeWidth="2.5" />
    </svg>
  )
}

export function BoltIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" {...base} {...props}>
      <path d="M13.5 2L5 13.5h6L9.5 22 19 9.5h-6.2z" fill="currentColor" />
    </svg>
  )
}

/** Simplified International Symbol of Access. */
export function AccessibleIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round" {...base} {...props}>
      <circle cx="11" cy="3.8" r="1.9" fill="currentColor" stroke="none" />
      <path d="M11 7v7h6l2.5 5.5" />
      <path d="M11 10.5h5" />
      <path d="M8 10.8a6 6 0 1 0 8.4 7.6" />
    </svg>
  )
}

export function BellIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" {...base} {...props}>
      <path d="M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z" />
      <path d="M10 20.5a2 2 0 0 0 4 0" />
    </svg>
  )
}

export function BackIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round" {...base} {...props}>
      <path d="M15 5l-7 7 7 7" />
    </svg>
  )
}

/**
 * The parking sign: a blue plate with an inset white border, as on real "P" signs.
 * Used as the app mark and to point out the driver's own spot on the plan.
 */
export function ParkingSign({ size = 32, className }: { size?: number; className?: string }) {
  return (
    <svg viewBox="0 0 32 32" width={size} height={size} className={className} {...base}>
      <rect width="32" height="32" rx="6" fill="var(--color-sign)" />
      <rect x="2.5" y="2.5" width="27" height="27" rx="4" fill="none" stroke="var(--color-surface)" strokeWidth="1.5" />
      <path
        d="M11 24V8h6.2c3.6 0 5.8 2 5.8 5.1s-2.2 5.1-5.8 5.1H14.4V24z M14.4 15.3h2.6c1.7 0 2.6-.8 2.6-2.2s-.9-2.2-2.6-2.2h-2.6z"
        fill="var(--color-surface)"
      />
    </svg>
  )
}

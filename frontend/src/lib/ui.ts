// Button and field styles, defined once. Primary is the sign blue; there is one per screen.
export const button = {
  primary:
    'inline-flex min-h-12 items-center justify-center rounded-sign bg-sign px-6 text-base font-semibold text-surface hover:bg-sign-dark disabled:opacity-50 disabled:hover:bg-sign',
  secondary:
    'inline-flex min-h-12 items-center justify-center rounded-sign border-2 border-ink px-6 text-base font-semibold text-ink hover:bg-ink hover:text-surface disabled:opacity-50',
  quiet: 'inline-flex min-h-11 items-center px-1 text-base font-semibold text-sign underline-offset-4 hover:underline',
  small:
    'inline-flex min-h-9 items-center justify-center rounded-sign border border-edge bg-surface px-3 text-sm font-semibold text-ink hover:border-ink',
}

export const field = {
  label: 'block text-sm font-semibold',
  input: 'mt-1 block min-h-11 w-full rounded-sign border border-edge bg-surface px-3 text-base text-ink',
  hint: 'mt-1 text-sm text-ink-muted',
}

/** One choice out of a few, as joined buttons (levels, billing blocks). The active one is sign blue. */
export function Segmented<T extends string | number>({
  label,
  options,
  value,
  onChange,
}: {
  label: string
  options: { value: T; label: string }[]
  value: T
  onChange: (value: T) => void
}) {
  return (
    <div className="flex" role="radiogroup" aria-label={label}>
      {options.map((option) => {
        const active = option.value === value
        return (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => onChange(option.value)}
            className={`-ml-0.5 min-h-11 border-2 px-4 text-base font-semibold first:ml-0 first:rounded-l-sign last:rounded-r-sign ${
              active ? 'relative border-sign bg-sign text-surface' : 'border-edge bg-surface text-ink hover:text-sign'
            }`}
          >
            {option.label}
          </button>
        )
      })}
    </div>
  )
}

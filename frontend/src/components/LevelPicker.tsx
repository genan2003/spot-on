import { levelName } from '../lib/format'

export function LevelPicker({ levels, value, onChange }: { levels: number[]; value: number; onChange: (level: number) => void }) {
  return (
    <div className="flex" role="radiogroup" aria-label="Level">
      {levels.map((level) => {
        const active = level === value
        return (
          <button
            key={level}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => onChange(level)}
            className={`-ml-0.5 min-h-11 border-2 px-4 text-base font-semibold first:ml-0 first:rounded-l-sign last:rounded-r-sign ${
              active ? 'relative border-sign bg-sign text-surface' : 'border-edge bg-surface text-ink hover:text-sign'
            }`}
          >
            {levelName(level)}
          </button>
        )
      })}
    </div>
  )
}

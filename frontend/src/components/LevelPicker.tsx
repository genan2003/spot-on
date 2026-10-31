import { levelName } from '../lib/format'
import { Segmented } from './Segmented'

export function LevelPicker({ levels, value, onChange }: { levels: number[]; value: number; onChange: (level: number) => void }) {
  return <Segmented label="Level" options={levels.map((level) => ({ value: level, label: levelName(level) }))} value={value} onChange={onChange} />
}

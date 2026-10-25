/** Share of free spots, as a plain bar: the free part is green, the rest is taken. */
export function OccupancyBar({ free, total }: { free: number; total: number }) {
  const share = total === 0 ? 0 : free / total
  return (
    <div className="h-1.5 w-full max-w-40 bg-hairline" aria-hidden>
      <div className="h-full bg-free" style={{ width: `${share * 100}%` }} />
    </div>
  )
}

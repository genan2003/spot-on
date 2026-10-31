/** A licence plate as it looks on the car: dark letters on a white plate with a dark rim. */
export function Plate({ plate }: { plate: string }) {
  return (
    <span className="tabular inline-block rounded-bay border-2 border-ink bg-paint px-1.5 font-bold tracking-wide text-ink">
      {plate}
    </span>
  )
}

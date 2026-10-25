import { Link } from 'react-router'

export function NotFound() {
  return (
    <div className="mx-auto max-w-xl p-8">
      <h1 className="text-2xl font-extrabold tracking-tight">This page doesn’t exist</h1>
      <p className="mt-2">The link may be old or mistyped.</p>
      <Link to="/" className="mt-4 inline-flex font-semibold text-sign hover:underline">
        Find a spot
      </Link>
    </div>
  )
}

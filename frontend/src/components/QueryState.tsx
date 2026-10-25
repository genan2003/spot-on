import type { UseQueryResult } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { button } from '../lib/ui'

/** Loading and error states for every data view, so no screen is ever silently blank. */
export function QueryState<T>({
  query,
  loading,
  children,
}: {
  query: UseQueryResult<T>
  loading: string
  children: (data: T) => ReactNode
}) {
  if (query.isPending) {
    return (
      <p className="px-4 py-8 text-ink-muted" role="status">
        {loading}
      </p>
    )
  }
  if (query.isError) {
    return (
      <div className="px-4 py-8" role="alert">
        <p className="max-w-prose">{query.error.message}</p>
        <button type="button" className={`${button.small} mt-3`} onClick={() => query.refetch()}>
          Try again
        </button>
      </div>
    )
  }
  return <>{children(query.data)}</>
}

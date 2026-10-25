/** Error carrying the service's RFC 7807 ProblemDetail, so screens can show what happened. */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number, detail: string) {
    super(detail)
    this.status = status
  }
}

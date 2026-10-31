import type { PaymentMethod } from '../api/types'

/** A simulated top-up adds this much; one button, no amount to type. */
export const TOP_UP_AMOUNT = 50

/** How a payment went through, naming the method (the processor payment-service's Factory used). */
export function paidText(method: PaymentMethod): string {
  return method === 'WALLET' ? 'Paid from your wallet' : 'Paid by card'
}

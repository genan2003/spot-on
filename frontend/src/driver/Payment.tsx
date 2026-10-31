import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { keys, useChoosePaymentMethod, usePayment, usePaymentAccount, useRetryPayment, useTopUpWallet } from '../api/queries'
import type { ParkingSession, PaymentMethod } from '../api/types'
import { CheckIcon } from '../components/icons'
import { Segmented } from '../components/Segmented'
import { lei } from '../lib/format'
import { paidText, TOP_UP_AMOUNT } from '../lib/payment'
import { button } from '../lib/ui'
import { ErrorLine } from './ReservationActions'

/**
 * "Pay with" while parked: the choice is the driver's preferred method in payment-service, and decides which
 * processor its Factory uses when the session ends.
 */
export function PayWith({ costSoFar }: { costSoFar: number }) {
  const account = usePaymentAccount()
  const choose = useChoosePaymentMethod()
  const topUp = useTopUpWallet()

  if (account.isPending) return <p className="mt-6 text-ink-muted">Loading payment options…</p>
  if (account.isError) {
    return (
      <p className="mt-6 max-w-prose text-ink-muted">
        Payment options couldn’t load, so this session will be paid by card unless you chose the wallet before.
      </p>
    )
  }

  const { walletBalance } = account.data
  // Show the new choice at once; the server confirms it a moment later.
  const method: PaymentMethod = (choose.isPending && choose.variables) || account.data.preferredMethod
  const short = method === 'WALLET' && walletBalance < costSoFar

  return (
    <div className="mt-6">
      <p id="pay-with" className="font-semibold">
        Pay with
      </p>
      <div className="mt-2">
        <Segmented
          label="Pay with"
          options={[
            { value: 'CARD', label: 'Card' },
            { value: 'WALLET', label: 'Wallet' },
          ]}
          value={method}
          onChange={(value) => choose.mutate(value)}
        />
      </div>
      <p className="tabular mt-3 flex flex-wrap items-center gap-x-3 gap-y-2 text-ink-muted">
        Wallet balance: {lei(walletBalance)}.
        <button type="button" className={button.small} disabled={topUp.isPending} onClick={() => topUp.mutate(TOP_UP_AMOUNT)}>
          {topUp.isPending ? 'Adding…' : `Add ${TOP_UP_AMOUNT} lei`}
        </button>
      </p>
      {short && <p className="mt-2 max-w-prose">Your wallet has less than the cost so far. Add money, or pay by card.</p>}
      <ErrorLine error={choose.error ?? topUp.error} />
    </div>
  )
}

/** The receipt's payment line: processing, paid (and how), or what went wrong and the ways to pay now. */
export function PaymentOutcome({ session }: { session: ParkingSession }) {
  const status = session.paymentStatus
  const payment = usePayment(session.paymentId, status)
  const account = usePaymentAccount()
  const queryClient = useQueryClient()

  // A wallet payment changes the balance: reload it once the outcome is known.
  useEffect(() => {
    if (status && status !== 'PENDING') queryClient.invalidateQueries({ queryKey: keys.paymentAccount })
  }, [status, queryClient])

  if (status === 'PENDING') return <p>Processing payment…</p>
  if (status === 'COMPLETED') {
    return (
      <p className="flex items-center gap-2 font-semibold">
        <CheckIcon className="size-5 text-free" />
        {session.cost === 0 ? 'Nothing to pay' : payment.data ? paidText(payment.data.method) : 'Paid'}
      </p>
    )
  }
  if (status !== 'FAILED') return null
  return <PaymentFailed session={session} method={payment.data?.method} walletBalance={account.data?.walletBalance} />
}

function PaymentFailed({
  session,
  method,
  walletBalance,
}: {
  session: ParkingSession
  method?: PaymentMethod
  walletBalance?: number
}) {
  const retry = useRetryPayment()
  const topUp = useTopUpWallet()
  const paymentId = session.paymentId!
  const walletCovers = walletBalance !== undefined && walletBalance >= session.cost
  const tryAgain = (
    <button type="button" className={button.primary} disabled={retry.isPending} onClick={() => retry.mutate({ paymentId })}>
      Try payment again
    </button>
  )

  let text: string
  let actions
  if (method === 'WALLET') {
    text = walletCovers
      ? `Your wallet now has ${lei(walletBalance!)}. Try payment again to pay ${lei(session.cost)} from it.`
      : `Your wallet doesn’t have enough money: ${lei(walletBalance ?? 0)} left, ${lei(session.cost)} due.`
    actions = walletCovers ? (
      <>
        {tryAgain}
        <button type="button" className={button.secondary} disabled={retry.isPending} onClick={() => retry.mutate({ paymentId, method: 'CARD' })}>
          Pay by card instead
        </button>
      </>
    ) : (
      <>
        <button type="button" className={button.primary} disabled={retry.isPending} onClick={() => retry.mutate({ paymentId, method: 'CARD' })}>
          Pay by card instead
        </button>
        <button type="button" className={button.secondary} disabled={topUp.isPending} onClick={() => topUp.mutate(TOP_UP_AMOUNT)}>
          {topUp.isPending ? 'Adding…' : `Add ${TOP_UP_AMOUNT} lei`}
        </button>
      </>
    )
  } else {
    text = walletCovers ? 'Your card was declined. Try again, or pay from your wallet.' : 'Your card was declined. Try again, or use another card.'
    actions = (
      <>
        {tryAgain}
        {method === 'CARD' && walletCovers && (
          <button type="button" className={button.secondary} disabled={retry.isPending} onClick={() => retry.mutate({ paymentId, method: 'WALLET' })}>
            Pay from wallet instead
          </button>
        )}
      </>
    )
  }

  return (
    <>
      <p className="tabular max-w-prose font-semibold">{text}</p>
      <div className="mt-3 flex flex-wrap items-center gap-3">{actions}</div>
      <ErrorLine error={retry.error ?? topUp.error} />
    </>
  )
}

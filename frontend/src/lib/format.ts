export function formatMoney(amount: number, currency = 'INR'): string {
  try {
    return new Intl.NumberFormat('en-IN', { style: 'currency', currency, maximumFractionDigits: 0 }).format(amount)
  } catch {
    return `${currency} ${amount.toFixed(0)}`
  }
}

/** Whether a course has a price set. A course left at zero has not been priced, which is not the same as free. */
export function hasCourseFee(fee: number | null | undefined): fee is number {
  return typeof fee === 'number' && fee > 0
}

/** A course's list price for a student to read before asking to join, or a note that the institute will confirm it. */
export function formatCourseFee(fee: number | null | undefined): string {
  return hasCourseFee(fee) ? formatMoney(fee) : 'To be confirmed by the institute'
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}

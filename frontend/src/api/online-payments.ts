import { apiClient } from './client'

export interface CheckoutConfig {
  enabled: boolean
  keyId: string | null
}

export interface OrderCreated {
  orderId: string
  keyId: string
  amountPaise: number
  currency: string
  courseTitle: string
}

/** CREATED, PAID (money received, not yet in a batch) or ENROLLED. */
export interface OrderStatus {
  orderId: string
  status: 'CREATED' | 'PAID' | 'ENROLLED'
  courseId: number
  batchCode: string | null
  message: string | null
}

export interface RazorpayResult {
  razorpay_order_id: string
  razorpay_payment_id: string
  razorpay_signature: string
}

export async function getCheckoutConfig(): Promise<CheckoutConfig> {
  const { data } = await apiClient.get<CheckoutConfig>('/api/payments/online/config')
  return data
}

export async function createOnlineOrder(courseId: number): Promise<OrderCreated> {
  const { data } = await apiClient.post<OrderCreated>('/api/payments/online/orders', { courseId })
  return data
}

/** The server checks Razorpay's signature, records the fee and places the student in a batch. */
export async function verifyOnlinePayment(result: RazorpayResult): Promise<OrderStatus> {
  const { data } = await apiClient.post<OrderStatus>('/api/payments/online/verify', {
    orderId: result.razorpay_order_id,
    paymentId: result.razorpay_payment_id,
    signature: result.razorpay_signature,
  })
  return data
}

interface RazorpayCheckout {
  open(): void
  on(event: 'payment.failed', handler: (response: { error?: { description?: string } }) => void): void
}
type RazorpayCtor = new (options: Record<string, unknown>) => RazorpayCheckout

let scriptPromise: Promise<RazorpayCtor> | null = null

/** Loads Razorpay's checkout script once. */
function loadRazorpay(): Promise<RazorpayCtor> {
  const existing = (window as unknown as { Razorpay?: RazorpayCtor }).Razorpay
  if (existing) return Promise.resolve(existing)
  scriptPromise ??= new Promise<RazorpayCtor>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = 'https://checkout.razorpay.com/v1/checkout.js'
    script.async = true
    script.onload = () => {
      const ctor = (window as unknown as { Razorpay?: RazorpayCtor }).Razorpay
      if (ctor) resolve(ctor)
      else reject(new Error('Razorpay did not load'))
    }
    script.onerror = () => {
      scriptPromise = null
      reject(new Error('Could not load the payment window. Check your connection and try again.'))
    }
    document.head.appendChild(script)
  })
  return scriptPromise
}

/**
 * Opens Razorpay Checkout for a created order. Resolves with Razorpay's result after a successful payment, rejects
 * with a message when the payment fails, and resolves null when the student closes the window.
 */
export async function payWithRazorpay(
  order: OrderCreated,
  prefill: { name?: string; email?: string; contact?: string },
): Promise<RazorpayResult | null> {
  const Razorpay = await loadRazorpay()
  return new Promise<RazorpayResult | null>((resolve, reject) => {
    const checkout = new Razorpay({
      key: order.keyId,
      order_id: order.orderId,
      amount: order.amountPaise,
      currency: order.currency,
      name: 'Course fee',
      description: order.courseTitle,
      prefill,
      handler: (result: RazorpayResult) => resolve(result),
      modal: { ondismiss: () => resolve(null) },
    })
    checkout.on('payment.failed', (response) => reject(new Error(response.error?.description ?? 'The payment failed.')))
    checkout.open()
  })
}

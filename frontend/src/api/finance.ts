import { apiClient } from './client'

export interface InstallmentResponse {
  id: number
  installmentNo: number
  dueDate: string
  amount: number
  paid: number
  remaining: number
  /** PAID, PARTLY_PAID, DUE, OVERDUE or UPCOMING. */
  state: string
}

export interface PaymentResponse {
  id: number
  feePlanId: number
  studentId: number
  amount: number
  paymentDate: string
  method: string
  referenceNo: string | null
  receiptNo: string
  status: string
  reversedAt: string | null
  reversalReason: string | null
  notes: string | null
  recordedAt: string
}

export interface FeePlanResponse {
  id: number
  studentId: number
  studentCode: string | null
  studentName: string
  courseId: number
  batchId: number | null
  currency: string
  totalFee: number
  discount: number
  netFee: number
  paid: number
  outstanding: number
  overdue: number
  status: string
  nextDueDate: string | null
  nextDueAmount: number | null
  notes: string | null
  installments: InstallmentResponse[]
  payments: PaymentResponse[] | null
}

export interface ReceiptResponse {
  receiptNo: string
  paymentDate: string
  studentCode: string | null
  studentName: string
  courseId: number
  currency: string
  currencySymbol: string
  amount: number
  method: string
  referenceNo: string | null
  status: string
  netFee: number
  paidToDate: number
  balanceAsOfToday: number
}

/** Each fee plan with its schedule, what's paid, outstanding and every payment made (Doc S14). */
export async function myFeePlans(): Promise<FeePlanResponse[]> {
  const { data } = await apiClient.get<FeePlanResponse[]>('/api/fees/me')
  return data
}

export async function getReceipt(paymentId: number | string): Promise<ReceiptResponse> {
  const { data } = await apiClient.get<ReceiptResponse>(`/api/payments/${paymentId}/receipt`)
  return data
}

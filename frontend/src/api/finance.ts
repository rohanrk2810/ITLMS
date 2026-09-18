import { apiClient } from './client'
import type { PageResponse } from './types'

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

export interface FinanceDashboardResponse {
  currency: string
  totalBilled: number
  collected: number
  pending: number
  overdueAmount: number
  overdueInstallments: number
  studentsWithOverdue: number
  activePlans: number
  settledPlans: number
  collectedThisMonth: number
  methodSplitThisMonth: { method: string; payments: number; amount: number }[]
}

export interface OverdueItemResponse {
  feePlanId: number
  installmentId: number
  studentId: number
  studentCode: string | null
  studentName: string
  installmentNo: number
  dueDate: string
  daysOverdue: number
  amountOverdue: number
}

export interface RecordPaymentInput {
  feePlanId: number
  amount: number
  paymentDate?: string
  method: string
  referenceNo?: string
  notes?: string
}

export interface FeePlanInput {
  studentId?: number
  courseId?: number
  batchId?: number
  totalFee: number
  discount?: number
  installmentCount?: number
  firstDueDate?: string
  notes?: string
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

// -----------------------------------------------------------------
// The finance desk (Doc S6.12, S15) - ADMIN, FINANCE, and COORDINATOR
// for read-only figures while chasing a student's dues.
// -----------------------------------------------------------------

export async function getFinanceDashboard(): Promise<FinanceDashboardResponse> {
  const { data } = await apiClient.get<FinanceDashboardResponse>('/api/fees/dashboard')
  return data
}

export async function getOverdueInstallments(): Promise<OverdueItemResponse[]> {
  const { data } = await apiClient.get<OverdueItemResponse[]>('/api/fees/overdue')
  return data
}

export async function searchFeePlans(params: { status?: string; page?: number }): Promise<PageResponse<FeePlanResponse>> {
  const { data } = await apiClient.get<PageResponse<FeePlanResponse>>('/api/fee-plans', { params })
  return data
}

export async function getFeePlan(id: number | string): Promise<FeePlanResponse> {
  const { data } = await apiClient.get<FeePlanResponse>(`/api/fee-plans/${id}`)
  return data
}

export async function createFeePlan(input: FeePlanInput): Promise<FeePlanResponse> {
  const { data } = await apiClient.post<FeePlanResponse>('/api/fee-plans', input)
  return data
}

export async function cancelFeePlan(id: number | string, reason: string): Promise<FeePlanResponse> {
  const { data } = await apiClient.post<FeePlanResponse>(`/api/fee-plans/${id}/cancel`, { reason })
  return data
}

/** Applied to the plan's installments oldest-first (Doc S14), and given a receipt number. */
export async function recordPayment(input: RecordPaymentInput): Promise<PaymentResponse> {
  const { data } = await apiClient.post<PaymentResponse>('/api/payments', input)
  return data
}

export async function reversePayment(id: number | string, reason: string): Promise<PaymentResponse> {
  const { data } = await apiClient.post<PaymentResponse>(`/api/payments/${id}/reverse`, { reason })
  return data
}

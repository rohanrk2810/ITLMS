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
  /** The name of an extra charge such as a development fee; empty for a part of the course fee. */
  label: string | null
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

/** Downloads the receipt as a PDF: the same document that is emailed when the payment is recorded. */
export async function downloadReceiptPdf(paymentId: number | string, receiptNo: string): Promise<void> {
  const { data } = await apiClient.get<ArrayBuffer>(`/api/payments/${paymentId}/receipt/pdf`, { responseType: 'arraybuffer' })
  const url = URL.createObjectURL(new Blob([data], { type: 'application/pdf' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `Receipt-${receiptNo}.pdf`
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}

export interface ChargeInput {
  title: string
  amount: number
  dueDate: string
}

/** Adds a named extra charge (a development fee, a book) to one student's fee plan. The student is told. */
export async function addCharge(feePlanId: number | string, input: ChargeInput): Promise<FeePlanResponse> {
  const { data } = await apiClient.post<FeePlanResponse>(`/api/fee-plans/${feePlanId}/charges`, input)
  return data
}

/** Adds the same charge to the fee plan of every student in a batch. */
export async function addBatchCharge(input: ChargeInput & { batchId: number }): Promise<{ students: number; skipped: number }> {
  const { data } = await apiClient.post<{ students: number; skipped: number }>('/api/fee-plans/charges/batch', input)
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

/** Every fee plan matching the status, read page by page. */
export async function allFeePlans(status: string): Promise<FeePlanResponse[]> {
  const all: FeePlanResponse[] = []
  for (let page = 0; ; page += 1) {
    const result = await searchFeePlans({ status: status || undefined, page })
    all.push(...result.content)
    if (result.last || result.content.length === 0) return all
  }
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

export interface FeeTemplateCharge {
  label: string
  amount: number
  dueDays: number
}

export interface FeeTemplate {
  courseId: number
  configured: boolean
  installmentCount: number
  firstDueDays: number
  charges: FeeTemplateCharge[]
}

/** The usual fee structure of a course: installments, first due date and extra heads. */
export async function getFeeTemplate(courseId: number): Promise<FeeTemplate> {
  const { data } = await apiClient.get<FeeTemplate>(`/api/fees/templates/${courseId}`)
  return data
}

export async function saveFeeTemplate(
  courseId: number,
  input: { installmentCount: number; firstDueDays: number; charges: FeeTemplateCharge[] },
): Promise<FeeTemplate> {
  const { data } = await apiClient.put<FeeTemplate>(`/api/fees/templates/${courseId}`, input)
  return data
}

/** Downloads a student's statement of fees: every course, every payment and the balance. */
export async function downloadStatementPdf(studentId: number | string): Promise<void> {
  const { data } = await apiClient.get<ArrayBuffer>(`/api/fees/students/${studentId}/statement`, { responseType: 'arraybuffer' })
  const url = URL.createObjectURL(new Blob([data], { type: 'application/pdf' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `Fee-statement-${studentId}.pdf`
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}

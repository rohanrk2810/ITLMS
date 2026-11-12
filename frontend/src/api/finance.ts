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
  /** How much of this payment has been given back. */
  refundedAmount: number
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
  discountReason?: string | null
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
  /** Why the discount is given; required when it is set or changed. */
  discountReason?: string
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
  /** True when the charge carries no GST. */
  gstExempt?: boolean
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

/** Every payment received between two dates (inclusive), read page by page. */
export async function paymentsBetween(from: string, to: string): Promise<PaymentResponse[]> {
  const all: PaymentResponse[] = []
  for (let page = 0, last = false; !last; page += 1) {
    const { data } = await apiClient.get<PageResponse<PaymentResponse>>('/api/payments', {
      params: { from, to, page, size: 200 },
    })
    all.push(...data.content)
    last = data.last || data.content.length === 0
  }
  return all
}

export interface RefundResponse {
  id: number
  paymentId: number
  feePlanId: number
  studentId: number
  studentName: string | null
  receiptNo: string | null
  amount: number
  reason: string
  /** REQUESTED, APPROVED or REJECTED. */
  status: string
  requestedAt: string
  decidedAt: string | null
  decisionNote: string | null
  paidOn: string | null
  payMethod: string | null
  payReference: string | null
}

export async function listRefunds(status?: string): Promise<RefundResponse[]> {
  const { data } = await apiClient.get<RefundResponse[]>('/api/fees/refunds', { params: { status } })
  return data
}

export async function requestRefund(input: { paymentId: number; amount: number; reason: string }): Promise<RefundResponse> {
  const { data } = await apiClient.post<RefundResponse>('/api/fees/refunds', input)
  return data
}

export async function approveRefund(id: number, note?: string): Promise<RefundResponse> {
  const { data } = await apiClient.post<RefundResponse>(`/api/fees/refunds/${id}/approve`, note ? { reason: note } : undefined)
  return data
}

export async function rejectRefund(id: number, reason: string): Promise<RefundResponse> {
  const { data } = await apiClient.post<RefundResponse>(`/api/fees/refunds/${id}/reject`, { reason })
  return data
}

/** Changes a plan's terms. The server keeps the schedule when only the discount moves. */
export async function updateFeePlan(id: number | string, input: FeePlanInput): Promise<FeePlanResponse> {
  const { data } = await apiClient.put<FeePlanResponse>(`/api/fee-plans/${id}`, input)
  return data
}

/** Removes a late fee (administrator only; nothing may have been paid on it). */
export async function waiveLateFee(feePlanId: number | string, installmentId: number, reason: string): Promise<FeePlanResponse> {
  const { data } = await apiClient.post<FeePlanResponse>(`/api/fee-plans/${feePlanId}/installments/${installmentId}/waive`, { reason })
  return data
}

export interface CashClosing {
  id: number
  date: string
  expectedCash: number
  countedCash: number
  difference: number
  note: string | null
}

export interface CashPreview {
  date: string
  expectedCash: number
  cashPayments: number
  closing: CashClosing | null
}

export async function cashPreview(date?: string): Promise<CashPreview> {
  const { data } = await apiClient.get<CashPreview>('/api/fees/cash-closings/preview', { params: { date } })
  return data
}

export async function recentCashClosings(): Promise<CashClosing[]> {
  const { data } = await apiClient.get<CashClosing[]>('/api/fees/cash-closings')
  return data
}

export async function closeCashDay(input: { date: string; countedCash: number; note?: string }): Promise<CashClosing> {
  const { data } = await apiClient.post<CashClosing>('/api/fees/cash-closings', input)
  return data
}

export interface ExpenseView {
  id: number
  date: string
  category: string
  amount: number
  description: string
  paidTo: string | null
  referenceNo: string | null
  gstAmount: number
  voided: boolean
  voidReason: string | null
}

export interface ExpenseSummary {
  from: string
  to: string
  collected: number
  spent: number
  profit: number
  byCategory: Record<string, number>
}

export const EXPENSE_CATEGORIES = ['SALARY', 'RENT', 'UTILITIES', 'MARKETING', 'SUPPLIES', 'MAINTENANCE', 'OTHER']

export async function listExpenses(from: string, to: string): Promise<ExpenseView[]> {
  const { data } = await apiClient.get<ExpenseView[]>('/api/fees/expenses', { params: { from, to } })
  return data
}

export async function expenseSummary(from: string, to: string): Promise<ExpenseSummary> {
  const { data } = await apiClient.get<ExpenseSummary>('/api/fees/expenses/summary', { params: { from, to } })
  return data
}

export async function addExpense(input: {
  date?: string
  category: string
  amount: number
  description: string
  paidTo?: string
  referenceNo?: string
  /** The GST inside the amount, claimed back against the GST owed. */
  gstAmount?: number
}): Promise<ExpenseView> {
  const { data } = await apiClient.post<ExpenseView>('/api/fees/expenses', input)
  return data
}

export async function voidExpense(id: number, reason: string): Promise<ExpenseView> {
  const { data } = await apiClient.post<ExpenseView>(`/api/fees/expenses/${id}/void`, { reason })
  return data
}

export interface UpiConfig {
  enabled: boolean
  upiId: string | null
  payeeName: string
}

export interface UpiClaim {
  id: number
  feePlanId: number
  studentId: number
  studentName: string | null
  amount: number
  utr: string
  /** PENDING, CONFIRMED or REJECTED. */
  status: string
  claimedAt: string
  decidedAt: string | null
  decisionNote: string | null
  paymentId: number | null
}

export async function getUpiConfig(): Promise<UpiConfig> {
  const { data } = await apiClient.get<UpiConfig>('/api/fees/upi/config')
  return data
}

export async function claimUpiPayment(input: { feePlanId: number; amount: number; utr: string }): Promise<UpiClaim> {
  const { data } = await apiClient.post<UpiClaim>('/api/fees/upi/claims', input)
  return data
}

export async function listUpiClaims(): Promise<UpiClaim[]> {
  const { data } = await apiClient.get<UpiClaim[]>('/api/fees/upi/claims')
  return data
}

export async function confirmUpiClaim(id: number): Promise<UpiClaim> {
  const { data } = await apiClient.post<UpiClaim>(`/api/fees/upi/claims/${id}/confirm`)
  return data
}

export async function rejectUpiClaim(id: number, reason: string): Promise<UpiClaim> {
  const { data } = await apiClient.post<UpiClaim>(`/api/fees/upi/claims/${id}/reject`, { reason })
  return data
}

/** Records that an approved refund was handed back: how, when and with what reference. */
export async function markRefundPaid(id: number, input: { method: string; reference?: string; paidOn?: string }): Promise<RefundResponse> {
  const { data } = await apiClient.post<RefundResponse>(`/api/fees/refunds/${id}/paid`, input)
  return data
}

export interface BooksLock {
  lockedThrough: string | null
  note: string | null
  setBy: number | null
  setAt: string | null
}

export async function getBooksLock(): Promise<BooksLock> {
  const { data } = await apiClient.get<BooksLock>('/api/fees/books-lock')
  return data
}

/** Closes the books through a date (or reopens them with null). Administrator only. */
export async function setBooksLock(lockedThrough: string | null, note?: string): Promise<BooksLock> {
  const { data } = await apiClient.put<BooksLock>('/api/fees/books-lock', { lockedThrough, note })
  return data
}

export interface GstMonth {
  month: string
  invoices: number
  taxable: number
  exempt: number
  cgst: number
  sgst: number
  igst: number
  outputTax: number
  inputCredit: number
  netPayable: number
  paid: number
}

export interface GstInvoice {
  receiptNo: string
  date: string
  student: string
  state: string
  rate: number
  taxable: number
  exempt: number
  cgst: number
  sgst: number
  igst: number
  total: number
}

export interface GstPaymentRow {
  id: number
  paidOn: string
  forMonth: string | null
  amount: number
  method: string
  reference: string | null
  note: string | null
}

export interface GstReport {
  enabled: boolean
  ratePercent: number
  gstin: string | null
  summary: { months: GstMonth[]; outputTax: number; inputCredit: number; paid: number; stillPayable: number }
  invoices: GstInvoice[]
  payments: GstPaymentRow[]
}

export async function gstSummary(from: string, to: string): Promise<GstReport> {
  const { data } = await apiClient.get<GstReport>('/api/fees/gst/summary', { params: { from, to } })
  return data
}

export interface BankLineView {
  id: number
  date: string
  description: string
  reference: string | null
  debit: number
  credit: number
  matchedKind: string | null
  matchedId: number | null
}

export interface BankEntry {
  kind: string
  id: number
  date: string
  label: string
  reference: string | null
  amount: number
}

export interface BankSummary {
  lines: number
  matched: number
  unmatched: number
  unmatchedIn: number
  unmatchedOut: number
  notOnStatement: BankEntry[]
}

export async function importBankLines(
  lines: { date: string; description: string; reference: string; debit: number; credit: number }[],
): Promise<{ imported: number; duplicates: number; matched: number }> {
  const { data } = await apiClient.post<{ imported: number; duplicates: number; matched: number }>('/api/fees/bank/import', { lines })
  return data
}

export async function bankSummary(): Promise<BankSummary> {
  const { data } = await apiClient.get<BankSummary>('/api/fees/bank/summary')
  return data
}

export async function bankLines(unmatched: boolean): Promise<BankLineView[]> {
  const { data } = await apiClient.get<BankLineView[]>('/api/fees/bank/lines', { params: { unmatched } })
  return data
}

export async function bankCandidates(lineId: number): Promise<BankEntry[]> {
  const { data } = await apiClient.get<BankEntry[]>(`/api/fees/bank/lines/${lineId}/candidates`)
  return data
}

export async function matchBankLine(lineId: number, kind: string, id: number): Promise<BankLineView> {
  const { data } = await apiClient.post<BankLineView>(`/api/fees/bank/lines/${lineId}/match`, { kind, id })
  return data
}

export async function unmatchBankLine(lineId: number): Promise<BankLineView> {
  const { data } = await apiClient.post<BankLineView>(`/api/fees/bank/lines/${lineId}/unmatch`)
  return data
}

export interface GstSettings {
  percent: number
  gstin: string
  sacCode: string
  effectiveFrom: string | null
  fromPortal: boolean
  setAt: string | null
}

export async function getGstSettings(): Promise<GstSettings> {
  const { data } = await apiClient.get<GstSettings>('/api/fees/gst/settings')
  return data
}

/** Sets the GST rate (administrator only). It applies from effectiveFrom; 0 switches GST off. */
export async function saveGstSettings(input: { percent: number; gstin: string; sacCode: string; effectiveFrom?: string }): Promise<GstSettings> {
  const { data } = await apiClient.put<GstSettings>('/api/fees/gst/settings', input)
  return data
}

/** Records GST paid to the government (a challan). */
export async function recordGstPayment(input: { paidOn?: string; forMonth?: string; amount: number; method: string; reference?: string; note?: string }): Promise<GstPaymentRow> {
  const { data } = await apiClient.post<GstPaymentRow>('/api/fees/gst/payments', input)
  return data
}

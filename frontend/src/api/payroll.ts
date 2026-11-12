import { apiClient } from './client'

export interface StaffRow {
  userId: number
  name: string
  email: string | null
  role: string
  basic: number
  allowances: number
  fixedDeduction: number
  active: boolean
  configured: boolean
}

export interface SlipView {
  id: number
  slipNo: string
  month: string
  userId: number
  staffName: string
  designation: string
  daysInMonth: number
  basic: number
  allowances: number
  fixedDeduction: number
  lwpDays: number
  lwpDeduction: number
  otherDeduction: number
  bonus: number
  gross: number
  net: number
  note: string | null
  /** PENDING, APPROVED or PAID. */
  status: string
  paidOn: string | null
  payMethod: string | null
  payReference: string | null
}

export interface MonthSummary {
  month: string
  slips: number
  totalNet: number
  paid: number
  toPay: number
  pending: number
  approved: number
  paidCount: number
}

export interface MonthView {
  summary: MonthSummary
  slips: SlipView[]
}

export interface YearView {
  year: number
  totalNet: number
  paid: number
  toPay: number
  months: MonthSummary[]
}

export async function listStaffPay(): Promise<StaffRow[]> {
  const { data } = await apiClient.get<StaffRow[]>('/api/fees/payroll/staff')
  return data
}

export async function saveStaffPay(
  userId: number,
  input: { basic: number; allowances: number; fixedDeduction: number; active: boolean },
): Promise<StaffRow> {
  const { data } = await apiClient.put<StaffRow>(`/api/fees/payroll/staff/${userId}`, input)
  return data
}

export async function getPayrollMonth(month: string): Promise<MonthView> {
  const { data } = await apiClient.get<MonthView>(`/api/fees/payroll/months/${month}`)
  return data
}

export async function getPayrollYear(year: number): Promise<YearView> {
  const { data } = await apiClient.get<YearView>(`/api/fees/payroll/years/${year}`)
  return data
}

export async function generatePayroll(month: string): Promise<{ created: number }> {
  const { data } = await apiClient.post<{ created: number }>(`/api/fees/payroll/months/${month}/generate`)
  return data
}

export async function updateSlip(
  id: number,
  input: { lwpDays: number; otherDeduction: number; bonus: number; note?: string },
): Promise<SlipView> {
  const { data } = await apiClient.put<SlipView>(`/api/fees/payroll/slips/${id}`, input)
  return data
}

export async function approveMonth(month: string): Promise<{ approved: number }> {
  const { data } = await apiClient.post<{ approved: number }>(`/api/fees/payroll/months/${month}/approve`)
  return data
}

export async function payMonth(month: string, input: { method: string; reference?: string; paidOn?: string }): Promise<{ paid: number }> {
  const { data } = await apiClient.post<{ paid: number }>(`/api/fees/payroll/months/${month}/pay`, input)
  return data
}

export async function paySlip(id: number, input: { method: string; reference?: string; paidOn?: string }): Promise<{ paid: number }> {
  const { data } = await apiClient.post<{ paid: number }>(`/api/fees/payroll/slips/${id}/pay`, input)
  return data
}

export async function approveSlip(id: number): Promise<{ approved: number }> {
  const { data } = await apiClient.post<{ approved: number }>(`/api/fees/payroll/slips/${id}/approve`)
  return data
}

export async function mySlips(): Promise<SlipView[]> {
  const { data } = await apiClient.get<SlipView[]>('/api/fees/payroll/me')
  return data
}

/** Saves a salary slip as a PDF. */
export async function downloadSlipPdf(id: number, slipNo: string): Promise<void> {
  const { data } = await apiClient.get<ArrayBuffer>(`/api/fees/payroll/slips/${id}/pdf`, { responseType: 'arraybuffer' })
  const url = URL.createObjectURL(new Blob([data], { type: 'application/pdf' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `Salary-slip-${slipNo}.pdf`
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}

export interface PayHistoryEntry {
  basic: number
  allowances: number
  fixedDeduction: number
  active: boolean
  changedBy: number | null
  changedAt: string
}

export async function staffPayHistory(userId: number): Promise<PayHistoryEntry[]> {
  const { data } = await apiClient.get<PayHistoryEntry[]>(`/api/fees/payroll/staff/${userId}/history`)
  return data
}

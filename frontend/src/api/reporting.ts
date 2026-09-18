import { apiClient } from './client'
import type { PageResponse } from './types'

export interface AttendanceSnapshot {
  present: number
  absent: number
  late: number
  excused: number
  percent: number
}

/** Institute-wide counters (Doc S15), built from events and cached server-side. */
export interface DashboardSummaryResponse {
  generatedAt: string
  studentsAdmittedTotal: number
  studentsAdmittedLast30Days: number
  enrollmentsTotal: number
  enrollmentsLast30Days: number
  revenueCollectedTotal: number
  revenueCollectedLast30Days: number
  feesBilledTotal: number
  installmentsOverdueTotal: number
  overdueAmountTotal: number
  certificatesIssuedTotal: number
  submissionsEvaluatedTotal: number
  quizAttemptsTotal: number
  quizAttemptsPassed: number
  jobsPostedTotal: number
  placementsSelectedTotal: number
  attendance: AttendanceSnapshot
}

export interface AuditLogResponse {
  id: number
  occurredAt: string
  serviceName: string
  actorUserId: number | null
  actorEmail: string | null
  actorRole: string | null
  action: string
  entityType: string | null
  entityId: string | null
  oldValue: string | null
  newValue: string | null
  ipAddress: string | null
  userAgent: string | null
}

export interface AuditLogFilter {
  serviceName?: string
  action?: string
  entityType?: string
  actorUserId?: number
  from?: string
  to?: string
}

export async function getDashboardSummary(): Promise<DashboardSummaryResponse> {
  const { data } = await apiClient.get<DashboardSummaryResponse>('/api/dashboard/summary')
  return data
}

export async function searchAuditLogs(
  filter: AuditLogFilter,
  page = 0,
): Promise<PageResponse<AuditLogResponse>> {
  const { data } = await apiClient.get<PageResponse<AuditLogResponse>>('/api/audit-logs', {
    params: { ...filter, page },
  })
  return data
}

/** Same filters as the search above; capped at itilms.reporting.max-export-rows on the server. */
export async function exportAuditLogs(filter: AuditLogFilter, format: 'CSV' | 'XLSX' | 'PDF'): Promise<void> {
  const response = await apiClient.get('/api/reports/audit-logs/export', {
    params: { ...filter, format },
    responseType: 'blob',
  })
  const disposition = response.headers['content-disposition'] as string | undefined
  const filename = disposition?.match(/filename="?([^"]+)"?/)?.[1] ?? `audit-log.${format.toLowerCase()}`
  const url = URL.createObjectURL(response.data as Blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 30_000)
}

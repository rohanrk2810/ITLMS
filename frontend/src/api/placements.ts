import { apiClient } from './client'
import type { PageResponse } from './types'

export interface CompanyResponse {
  id: number
  name: string
  industry: string | null
  website: string | null
  location: string | null
  contactName: string | null
  contactEmail: string | null
  contactPhone: string | null
  notes: string | null
  active: boolean
}

export interface CompanyInput {
  name: string
  industry?: string
  website?: string
  location?: string
  contactName?: string
  contactEmail?: string
  contactPhone?: string
  notes?: string
  active?: boolean
}

/** A job opening. `eligible`/`ineligibleReasons`/`myApplicationStage` are set only on the student view. */
export interface JobResponse {
  id: number
  companyId: number
  companyName: string
  title: string
  description: string | null
  jobType: 'FULL_TIME' | 'INTERNSHIP' | 'CONTRACT' | 'PART_TIME'
  location: string | null
  packageOffered: string | null
  openings: number | null
  applicationDeadline: string | null
  eligibleCourseIds: number[]
  requireCertificate: boolean
  minAttendancePercent: number | null
  minScorePercent: number | null
  eligibilityNotes: string | null
  status: 'DRAFT' | 'OPEN' | 'CLOSED' | 'CANCELLED'
  publishedAt: string | null
  applicationCount: number | null
  eligible: boolean | null
  ineligibleReasons: string[] | null
  myApplicationStage: string | null
}

export interface JobInput {
  companyId: number
  title: string
  description?: string
  jobType: JobResponse['jobType']
  location?: string
  packageOffered?: string
  openings?: number
  applicationDeadline?: string
  eligibleCourseIds?: number[]
  requireCertificate?: boolean
  minAttendancePercent?: number
  minScorePercent?: number
  eligibilityNotes?: string
}

export interface ApplicationResponse {
  id: number
  jobId: number
  jobTitle: string
  companyName: string
  studentId: number
  studentName: string
  courseId: number | null
  resumeRef: string | null
  coverNote: string | null
  appliedAt: string
  stage: 'APPLIED' | 'SHORTLISTED' | 'INTERVIEW' | 'ON_HOLD' | 'SELECTED' | 'REJECTED' | 'WITHDRAWN'
  currentRound: number | null
  nextInterviewAt: string | null
  offerDetails: string | null
  decidedAt: string | null
}

export interface StageChangeResponse {
  fromStage: string | null
  toStage: string
  roundNo: number | null
  note: string | null
  changedBy: number | null
  changedAt: string
}

export interface StageChangeInput {
  stage: string
  roundNo?: number
  nextInterviewAt?: string
  offerDetails?: string
  note?: string
}

export interface PlacementDashboardResponse {
  openJobs: number
  totalApplications: number
  applicationsByStage: Record<string, number>
  selected: number
  placementsByCompany: { companyId: number; companyName: string; selected: number }[]
}

// ---- Companies ----

export async function listCompanies(activeOnly = false): Promise<CompanyResponse[]> {
  const { data } = await apiClient.get<CompanyResponse[]>('/api/companies', { params: { activeOnly } })
  return data
}

export async function getCompany(id: number | string): Promise<CompanyResponse> {
  const { data } = await apiClient.get<CompanyResponse>(`/api/companies/${id}`)
  return data
}

export async function createCompany(input: CompanyInput): Promise<CompanyResponse> {
  const { data } = await apiClient.post<CompanyResponse>('/api/companies', input)
  return data
}

export async function updateCompany(id: number | string, input: CompanyInput): Promise<CompanyResponse> {
  const { data } = await apiClient.put<CompanyResponse>(`/api/companies/${id}`, input)
  return data
}

// ---- Jobs ----

export async function listJobs(params: { status?: string; companyId?: number }): Promise<PageResponse<JobResponse>> {
  const { data } = await apiClient.get<PageResponse<JobResponse>>('/api/jobs', { params })
  return data
}

export async function getJob(id: number | string): Promise<JobResponse> {
  const { data } = await apiClient.get<JobResponse>(`/api/jobs/${id}`)
  return data
}

export async function createJob(input: JobInput): Promise<JobResponse> {
  const { data } = await apiClient.post<JobResponse>('/api/jobs', input)
  return data
}

export async function updateJob(id: number | string, input: JobInput): Promise<JobResponse> {
  const { data } = await apiClient.put<JobResponse>(`/api/jobs/${id}`, input)
  return data
}

export async function publishJob(id: number | string): Promise<JobResponse> {
  const { data } = await apiClient.post<JobResponse>(`/api/jobs/${id}/publish`)
  return data
}

export async function closeJob(id: number | string): Promise<JobResponse> {
  const { data } = await apiClient.post<JobResponse>(`/api/jobs/${id}/close`)
  return data
}

export async function cancelJob(id: number | string): Promise<JobResponse> {
  const { data } = await apiClient.post<JobResponse>(`/api/jobs/${id}/cancel`)
  return data
}

export async function jobApplications(id: number | string): Promise<ApplicationResponse[]> {
  const { data } = await apiClient.get<ApplicationResponse[]>(`/api/jobs/${id}/applications`)
  return data
}

// ---- Applying (student) ----

export interface ApplyInput {
  resumeRef?: string
  coverNote?: string
}

export async function applyToJob(id: number | string, input: ApplyInput): Promise<ApplicationResponse> {
  const { data } = await apiClient.post<ApplicationResponse>(`/api/jobs/${id}/apply`, input)
  return data
}

export async function myApplications(): Promise<ApplicationResponse[]> {
  const { data } = await apiClient.get<ApplicationResponse[]>('/api/applications/me')
  return data
}

export async function withdrawApplication(id: number | string): Promise<ApplicationResponse> {
  const { data } = await apiClient.post<ApplicationResponse>(`/api/applications/${id}/withdraw`)
  return data
}

export async function applicationHistory(id: number | string): Promise<StageChangeResponse[]> {
  const { data } = await apiClient.get<StageChangeResponse[]>(`/api/applications/${id}/history`)
  return data
}

export async function changeApplicationStage(
  id: number | string,
  input: StageChangeInput,
): Promise<ApplicationResponse> {
  const { data } = await apiClient.put<ApplicationResponse>(`/api/applications/${id}/stage`, input)
  return data
}

// ---- Placement records and dashboard ----

export async function placementRecords(): Promise<ApplicationResponse[]> {
  const { data } = await apiClient.get<ApplicationResponse[]>('/api/placements')
  return data
}

export async function placementDashboard(): Promise<PlacementDashboardResponse> {
  const { data } = await apiClient.get<PlacementDashboardResponse>('/api/placements/dashboard')
  return data
}

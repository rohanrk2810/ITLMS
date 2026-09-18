import { apiClient } from './client'
import type { PageResponse } from './types'

export interface LeadResponse {
  id: number
  name: string
  phone: string
  email: string | null
  source: string
  status: string
  interestedCourseId: number | null
  counselorUserId: number | null
  nextFollowUpAt: string | null
  overdue: boolean
  notes: string | null
  lostReason: string | null
  convertedStudentId: number | null
  convertedAt: string | null
  followupCount: number
  createdAt: string
}

export interface FollowupResponse {
  id: number
  leadId: number
  contactedAt: string
  outcome: string
  remark: string | null
  nextActionAt: string | null
  createdBy: number | null
}

export interface CreateLeadInput {
  name: string
  phone: string
  email?: string
  source: string
  interestedCourseId?: number
  notes?: string
}

export interface AddFollowupInput {
  outcome: string
  remark?: string
  nextActionAt?: string
  newStatus?: string
}

export interface ConvertLeadInput {
  courseId: number
  batchId?: number
  totalFee: number
  discount?: number
  installments?: number
  admissionDate?: string
}

export interface AdmissionResultResponse {
  leadId: number
  studentId: number
  studentCode: string
  userId: number
  email: string
  courseId: number
  batchId: number | null
  feePlanRequested: boolean
  message: string
}

export async function searchLeads(params: {
  status?: string
  query?: string
  openOnly?: boolean
  page?: number
}): Promise<PageResponse<LeadResponse>> {
  const { data } = await apiClient.get<PageResponse<LeadResponse>>('/api/leads', { params })
  return data
}

export async function getLead(id: number | string): Promise<LeadResponse> {
  const { data } = await apiClient.get<LeadResponse>(`/api/leads/${id}`)
  return data
}

export async function createLead(input: CreateLeadInput): Promise<LeadResponse> {
  const { data } = await apiClient.post<LeadResponse>('/api/leads', input)
  return data
}

export async function getFollowups(leadId: number | string): Promise<FollowupResponse[]> {
  const { data } = await apiClient.get<FollowupResponse[]>(`/api/leads/${leadId}/followups`)
  return data
}

export async function addFollowup(leadId: number | string, input: AddFollowupInput): Promise<FollowupResponse> {
  const { data } = await apiClient.post<FollowupResponse>(`/api/leads/${leadId}/followups`, input)
  return data
}

export async function convertLead(
  leadId: number | string,
  input: ConvertLeadInput,
): Promise<AdmissionResultResponse> {
  const { data } = await apiClient.post<AdmissionResultResponse>(`/api/leads/${leadId}/convert`, input)
  return data
}

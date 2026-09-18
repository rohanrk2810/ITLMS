import { apiClient } from './client'

export interface LiveSessionResponse {
  id: number
  classSessionId: number
  batchId: number
  batchCode: string
  courseTitle: string
  trainerId: number | null
  topic: string | null
  roomName: string
  sessionDate: string
  startTime: string
  endTime: string
  scheduledStartAt: string
  scheduledEndAt: string
  status: string
  startedAt: string | null
  endedAt: string | null
  peakParticipants: number
  recordingEnabled: boolean
  recordingUrl: string | null
  attendanceComputed: boolean
  /** True when the room will accept a join request right now. */
  joinable: boolean
}

/** Everything the browser needs to enter a live class, and nothing more. */
export interface JoinTokenResponse {
  liveSessionId: number
  classSessionId: number
  batchId: number
  batchCode: string
  courseTitle: string
  topic: string | null
  roomName: string
  serverUrl: string
  token: string
  identity: string
  displayName: string
  role: string
  canPublish: boolean
  roomAdmin: boolean
  recording: boolean
  scheduledStartAt: string
  scheduledEndAt: string
  expiresAt: string
}

/** My upcoming live classes - students and trainers see their own batches. */
export async function upcomingLiveClasses(): Promise<LiveSessionResponse[]> {
  const { data } = await apiClient.get<LiveSessionResponse[]>('/api/liveclass/upcoming')
  return data
}

export async function getLiveSessionStatus(classSessionId: number | string): Promise<LiveSessionResponse> {
  const { data } = await apiClient.get<LiveSessionResponse>(`/api/liveclass/class-sessions/${classSessionId}`)
  return data
}

export async function joinLiveClass(classSessionId: number | string): Promise<JoinTokenResponse> {
  const { data } = await apiClient.post<JoinTokenResponse>(`/api/liveclass/class-sessions/${classSessionId}/join`)
  return data
}

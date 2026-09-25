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
  canMic: boolean
  canCamera: boolean
  canScreenShare: boolean
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

export interface RoomPolicy {
  studentsCanMic: boolean
  studentsCanCamera: boolean
  studentsCanShareScreen: boolean
}

export interface ParticipantControl {
  userId: number
  displayName: string
  role: string
  inRoom: boolean
  microphone: boolean
  camera: boolean
  screenShare: boolean
  microphoneOverride: boolean | null
  cameraOverride: boolean | null
  screenShareOverride: boolean | null
}

export interface RoomControls {
  policy: RoomPolicy
  participants: ParticipantControl[]
}

export type MuteSource = 'MICROPHONE' | 'CAMERA' | 'SCREEN_SHARE'

export async function getRoomControls(liveSessionId: number): Promise<RoomControls> {
  const { data } = await apiClient.get<RoomControls>(`/api/liveclass/sessions/${liveSessionId}/controls`)
  return data
}

export async function updateRoomPolicy(liveSessionId: number, policy: Partial<RoomPolicy>): Promise<RoomControls> {
  const { data } = await apiClient.put<RoomControls>(`/api/liveclass/sessions/${liveSessionId}/policy`, policy)
  return data
}

export async function updateParticipantPermissions(
  liveSessionId: number,
  userId: number,
  change: { microphone?: boolean; camera?: boolean; screenShare?: boolean; followRoom?: boolean },
): Promise<RoomControls> {
  const { data } = await apiClient.put<RoomControls>(
    `/api/liveclass/sessions/${liveSessionId}/participants/${userId}/permissions`,
    change,
  )
  return data
}

export async function muteParticipant(liveSessionId: number, userId: number, source: MuteSource): Promise<number> {
  const { data } = await apiClient.post<{ muted: number }>(
    `/api/liveclass/sessions/${liveSessionId}/participants/${userId}/mute`,
    { source },
  )
  return data.muted
}

export async function muteAllStudents(liveSessionId: number): Promise<number> {
  const { data } = await apiClient.post<{ muted: number }>(`/api/liveclass/sessions/${liveSessionId}/mute-all`)
  return data.muted
}

export async function removeFromClass(liveSessionId: number, userId: number): Promise<void> {
  await apiClient.delete(`/api/liveclass/sessions/${liveSessionId}/participants/${userId}`)
}

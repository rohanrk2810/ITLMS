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
  /** A capture is running right now. */
  recording: boolean
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

export async function pastLiveClasses(): Promise<LiveSessionResponse[]> {
  const { data } = await apiClient.get<LiveSessionResponse[]>('/api/liveclass/past')
  return data
}

export async function getLiveSessionStatus(classSessionId: number | string): Promise<LiveSessionResponse> {
  const { data } = await apiClient.get<LiveSessionResponse>(`/api/liveclass/class-sessions/${classSessionId}`)
  return data
}

/** The host's view of a class by its live-session id - the one with the recording state and participant list. */
export async function getLiveSession(liveSessionId: number): Promise<LiveSessionResponse> {
  const { data } = await apiClient.get<LiveSessionResponse>(`/api/liveclass/sessions/${liveSessionId}`)
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

export async function startRecording(liveSessionId: number): Promise<void> {
  await apiClient.post(`/api/liveclass/sessions/${liveSessionId}/recording/start`)
}

export async function stopRecording(liveSessionId: number): Promise<void> {
  await apiClient.post(`/api/liveclass/sessions/${liveSessionId}/recording/stop`)
}

/**
 * Fetches the whole recording as a blob and hands back an object URL for a `<video>` tag. The backend does not
 * honour byte ranges, so this downloads the file once rather than streaming it - fine for a class recording, and it
 * lets the request carry the normal Authorization header, which a plain `<video src>` cannot do.
 */
export async function fetchRecording(liveSessionId: number): Promise<string> {
  const { data } = await apiClient.get(`/api/liveclass/sessions/${liveSessionId}/recording`, { responseType: 'blob' })
  return URL.createObjectURL(data as Blob)
}

export type LiveQuestionType = 'MCQ' | 'MULTIPLE_SELECT' | 'TRUE_FALSE' | 'SHORT_ANSWER' | 'CODING' | 'OTHER'

export const LIVE_QUESTION_TYPE_LABEL: Record<LiveQuestionType, string> = {
  MCQ: 'Single choice (MCQ)',
  MULTIPLE_SELECT: 'Multiple select',
  TRUE_FALSE: 'True / False',
  SHORT_ANSWER: 'Short answer',
  CODING: 'Coding',
  OTHER: 'Open question',
}

export interface AskQuestionInput {
  type: LiveQuestionType
  prompt: string
  options?: string[]
  correctOptions?: number[]
  acceptedAnswers?: string[]
  language?: string
  starterCode?: string
  explanation?: string
  marks?: number
}

export interface AnswerQuestionInput {
  selected?: number[]
  text?: string
  code?: string
  language?: string
}

export interface LiveQuestionMyAnswer {
  selected: number[] | null
  text: string | null
  code: string | null
  language: string | null
  correct: boolean | null
  awardedMarks: number | null
  viaRecording: boolean
  submittedAt: string
}

export interface LiveQuestion {
  id: number
  liveSessionId: number
  classSessionId: number
  type: LiveQuestionType
  prompt: string
  options: string[] | null
  language: string | null
  starterCode: string | null
  marks: number
  status: 'OPEN' | 'CLOSED'
  askedAt: string
  offsetSeconds: number
  offsetLabel: string
  correctOptions: number[] | null
  acceptedAnswers: string[] | null
  explanation: string | null
  answered: number | null
  correctCount: number | null
  myAnswer: LiveQuestionMyAnswer | null
}

export interface LiveAnswer {
  userId: number
  studentId: number | null
  displayName: string | null
  selected: number[] | null
  text: string | null
  code: string | null
  language: string | null
  correct: boolean | null
  awardedMarks: number | null
  viaRecording: boolean
  submittedAt: string
}

export async function askQuestion(liveSessionId: number, input: AskQuestionInput): Promise<LiveQuestion> {
  const { data } = await apiClient.post<LiveQuestion>(`/api/liveclass/sessions/${liveSessionId}/questions`, input)
  return data
}

export async function closeQuestion(questionId: number): Promise<LiveQuestion> {
  const { data } = await apiClient.post<LiveQuestion>(`/api/liveclass/questions/${questionId}/close`)
  return data
}

export async function questionAnswers(questionId: number): Promise<LiveAnswer[]> {
  const { data } = await apiClient.get<LiveAnswer[]>(`/api/liveclass/questions/${questionId}/answers`)
  return data
}

export async function classQuestions(classSessionId: number | string): Promise<LiveQuestion[]> {
  const { data } = await apiClient.get<LiveQuestion[]>(`/api/liveclass/class-sessions/${classSessionId}/questions`)
  return data
}

export async function openQuestion(classSessionId: number | string): Promise<LiveQuestion | null> {
  const { data, status } = await apiClient.get<LiveQuestion>(`/api/liveclass/class-sessions/${classSessionId}/questions/open`, {
    validateStatus: (s) => s === 200 || s === 204,
  })
  return status === 204 ? null : data
}

export async function answerQuestion(questionId: number, input: AnswerQuestionInput): Promise<LiveQuestion> {
  const { data } = await apiClient.post<LiveQuestion>(`/api/liveclass/questions/${questionId}/answer`, input)
  return data
}

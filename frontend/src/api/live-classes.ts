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
  /** Bytes of the recording stored so far (or in total, once finished). */
  recordingBytes: number
  attendanceComputed: boolean
  /** How many phrases of the class's written transcript exist; 0 when none was captured. */
  transcriptLines: number
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
  expiresAt: string | null
  /** Set when the class runs on Google Meet, Zoom etc.; there is then no room, token or server URL. */
  externalUrl: string | null
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

export interface BrowserRecordingState {
  nextChunk: number
  bytes: number
}

export async function startBrowserRecording(liveSessionId: number): Promise<void> {
  await apiClient.post(`/api/liveclass/sessions/${liveSessionId}/recording/browser/start`)
}

/** One piece of the browser recording, as raw bytes. Sending a piece the server already has is harmless. */
export async function uploadRecordingChunk(liveSessionId: number, seq: number, chunk: Blob): Promise<BrowserRecordingState> {
  const { data } = await apiClient.put<BrowserRecordingState>(
    `/api/liveclass/sessions/${liveSessionId}/recording/browser/chunks/${seq}`,
    chunk,
    { headers: { 'Content-Type': 'application/octet-stream' }, timeout: 120_000 },
  )
  return data
}

export async function stopBrowserRecording(liveSessionId: number): Promise<BrowserRecordingState> {
  const { data } = await apiClient.post<BrowserRecordingState>(`/api/liveclass/sessions/${liveSessionId}/recording/browser/stop`)
  return data
}

/** Saves the recording to the person's computer. The request carries the login, so it goes through a blob. */
/** Closes the room for everyone and records attendance now. Only the class's trainer or staff. */
export async function endLiveClass(liveSessionId: number): Promise<LiveSessionResponse> {
  const { data } = await apiClient.post<LiveSessionResponse>(`/api/liveclass/sessions/${liveSessionId}/end`)
  return data
}

/** One finished phrase of what the trainer said. `spokenAt` is epoch milliseconds on this browser's clock. */
export interface TranscriptPhrase {
  clientId: string
  text: string
  language: string
  spokenAt: number
}

/** Saves finished phrases while the class is live. A phrase sent twice is stored once, so a retry is safe. */
export async function saveTranscript(liveSessionId: number, phrases: TranscriptPhrase[]): Promise<number> {
  const { data } = await apiClient.post<{ saved: number }>(`/api/liveclass/sessions/${liveSessionId}/transcript`, { phrases })
  return data.saved
}

/** Downloads what the trainer said, as a text file. 404 when nothing was captured. */
export async function downloadTranscript(liveSessionId: number): Promise<void> {
  const response = await apiClient.get(`/api/liveclass/sessions/${liveSessionId}/transcript`, { responseType: 'blob' })
  const header = String(response.headers['content-disposition'] ?? '')
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(header)
  const name = match ? decodeURIComponent(match[1]) : `class-${liveSessionId}-transcript.txt`
  const url = URL.createObjectURL(response.data as Blob)
  const link = document.createElement('a')
  link.href = url
  link.download = name
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
}

export async function downloadRecording(liveSessionId: number): Promise<void> {
  const response = await apiClient.get(`/api/liveclass/sessions/${liveSessionId}/recording`, {
    params: { download: true },
    responseType: 'blob',
    timeout: 0,
  })
  const header = String(response.headers['content-disposition'] ?? '')
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(header)
  const name = match ? decodeURIComponent(match[1]) : `class-${liveSessionId}.webm`
  const url = URL.createObjectURL(response.data as Blob)
  const link = document.createElement('a')
  link.href = url
  link.download = name
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
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
  /** Code shown above the question. */
  snippet?: string
  snippetLanguage?: string
  /** A small database shown as tables. */
  dataset?: DataTable[]
  /** CODING: what a correct program prints for each input. A test marks the code with these. */
  testCases?: CodeTestCase[]
  /** CODING in SQL: CREATE TABLE and INSERT statements that run before the student's query. */
  setupSql?: string
  allowLanguageChoice?: boolean
}

export interface CodeTestCase {
  input: string
  expectedOutput: string
  hidden: boolean
  weight: number
}

export interface DataTable {
  name: string
  columns: string[]
  rows: string[][]
}

export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'

/** Where a question sits in the bank: how to find it again, and whether other trainers may use it. */
export interface MaterialMeta {
  courseId?: number | null
  topic?: string
  tags?: string[]
  difficulty?: Difficulty | ''
  shared?: boolean
  /** Where it is filed, like Java/OOPs/Coding. */
  folder?: string
}

/** A question in the trainer's bank, or one another trainer shared (mine = false). */
export interface LiveMaterial {
  id: number
  title: string
  question: AskQuestionInput
  courseId: number | null
  topic: string | null
  tags: string[]
  difficulty: Difficulty | null
  shared: boolean
  mine: boolean
  folder: string | null
  ownerName: string | null
  createdAt: string
}

export interface MaterialFilter {
  scope?: 'mine' | 'shared'
  query?: string
  courseId?: number
  tag?: string
  difficulty?: Difficulty
  /** A folder path; its sub-folders are included. */
  folder?: string
  type?: LiveQuestionType
}

export interface BankFolder {
  path: string
  /** Questions filed directly in this folder, not in its sub-folders. */
  count: number
}

/** The tables of a question whose database is too big to travel with it. */
export async function questionDataset(questionId: number): Promise<DataTable[]> {
  const { data } = await apiClient.get<DataTable[]>(`/api/liveclass/questions/${questionId}/dataset`)
  return data
}

export async function listFolders(): Promise<BankFolder[]> {
  const { data } = await apiClient.get<BankFolder[]>('/api/liveclass/materials/folders')
  return data
}

export async function createFolder(path: string): Promise<BankFolder> {
  const { data } = await apiClient.post<BankFolder>('/api/liveclass/materials/folders', { path })
  return data
}

/** Renames a folder and everything under it, or moves it by giving a longer path. */
export async function renameFolder(path: string, to: string): Promise<void> {
  await apiClient.put('/api/liveclass/materials/folders', { path, to })
}

/** Deletes the folder with every folder inside it and every question filed in them. */
export async function deleteFolder(path: string): Promise<{ questions: number; folders: number }> {
  const { data } = await apiClient.delete<{ questions: number; folders: number }>('/api/liveclass/materials/folders', {
    params: { path },
  })
  return data
}

export async function listMaterials(params: MaterialFilter = {}): Promise<LiveMaterial[]> {
  const { data } = await apiClient.get<LiveMaterial[]>('/api/liveclass/materials', { params })
  return data
}

export async function saveMaterial(
  title: string,
  question: AskQuestionInput,
  meta: MaterialMeta = {},
  id?: number,
): Promise<LiveMaterial> {
  const body = { title, question, ...meta, difficulty: meta.difficulty || undefined }
  const { data } = id
    ? await apiClient.put<LiveMaterial>(`/api/liveclass/materials/${id}`, body)
    : await apiClient.post<LiveMaterial>('/api/liveclass/materials', body)
  return data
}

export interface MaterialImportResult {
  imported: number
  errors: { row: number; message: string }[]
}

/** Saves every usable row of a filled-in template to the bank; rows with a mistake come back in errors. */
export async function importMaterials(file: File): Promise<MaterialImportResult> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await apiClient.post<MaterialImportResult>('/api/liveclass/materials/import', form)
  return data
}

/** The Excel template, saved through the browser. */
export async function downloadMaterialTemplate(): Promise<void> {
  const { data } = await apiClient.get<Blob>('/api/liveclass/materials/import/template', { responseType: 'blob' })
  const url = URL.createObjectURL(data)
  const link = document.createElement('a')
  link.href = url
  link.download = 'question-bank-template.xlsx'
  link.click()
  URL.revokeObjectURL(url)
}

export async function copyMaterial(id: number): Promise<LiveMaterial> {
  const { data } = await apiClient.post<LiveMaterial>(`/api/liveclass/materials/${id}/copy`)
  return data
}

export async function deleteMaterial(id: number): Promise<void> {
  await apiClient.delete(`/api/liveclass/materials/${id}`)
}

export async function sendMaterial(id: number, liveSessionId: number): Promise<LiveQuestion> {
  const { data } = await apiClient.post<LiveQuestion>(`/api/liveclass/materials/${id}/send`, null, {
    params: { liveSessionId },
  })
  return data
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
  snippet: string | null
  snippetLanguage: string | null
  dataset: DataTable[] | null
  /** The tables were too big to include; load them with questionDataset(). */
  datasetLarge: boolean
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

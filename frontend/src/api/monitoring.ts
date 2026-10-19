import { apiClient } from './client'

export type MonitoringScope = 'INSTITUTE' | 'COURSE' | 'BATCH' | 'SESSION'

export interface MonitoringSetting {
  id: number
  scopeType: MonitoringScope
  scopeId: number
  enabled: boolean
  faceVisibility: boolean
  cameraRequired: boolean
  microphoneRequired: boolean
  warningAfterSeconds: number
  showWarning: boolean
  warningMessage: string | null
  logEvents: boolean
  updatedAt: string
  tabSwitchCheck: boolean
  attendancePenalty: boolean
  strictSeverity: boolean
  autoRemove: boolean
  autoRemoveAfterCritical: number
  alertTrainer: boolean
}

export interface MonitoringSettingInput {
  scopeType: MonitoringScope
  scopeId?: number
  enabled: boolean
  faceVisibility: boolean
  cameraRequired: boolean
  /** Students must allow the microphone to join. Permission only: nothing is listened to. */
  microphoneRequired: boolean
  warningAfterSeconds: number
  showWarning: boolean
  warningMessage: string
  logEvents: boolean
  tabSwitchCheck: boolean
  attendancePenalty: boolean
  strictSeverity: boolean
  autoRemove: boolean
  autoRemoveAfterCritical: number
  alertTrainer: boolean
}

/** What applies to one class. `enabled` is false unless an administrator switched monitoring on. */
export interface EffectiveMonitoring {
  enabled: boolean
  faceVisibility: boolean
  cameraRequired: boolean
  microphoneRequired: boolean
  warningAfterSeconds: number
  showWarning: boolean
  warningMessage: string | null
  logEvents: boolean
  source: string
  tabSwitchCheck: boolean
  attendancePenalty: boolean
  strictSeverity: boolean
  autoRemove: boolean
  autoRemoveAfterCritical: number
  alertTrainer: boolean
}

export type MonitoringEventType =
  | 'FACE_NOT_DETECTED'
  | 'FACE_RESTORED'
  | 'MULTIPLE_FACES'
  | 'CAMERA_DISABLED'
  | 'CAMERA_PERMISSION_DENIED'
  | 'MICROPHONE_DISABLED'
  | 'MICROPHONE_PERMISSION_DENIED'
  | 'TAB_AWAY'
  | 'REMOVED_BY_MONITORING'

export interface MonitoringEvent {
  id: number
  studentId: number
  studentName: string | null
  type: MonitoringEventType
  severity: 'INFO' | 'WARNING' | 'CRITICAL'
  occurredAt: string
  offsetSeconds: number
  offsetLabel: string
  durationSeconds: number | null
  detail: string | null
}

export const DEFAULT_WARNING = 'Please sit properly and keep your face visible.'

export async function listMonitoringSettings(): Promise<MonitoringSetting[]> {
  const { data } = await apiClient.get<MonitoringSetting[]>('/api/liveclass/monitoring/settings')
  return data
}

export async function saveMonitoringSetting(input: MonitoringSettingInput): Promise<MonitoringSetting> {
  const { data } = await apiClient.put<MonitoringSetting>('/api/liveclass/monitoring/settings', input)
  return data
}

export async function removeMonitoringSetting(id: number): Promise<void> {
  await apiClient.delete(`/api/liveclass/monitoring/settings/${id}`)
}

export async function getClassMonitoring(classSessionId: number): Promise<EffectiveMonitoring> {
  const { data } = await apiClient.get<EffectiveMonitoring>(
    `/api/liveclass/class-sessions/${classSessionId}/monitoring`,
  )
  return data
}

export async function reportMonitoringEvent(
  classSessionId: number,
  event: { type: MonitoringEventType; durationSeconds?: number; detail?: string },
): Promise<void> {
  await apiClient.post(`/api/liveclass/class-sessions/${classSessionId}/monitoring/events`, event)
}

export async function listMonitoringEvents(classSessionId: number): Promise<MonitoringEvent[]> {
  const { data } = await apiClient.get<MonitoringEvent[]>(
    `/api/liveclass/class-sessions/${classSessionId}/monitoring/events`,
  )
  return data
}

/** One student's monitoring summary for a class. */
export interface StudentMonitoringReport {
  studentId: number
  studentName: string | null
  faceAbsences: number
  faceAbsentSeconds: number
  tabSwitches: number
  tabAwaySeconds: number
  multipleFaces: number
  cameraProblems: number
  warnings: number
  critical: number
  removed: boolean
  penaltySeconds: number
}

export async function getMonitoringReport(classSessionId: number): Promise<StudentMonitoringReport[]> {
  const { data } = await apiClient.get<StudentMonitoringReport[]>(
    `/api/liveclass/class-sessions/${classSessionId}/monitoring/report`,
  )
  return data
}

/** The switches that can each be turned on or off on their own, with the words a person sees for them. */
export const MONITORING_SWITCHES = [
  { key: 'faceVisibility', label: 'Face check', hint: 'Warn and note when the face is not visible.' },
  { key: 'tabSwitchCheck', label: 'Tab-switch check', hint: 'Note when a student leaves the class tab or window.' },
  { key: 'strictSeverity', label: 'Strict severity', hint: 'A missing face or a second face counts as critical at once.' },
  {
    key: 'attendancePenalty',
    label: 'Attendance penalty',
    hint: 'Time with the face missing or the tab left is not counted as attended.',
  },
  { key: 'alertTrainer', label: 'Alert the trainer', hint: 'Show an alert here for every critical note.' },
  {
    key: 'autoRemove',
    label: 'Auto-remove',
    hint: 'Remove a student from the room after several critical notes (they may rejoin).',
  },
] as const

export type MonitoringSwitchKey = (typeof MONITORING_SWITCHES)[number]['key']

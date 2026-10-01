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
}

export type MonitoringEventType =
  | 'FACE_NOT_DETECTED'
  | 'FACE_RESTORED'
  | 'MULTIPLE_FACES'
  | 'CAMERA_DISABLED'
  | 'CAMERA_PERMISSION_DENIED'
  | 'MICROPHONE_DISABLED'
  | 'MICROPHONE_PERMISSION_DENIED'

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

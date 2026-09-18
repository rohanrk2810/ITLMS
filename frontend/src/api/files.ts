import { apiClient } from './client'

export interface FileResponse {
  id: number
  filename: string
  contentType: string
  sizeBytes: number
  category: string
  ownerUserId: number
  uploadedAt: string
  downloadUrl: string
}

/** Uploads course material (or any other category) to file-service through the gateway. */
export async function uploadFile(file: File, category: string): Promise<FileResponse> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await apiClient.post<FileResponse>('/api/files', form, { params: { category } })
  return data
}

/**
 * Downloads a file and triggers a save-as, for the many places a plain `<a
 * href>` won't do because the download needs a bearer token (Doc S12: no
 * file-service download is anonymous).
 */
export async function downloadFile(fileId: number | string, filename: string): Promise<void> {
  const { data } = await apiClient.get<ArrayBuffer>(`/api/files/${fileId}/download`, {
    responseType: 'arraybuffer',
  })
  const url = URL.createObjectURL(new Blob([data]))
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 30_000)
}

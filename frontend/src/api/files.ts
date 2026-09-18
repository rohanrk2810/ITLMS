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

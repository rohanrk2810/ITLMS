import { type ChangeEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Download, Trash2, Upload } from 'lucide-react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { deleteFile, downloadFile, type FileResponse, listFiles, uploadFile } from '@/api/files'
import type { Role } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate } from '@/lib/format'
import { hasRole, useAuthStore } from '@/stores/auth-store'

const CATEGORIES_BY_ROLE: Record<Role, string[]> = {
  STUDENT: ['AVATAR', 'DOCUMENT', 'SUBMISSION'],
  TRAINER: ['AVATAR', 'ASSIGNMENT', 'LESSON_RESOURCE'],
  ADMIN: ['AVATAR', 'DOCUMENT', 'ASSIGNMENT', 'LESSON_RESOURCE', 'CERTIFICATE', 'RECEIPT'],
  COORDINATOR: ['AVATAR', 'DOCUMENT', 'ASSIGNMENT', 'LESSON_RESOURCE', 'CERTIFICATE', 'RECEIPT'],
  FINANCE: ['AVATAR', 'RECEIPT'],
  PLACEMENT: ['AVATAR'],
}

const ALL_CATEGORIES = ['AVATAR', 'DOCUMENT', 'ASSIGNMENT', 'SUBMISSION', 'LESSON_RESOURCE', 'CERTIFICATE', 'RECEIPT']

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function FilesPage() {
  const role = useAuthStore((state) => state.user?.role)
  const isStaff = hasRole(role, ['ADMIN', 'COORDINATOR'])
  const categories = role ? CATEGORIES_BY_ROLE[role] : []

  return (
    <div className="flex flex-col gap-8">
      <div>
        <h1 className="text-2xl font-semibold">Files</h1>
        <p className="text-muted-foreground">Upload, download and manage files (Doc S12).</p>
      </div>

      <UploadPanel categories={categories} />

      {isStaff && <DirectoryPanel />}
    </div>
  )
}

function UploadPanel({ categories }: { categories: string[] }) {
  const [category, setCategory] = useState(categories[0] ?? 'AVATAR')
  const [uploading, setUploading] = useState(false)
  const [uploaded, setUploaded] = useState<FileResponse[]>([])

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteFile(id),
    onSuccess: (_data, id) => {
      toast.success('File deleted')
      setUploaded((current) => current.filter((f) => f.id !== id))
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not delete the file.')),
  })

  async function handlePick(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    setUploading(true)
    try {
      const result = await uploadFile(file, category)
      setUploaded((current) => [result, ...current])
      toast.success('Uploaded')
    } catch (error) {
      toast.error(apiErrorMessage(error, 'Could not upload the file.'))
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <h2 className="text-sm font-medium text-muted-foreground">Upload</h2>
      <div className="flex flex-wrap items-end gap-3">
        <div className="flex flex-col gap-2">
          <Label htmlFor="category">Category</Label>
          <select
            id="category"
            value={category}
            onChange={(event) => setCategory(event.target.value)}
            className="h-9 rounded-md border bg-transparent px-3 text-sm"
          >
            {categories.map((c) => (
              <option key={c} value={c}>
                {c.replace('_', ' ')}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-2">
          <Label htmlFor="file">File</Label>
          <input
            id="file"
            type="file"
            disabled={uploading}
            onChange={(event) => void handlePick(event)}
            className="text-sm file:mr-3 file:rounded-md file:border file:bg-transparent file:px-3 file:py-1.5 file:text-sm"
          />
        </div>
        {uploading && (
          <span className="flex items-center gap-1 text-xs text-muted-foreground">
            <Upload className="size-3 animate-pulse" />
            Uploading...
          </span>
        )}
      </div>

      {uploaded.length > 0 && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>File</TableHead>
              <TableHead>Category</TableHead>
              <TableHead>Size</TableHead>
              <TableHead>Uploaded</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {uploaded.map((file) => (
              <TableRow key={file.id}>
                <TableCell className="font-medium">{file.filename}</TableCell>
                <TableCell>{file.category}</TableCell>
                <TableCell>{formatSize(file.sizeBytes)}</TableCell>
                <TableCell>{formatDate(file.uploadedAt)}</TableCell>
                <TableCell className="flex gap-1">
                  <Button size="sm" variant="ghost" onClick={() => void downloadFile(file.id, file.filename)}>
                    <Download className="size-3.5" />
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    disabled={deleteMutation.isPending}
                    onClick={() => deleteMutation.mutate(file.id)}
                  >
                    <Trash2 className="size-3.5" />
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
      {uploaded.length === 0 && (
        <p className="text-xs text-muted-foreground">Files you upload here appear below, this session, with a download link.</p>
      )}
    </div>
  )
}

function DirectoryPanel() {
  const [ownerUserId, setOwnerUserId] = useState('')
  const [category, setCategory] = useState('')

  const query = useQuery({
    queryKey: ['files', 'directory', ownerUserId, category],
    queryFn: () =>
      listFiles({
        ownerUserId: ownerUserId ? Number(ownerUserId) : undefined,
        category: category || undefined,
      }),
  })

  const queryClient = useQueryClient()
  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteFile(id),
    onSuccess: () => {
      toast.success('File deleted')
      void queryClient.invalidateQueries({ queryKey: ['files', 'directory'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not delete the file.')),
  })

  return (
    <div className="flex flex-col gap-3">
      <h2 className="text-sm font-medium text-muted-foreground">Directory (staff)</h2>
      <div className="flex flex-wrap gap-2">
        <Input
          placeholder="Owner user id"
          value={ownerUserId}
          onChange={(event) => setOwnerUserId(event.target.value)}
          className="max-w-40"
        />
        <select
          value={category}
          onChange={(event) => setCategory(event.target.value)}
          className="h-9 rounded-md border bg-transparent px-3 text-sm"
        >
          <option value="">All categories</option>
          {ALL_CATEGORIES.map((c) => (
            <option key={c} value={c}>
              {c.replace('_', ' ')}
            </option>
          ))}
        </select>
      </div>

      {query.isLoading && <Skeleton className="h-48" />}

      {query.data && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>File</TableHead>
              <TableHead>Category</TableHead>
              <TableHead>Owner</TableHead>
              <TableHead>Size</TableHead>
              <TableHead>Uploaded</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {query.data.content.map((file) => (
              <TableRow key={file.id}>
                <TableCell className="font-medium">{file.filename}</TableCell>
                <TableCell>{file.category}</TableCell>
                <TableCell>{file.ownerUserId}</TableCell>
                <TableCell>{formatSize(file.sizeBytes)}</TableCell>
                <TableCell>{formatDate(file.uploadedAt)}</TableCell>
                <TableCell className="flex gap-1">
                  <Button size="sm" variant="ghost" onClick={() => void downloadFile(file.id, file.filename)}>
                    <Download className="size-3.5" />
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    disabled={deleteMutation.isPending}
                    onClick={() => deleteMutation.mutate(file.id)}
                  >
                    <Trash2 className="size-3.5" />
                  </Button>
                </TableCell>
              </TableRow>
            ))}
            {query.data.content.length === 0 && (
              <TableRow>
                <TableCell colSpan={6} className="text-center text-muted-foreground">
                  No files match.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      )}
    </div>
  )
}

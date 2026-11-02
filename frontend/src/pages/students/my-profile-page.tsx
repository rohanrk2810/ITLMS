import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Upload } from 'lucide-react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { getMyProfile, uploadMyPhoto } from '@/api/students'
import { StudentPhoto } from '@/components/student-photo'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate } from '@/lib/format'

import { EditStudentDialog } from './edit-student-dialog'

function Field({ label, value }: { label: string; value?: string | number | null }) {
  return (
    <div>
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="text-sm">{value || '—'}</dd>
    </div>
  )
}

/**
 * A student's own record. The photo for the ID card is added here once; after that it is locked and only the
 * institute office can change it.
 */
export function MyProfilePage() {
  const queryClient = useQueryClient()
  const query = useQuery({ queryKey: ['my-profile'], queryFn: getMyProfile })
  const [version, setVersion] = useState(0)
  const input = useRef<HTMLInputElement>(null)

  const upload = useMutation({
    mutationFn: (file: File) => uploadMyPhoto(file),
    onSuccess: () => {
      toast.success('Photo saved. It will be printed on your ID card.')
      setVersion((v) => v + 1)
      void queryClient.invalidateQueries({ queryKey: ['my-profile'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not save the photo.')),
  })

  if (query.isLoading) return <Skeleton className="h-96 max-w-3xl" />
  const student = query.data
  if (!student) return null

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">My profile</h1>
          <p className="text-muted-foreground">
            {student.fullName} &middot; {student.studentCode}
          </p>
        </div>
        <EditStudentDialog student={student} isAdmin={false} self />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Photo for your ID card</CardTitle>
          <CardDescription>
            {student.hasPhoto
              ? 'Your photo is saved. To change it, ask the institute office.'
              : 'Add a clear passport-style photo (JPEG or PNG, up to 256 KB). You can add it only once; after that only the institute office can change it.'}
          </CardDescription>
        </CardHeader>
        <CardContent className="flex items-center gap-4">
          <StudentPhoto studentId={student.id} hasPhoto={student.hasPhoto} version={version} />
          {!student.hasPhoto && (
            <>
              <input
                ref={input}
                type="file"
                accept="image/jpeg,image/png"
                className="hidden"
                onChange={(event) => {
                  const file = event.target.files?.[0]
                  event.target.value = ''
                  if (file) upload.mutate(file)
                }}
              />
              <Button onClick={() => input.current?.click()} disabled={upload.isPending}>
                <Upload />
                {upload.isPending ? 'Saving...' : 'Upload photo'}
              </Button>
            </>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Details</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid gap-4 sm:grid-cols-2">
            <Field label="Email" value={student.email} />
            <Field label="Phone" value={student.phone} />
            <Field label="Date of birth" value={student.dateOfBirth && formatDate(student.dateOfBirth)} />
            <Field label="Gender" value={student.gender} />
            <Field label="Education" value={student.highestEducation} />
            <Field label="College" value={student.college} />
            <Field label="Graduation year" value={student.graduationYear} />
            <Field
              label="Address"
              value={[student.addressLine, student.city, student.state, student.pincode].filter(Boolean).join(', ')}
            />
            <Field
              label="Guardian"
              value={student.guardianName && `${student.guardianName} (${student.guardianPhone ?? '—'})`}
            />
            <Field label="Emergency contact" value={student.emergencyContact} />
          </dl>
        </CardContent>
      </Card>
    </div>
  )
}

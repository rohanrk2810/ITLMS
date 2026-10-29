import { type FormEvent, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Pencil } from 'lucide-react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { type StudentResponse, updateStudent } from '@/api/students'
import { getUser, updateUser } from '@/api/users'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

interface FormState {
  firstName: string
  lastName: string
  email: string
  phone: string
  dateOfBirth: string
  gender: string
  highestEducation: string
  college: string
  graduationYear: string
  addressLine: string
  city: string
  state: string
  pincode: string
  guardianName: string
  guardianPhone: string
  emergencyContact: string
  remarks: string
}

function blank(value: string) {
  return value.trim() === '' ? undefined : value.trim()
}

/**
 * Staff correct a student's record. The profile fields save for any staff member; name, email and phone live on the
 * login account, which only an administrator may change, so those are offered to administrators alone.
 */
export function EditStudentDialog({ student, isAdmin }: { student: StudentResponse; isAdmin: boolean }) {
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState<FormState | null>(null)
  const queryClient = useQueryClient()

  async function load(next: boolean) {
    setOpen(next)
    if (!next) return
    // First and last name are held separately on the account; the student record only has the full name.
    let firstName = ''
    let lastName = ''
    if (isAdmin) {
      try {
        const user = await getUser(student.userId)
        firstName = user.firstName
        lastName = user.lastName
      } catch (error) {
        toast.error(apiErrorMessage(error, 'Could not load the account details.'))
      }
    }
    setForm({
      firstName,
      lastName,
      email: student.email,
      phone: student.phone,
      dateOfBirth: student.dateOfBirth ?? '',
      gender: student.gender ?? '',
      highestEducation: student.highestEducation ?? '',
      college: student.college ?? '',
      graduationYear: student.graduationYear?.toString() ?? '',
      addressLine: student.addressLine ?? '',
      city: student.city ?? '',
      state: student.state ?? '',
      pincode: student.pincode ?? '',
      guardianName: student.guardianName ?? '',
      guardianPhone: student.guardianPhone ?? '',
      emergencyContact: student.emergencyContact ?? '',
      remarks: student.remarks ?? '',
    })
  }

  const mutation = useMutation({
    mutationFn: async (f: FormState) => {
      if (isAdmin && f.firstName.trim() && f.lastName.trim()) {
        await updateUser(student.userId, {
          firstName: f.firstName.trim(),
          lastName: f.lastName.trim(),
          email: f.email.trim(),
          phone: blank(f.phone),
        })
      }
      return updateStudent(student.id, {
        dateOfBirth: blank(f.dateOfBirth),
        gender: blank(f.gender),
        highestEducation: blank(f.highestEducation),
        college: blank(f.college),
        graduationYear: f.graduationYear.trim() ? Number(f.graduationYear) : undefined,
        addressLine: blank(f.addressLine),
        city: blank(f.city),
        state: blank(f.state),
        pincode: f.pincode.trim(),
        guardianName: blank(f.guardianName),
        guardianPhone: f.guardianPhone.trim(),
        emergencyContact: f.emergencyContact.trim(),
        remarks: blank(f.remarks),
      })
    },
    onSuccess: () => {
      toast.success('Student updated')
      setOpen(false)
      void queryClient.invalidateQueries({ queryKey: ['students'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not update the student.')),
  })

  function field(key: keyof FormState, label: string, props: { type?: string; required?: boolean } = {}) {
    return (
      <div className="flex flex-col gap-1.5">
        <Label htmlFor={`student-${key}`}>{label}</Label>
        <Input
          id={`student-${key}`}
          type={props.type}
          required={props.required}
          value={form![key]}
          onChange={(event) => setForm({ ...form!, [key]: event.target.value })}
        />
      </div>
    )
  }

  return (
    <Dialog open={open} onOpenChange={(next) => void load(next)}>
      <DialogTrigger asChild>
        <Button size="sm" variant="outline">
          <Pencil className="size-3.5" />
          Edit
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Edit {student.fullName}</DialogTitle>
        </DialogHeader>
        {form && (
          <form
            className="flex flex-col gap-4"
            onSubmit={(event: FormEvent) => {
              event.preventDefault()
              mutation.mutate(form)
            }}
          >
            {isAdmin && (
              <div className="grid gap-3 sm:grid-cols-2">
                {field('firstName', 'First name', { required: true })}
                {field('lastName', 'Last name', { required: true })}
                {field('email', 'Email', { type: 'email', required: true })}
                {field('phone', 'Phone')}
              </div>
            )}
            <div className="grid gap-3 sm:grid-cols-2">
              {field('dateOfBirth', 'Date of birth', { type: 'date' })}
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="student-gender">Gender</Label>
                <select
                  id="student-gender"
                  value={form.gender}
                  onChange={(event) => setForm({ ...form, gender: event.target.value })}
                  className="h-9 rounded-md border bg-transparent px-3 text-sm"
                >
                  <option value="">—</option>
                  <option value="MALE">Male</option>
                  <option value="FEMALE">Female</option>
                  <option value="OTHER">Other</option>
                </select>
              </div>
              {field('highestEducation', 'Education')}
              {field('college', 'College')}
              {field('graduationYear', 'Graduation year', { type: 'number' })}
              {field('addressLine', 'Address')}
              {field('city', 'City')}
              {field('state', 'State')}
              {field('pincode', 'PIN code')}
              {field('guardianName', 'Guardian name')}
              {field('guardianPhone', 'Guardian phone')}
              {field('emergencyContact', 'Emergency contact')}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="student-remarks">Remarks</Label>
              <textarea
                id="student-remarks"
                rows={3}
                className="rounded-md border bg-transparent px-3 py-2 text-sm"
                value={form.remarks}
                onChange={(event) => setForm({ ...form, remarks: event.target.value })}
              />
            </div>
            <DialogFooter>
              <Button type="submit" disabled={mutation.isPending}>
                {mutation.isPending ? 'Saving...' : 'Save changes'}
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  )
}

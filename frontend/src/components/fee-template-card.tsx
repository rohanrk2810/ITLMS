import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { searchCourses } from '@/api/courses'
import { getFeeTemplate, type FeeTemplateCharge, saveFeeTemplate } from '@/api/finance'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/**
 * The usual fee structure of a course: how many installments, when the first falls due, and the extra heads (a
 * development fee, a book) every student is charged. Used for plans raised from now on.
 */
export function FeeTemplateCard() {
  const queryClient = useQueryClient()
  const courses = useQuery({ queryKey: ['courses', 'for-fee-template'], queryFn: () => searchCourses({}) })
  const [courseId, setCourseId] = useState<number | null>(null)
  const [count, setCount] = useState('1')
  const [firstDueDays, setFirstDueDays] = useState('7')
  const [charges, setCharges] = useState<FeeTemplateCharge[]>([])

  const template = useQuery({
    queryKey: ['finance', 'template', courseId],
    queryFn: () => getFeeTemplate(courseId as number),
    enabled: courseId != null,
  })
  useEffect(() => {
    if (!template.data) return
    setCount(String(template.data.installmentCount))
    setFirstDueDays(String(template.data.firstDueDays))
    setCharges(template.data.charges)
  }, [template.data])

  const save = useMutation({
    mutationFn: () =>
      saveFeeTemplate(courseId as number, {
        installmentCount: Number(count),
        firstDueDays: Number(firstDueDays),
        charges: charges.filter((c) => c.label.trim() && Number(c.amount) > 0),
      }),
    onSuccess: () => {
      toast.success('Fee structure saved. New plans for this course will follow it.')
      void queryClient.invalidateQueries({ queryKey: ['finance', 'template', courseId] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not save the fee structure.')),
  })

  function setCharge(index: number, next: Partial<FeeTemplateCharge>) {
    setCharges((list) => list.map((c, i) => (i === index ? { ...c, ...next } : c)))
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Fee structure of a course</CardTitle>
        <CardDescription>
          When a student is placed in a batch, their fee plan follows this: the installments, the first due date and the
          extra heads. Plans already raised are not changed.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <select
          aria-label="Course"
          value={courseId ?? ''}
          onChange={(event) => setCourseId(event.target.value ? Number(event.target.value) : null)}
          className="h-9 max-w-md rounded-md border bg-transparent px-3 text-sm"
        >
          <option value="">Choose a course</option>
          {courses.data?.content.map((c) => (
            <option key={c.id} value={c.id}>
              {c.title}
            </option>
          ))}
        </select>

        {courseId != null && template.data && (
          <>
            <div className="flex flex-wrap gap-4">
              <div className="flex flex-col gap-1">
                <Label htmlFor="tpl-count">Installments</Label>
                <Input id="tpl-count" type="number" min={1} max={36} className="w-28" value={count}
                  onChange={(e) => setCount(e.target.value)} />
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="tpl-days">First due, days after placement</Label>
                <Input id="tpl-days" type="number" min={0} max={365} className="w-28" value={firstDueDays}
                  onChange={(e) => setFirstDueDays(e.target.value)} />
              </div>
            </div>

            <div className="flex flex-col gap-2">
              <Label>Extra heads</Label>
              {charges.map((c, i) => (
                <div key={i} className="flex flex-wrap items-center gap-2">
                  <Input aria-label="Name" placeholder="Development fee" maxLength={120} className="w-56" value={c.label}
                    onChange={(e) => setCharge(i, { label: e.target.value })} />
                  <Input aria-label="Amount" type="number" min={1} placeholder="Amount" className="w-28"
                    value={c.amount || ''} onChange={(e) => setCharge(i, { amount: Number(e.target.value) })} />
                  <Input aria-label="Due in days" type="number" min={0} max={365} className="w-24" value={c.dueDays}
                    onChange={(e) => setCharge(i, { dueDays: Number(e.target.value) })} />
                  <span className="text-xs text-muted-foreground">days after placement</span>
                  <Button type="button" variant="ghost" size="icon" aria-label="Remove"
                    onClick={() => setCharges((list) => list.filter((_, j) => j !== i))}>
                    <Trash2 className="size-4" />
                  </Button>
                </div>
              ))}
              <div>
                <Button type="button" variant="outline" size="sm"
                  onClick={() => setCharges((list) => [...list, { label: '', amount: 0, dueDays: 7 }])}>
                  <Plus className="size-4" /> Add a head
                </Button>
              </div>
            </div>

            <div>
              <Button onClick={() => save.mutate()} disabled={save.isPending}>
                {save.isPending ? 'Saving...' : 'Save fee structure'}
              </Button>
            </div>
          </>
        )}
      </CardContent>
    </Card>
  )
}

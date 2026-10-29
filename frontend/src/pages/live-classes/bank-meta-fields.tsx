import { useEffect, useState } from 'react'

import { type CourseSummaryResponse, searchCourses } from '@/api/courses'
import type { Difficulty, MaterialMeta } from '@/api/live-classes'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'

/** The text form of {@link MaterialMeta}: tags are typed as "loops, python". */
export interface MetaDraft {
  courseId: number | null
  topic: string
  tags: string
  difficulty: Difficulty | ''
  shared: boolean
}

export const EMPTY_META: MetaDraft = { courseId: null, topic: '', tags: '', difficulty: '', shared: false }

export function metaToPayload(draft: MetaDraft): MaterialMeta {
  return {
    courseId: draft.courseId,
    topic: draft.topic.trim() || undefined,
    tags: draft.tags.split(',').map((t) => t.trim()).filter(Boolean),
    difficulty: draft.difficulty,
    shared: draft.shared,
  }
}

/** Course, topic, tags, difficulty and sharing for a question in the bank. */
export function BankMetaFields({ value, onChange }: { value: MetaDraft; onChange: (next: MetaDraft) => void }) {
  const [courses, setCourses] = useState<CourseSummaryResponse[]>([])
  useEffect(() => {
    // A course is only a label here; if the list cannot be read the question can still be saved without one.
    searchCourses({}).then((page) => setCourses(page.content)).catch(() => undefined)
  }, [])

  return (
    <div className="flex flex-col gap-2 rounded-md border p-2">
      <p className="text-xs font-medium text-muted-foreground">Find it again later</p>
      <div className="grid gap-2 sm:grid-cols-2">
        <select
          value={value.courseId ?? ''}
          onChange={(e) => onChange({ ...value, courseId: e.target.value ? Number(e.target.value) : null })}
          className="h-9 rounded-md border bg-transparent px-2 text-sm"
        >
          <option value="">No course</option>
          {courses.map((c) => (
            <option key={c.id} value={c.id}>{c.title}</option>
          ))}
        </select>
        <select
          value={value.difficulty}
          onChange={(e) => onChange({ ...value, difficulty: e.target.value as Difficulty | '' })}
          className="h-9 rounded-md border bg-transparent px-2 text-sm"
        >
          <option value="">Difficulty: not set</option>
          <option value="EASY">Easy</option>
          <option value="MEDIUM">Medium</option>
          <option value="HARD">Hard</option>
        </select>
        <Input value={value.topic} onChange={(e) => onChange({ ...value, topic: e.target.value })} placeholder="Topic, e.g. SQL joins" maxLength={120} />
        <Input value={value.tags} onChange={(e) => onChange({ ...value, tags: e.target.value })} placeholder="Tags, comma separated" />
      </div>
      <label className="flex items-start gap-2 text-sm">
        <Checkbox checked={value.shared} onCheckedChange={(v) => onChange({ ...value, shared: v === true })} />
        <span>
          Share with other trainers
          <span className="block text-xs text-muted-foreground">They can send it to their class or copy it. Only you can change it.</span>
        </span>
      </label>
    </div>
  )
}

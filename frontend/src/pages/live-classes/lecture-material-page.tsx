import { useState } from 'react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { type AskQuestionInput, type LiveMaterial, saveMaterial } from '@/api/live-classes'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { BankMetaFields, EMPTY_META, type MetaDraft, metaToPayload } from './bank-meta-fields'
import { MaterialList } from './material-list'
import { QuestionForm } from './question-form'

/** The trainer's question bank: write, tag and share questions, code and small databases to send from a class. */
export function LectureMaterialPage() {
  const [editing, setEditing] = useState<LiveMaterial | null>(null)
  const [title, setTitle] = useState('')
  const [meta, setMeta] = useState<MetaDraft>(EMPTY_META)
  const [savedKey, setSavedKey] = useState(0)

  function startEdit(item: LiveMaterial) {
    setEditing(item)
    setTitle(item.title)
    setMeta({
      courseId: item.courseId,
      topic: item.topic ?? '',
      tags: item.tags.join(', '),
      difficulty: item.difficulty ?? '',
      shared: item.shared,
    })
  }

  function reset() {
    setEditing(null)
    setTitle('')
    setMeta(EMPTY_META)
  }

  async function save(input: AskQuestionInput) {
    const name = title.trim() || input.prompt.slice(0, 60)
    try {
      await saveMaterial(name, input, metaToPayload(meta), editing?.id)
      toast.success(editing ? 'Updated' : 'Saved')
      reset()
      setSavedKey((k) => k + 1)
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not save it.'))
      throw err
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Question bank</h1>
        <p className="text-muted-foreground">
          Prepare questions, code and database tables now. In the live class, open Questions &rarr; Question bank and press Send.
          In a test, use Import from bank.
        </p>
      </div>
      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader className="flex-row items-center justify-between space-y-0">
            <CardTitle className="text-base">{editing ? `Editing: ${editing.title}` : 'New question'}</CardTitle>
            {editing && <Button size="sm" variant="ghost" onClick={reset}>Cancel</Button>}
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Name (e.g. SQL joins - quiz 1)" maxLength={160} />
            <BankMetaFields value={meta} onChange={setMeta} />
            {/* Keyed by the item so editing a different one starts from its own text, not the last form's. */}
            <QuestionForm
              key={editing?.id ?? 'new'}
              initial={editing?.question}
              submitLabel={editing ? 'Save changes' : 'Save to bank'}
              onSubmit={save}
            />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Bank</CardTitle>
          </CardHeader>
          <CardContent>
            <MaterialList refreshKey={savedKey} onEdit={startEdit} />
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

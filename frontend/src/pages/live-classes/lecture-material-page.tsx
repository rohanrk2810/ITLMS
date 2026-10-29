import { useRef, useState } from 'react'
import { Download, FileSpreadsheet, Loader2 } from 'lucide-react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import {
  type AskQuestionInput,
  type LiveMaterial,
  type MaterialImportResult,
  downloadMaterialTemplate,
  importMaterials,
  saveMaterial,
} from '@/api/live-classes'
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
  const [importing, setImporting] = useState(false)
  const [report, setReport] = useState<MaterialImportResult | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)

  async function upload(file: File) {
    setImporting(true)
    setReport(null)
    try {
      const result = await importMaterials(file)
      setReport(result)
      setSavedKey((k) => k + 1)
      if (result.imported > 0) toast.success(`${result.imported} question${result.imported === 1 ? '' : 's'} added to your bank`)
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not read that file.'))
    } finally {
      setImporting(false)
      if (fileInput.current) fileInput.current.value = ''
    }
  }

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
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <Button size="sm" variant="outline" onClick={() => void downloadMaterialTemplate().catch((err) => toast.error(apiErrorMessage(err, 'Could not download the template.')))}>
            <Download className="size-4" /> Excel template
          </Button>
          <Button size="sm" disabled={importing} onClick={() => fileInput.current?.click()}>
            {importing ? <Loader2 className="size-4 animate-spin" /> : <FileSpreadsheet className="size-4" />} Upload filled Excel
          </Button>
          <input
            ref={fileInput}
            type="file"
            accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            className="hidden"
            onChange={(e) => e.target.files?.[0] && void upload(e.target.files[0])}
          />
        </div>
        {report && (
          <div className="mt-3 rounded-md border p-3 text-sm">
            <p className="font-medium">
              {report.imported} question{report.imported === 1 ? '' : 's'} added
              {report.errors.length > 0 && `, ${report.errors.length} row${report.errors.length === 1 ? '' : 's'} skipped`}
            </p>
            {report.errors.length > 0 && (
              <ul className="mt-1 list-disc pl-5 text-destructive">
                {report.errors.map((e) => (
                  <li key={e.row}>Row {e.row}: {e.message}</li>
                ))}
              </ul>
            )}
          </div>
        )}
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

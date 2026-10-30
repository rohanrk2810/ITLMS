import { useRef, useState } from 'react'
import { Bot, Copy, Download, FileSpreadsheet, Loader2 } from 'lucide-react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import {
  type AskQuestionInput,
  type LiveMaterial,
  type MaterialImportResult,
  downloadMaterialTemplate,
  fetchAiPrompt,
  importMaterials,
  saveMaterial,
} from '@/api/live-classes'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { BankMetaFields, EMPTY_META, type MetaDraft, metaToPayload } from './bank-meta-fields'
import { FolderTree } from './folder-tree'
import { MaterialList } from './material-list'
import { QuestionForm } from './question-form'

/** The trainer's question bank: write, tag and share questions, code and small databases to send from a class. */
export function LectureMaterialPage() {
  const [editing, setEditing] = useState<LiveMaterial | null>(null)
  const [title, setTitle] = useState('')
  const [meta, setMeta] = useState<MetaDraft>(EMPTY_META)
  const [folder, setFolder] = useState('')

  /** Browsing a folder also makes it the place the next new question is filed. */
  function pickFolder(path: string) {
    setFolder(path)
    if (!editing) setMeta((m) => ({ ...m, folder: path }))
  }
  const [savedKey, setSavedKey] = useState(0)
  const [importing, setImporting] = useState(false)
  const [report, setReport] = useState<MaterialImportResult | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)
  const [pasted, setPasted] = useState('')

  /** Puts the instructions for ChatGPT on the clipboard; if the browser refuses, saves them as a text file instead. */
  async function copyPrompt() {
    try {
      const prompt = await fetchAiPrompt()
      try {
        await navigator.clipboard.writeText(prompt)
        toast.success('Copied. Paste it into ChatGPT, then write what you want where it says WHAT I WANT.')
      } catch {
        const url = URL.createObjectURL(new Blob([prompt], { type: 'text/plain' }))
        const link = document.createElement('a')
        link.href = url
        link.download = 'chatgpt-prompt.txt'
        link.click()
        URL.revokeObjectURL(url)
        toast.success('Saved as chatgpt-prompt.txt. Open it, copy everything and paste it into ChatGPT.')
      }
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not get the text.'))
    }
  }

  /** Resolves true when at least one question was added, so a pasted box is only cleared when it worked. */
  async function upload(files: File[]): Promise<boolean> {
    setImporting(true)
    setReport(null)
    try {
      const result = await importMaterials(files)
      setReport(result)
      setSavedKey((k) => k + 1)
      if (result.imported > 0) toast.success(`${result.imported} question${result.imported === 1 ? '' : 's'} added to your bank`)
      return result.imported > 0
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not read that file.'))
      return false
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
      folder: item.folder ?? '',
    })
  }

  function reset() {
    setEditing(null)
    setTitle('')
    setMeta({ ...EMPTY_META, folder })
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
        <div className="mt-3 flex flex-col gap-3 rounded-md border bg-muted/30 p-3">
          <p className="flex items-center gap-2 text-sm font-medium">
            <Bot className="size-4" /> Make questions with ChatGPT - one file with everything
          </p>
          <ol className="list-decimal pl-5 text-sm text-muted-foreground">
            <li>Press <b>Copy ChatGPT prompt</b> and paste it into ChatGPT.</li>
            <li>Write what you want where it says WHAT I WANT (topic, how many, which kinds, folder).</li>
            <li>Copy ChatGPT&apos;s reply and paste it in the box below (or save it as a .json file and upload it).</li>
          </ol>
          <div className="flex flex-wrap gap-2">
            <Button size="sm" onClick={() => void copyPrompt()}>
              <Copy className="size-4" /> Copy ChatGPT prompt
            </Button>
            <Button size="sm" variant="outline" onClick={() => void downloadMaterialTemplate('json').catch((err) => toast.error(apiErrorMessage(err, 'Could not download the example.')))}>
              <Download className="size-4" /> Example file (.json)
            </Button>
          </div>
          <textarea
            value={pasted}
            onChange={(e) => setPasted(e.target.value)}
            rows={4}
            spellCheck={false}
            placeholder="Paste ChatGPT's reply here"
            className="rounded-md border bg-background px-3 py-2 font-mono text-xs"
          />
          <Button
            size="sm"
            className="self-start"
            disabled={importing || pasted.trim() === ''}
            onClick={() => {
              void upload([new File([pasted], 'pasted.json', { type: 'application/json' })]).then((ok) => ok && setPasted(''))
            }}
          >
            {importing ? <Loader2 className="size-4 animate-spin" /> : null} Add these questions to my bank
          </Button>
        </div>
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <Button size="sm" variant="outline" onClick={() => void downloadMaterialTemplate().catch((err) => toast.error(apiErrorMessage(err, 'Could not download the template.')))}>
            <Download className="size-4" /> Excel template
          </Button>
          <Button size="sm" variant="outline" onClick={() => void downloadMaterialTemplate('csv').catch((err) => toast.error(apiErrorMessage(err, 'Could not download the templates.')))}>
            <Download className="size-4" /> CSV templates
          </Button>
          <Button size="sm" disabled={importing} onClick={() => fileInput.current?.click()}>
            {importing ? <Loader2 className="size-4 animate-spin" /> : <FileSpreadsheet className="size-4" />} Upload filled file
          </Button>
          <input
            ref={fileInput}
            type="file"
            multiple
            accept=".xlsx,.csv,.json,text/csv,application/json,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            className="hidden"
            onChange={(e) => e.target.files?.length && void upload(Array.from(e.target.files))}
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
                {report.errors.map((e, i) => (
                  <li key={i}>{e.row > 0 ? `Row ${e.row}: ` : ''}{e.message}</li>
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
            <div className="flex flex-col gap-4 sm:flex-row">
              <FolderTree selected={folder} onSelect={pickFolder} refreshKey={savedKey} onChanged={() => setSavedKey((k) => k + 1)} />
              <div className="min-w-0 flex-1">
                <MaterialList refreshKey={savedKey} onEdit={startEdit} folder={folder} />
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

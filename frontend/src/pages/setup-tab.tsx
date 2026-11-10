import { useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { AlertTriangleIcon, CheckCircle2Icon, CopyIcon, DownloadIcon, FileTextIcon, FileUpIcon, Loader2Icon } from 'lucide-react'
import { toast } from 'sonner'

import { getBranding, getSiteConfig, updateBranding, updateSiteConfig } from '@/api/branding'
import { apiErrorMessage } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { type Checked, EXAMPLE_JSON, buildPrompt, checkSetup, mergeInstitute, mergeSite } from '@/lib/institute-setup'
import { parseSite } from '@/lib/site-config'

function saveText(name: string, text: string, type = 'text/plain') {
  const url = URL.createObjectURL(new Blob([text], { type }))
  const link = document.createElement('a')
  link.href = url
  link.download = name
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}

/**
 * Quick setup: the institute and the whole website from one .json file, the same way the question bank takes its
 * questions. The institute fills in the PDF data sheet (or gives the facts to ChatGPT with the prompt file), and the
 * file that comes back is checked here, shown as a list of changes, and applied only when the administrator agrees.
 */
export function SetupTab() {
  const queryClient = useQueryClient()
  const fileInput = useRef<HTMLInputElement>(null)
  const [pasted, setPasted] = useState('')
  const [checked, setChecked] = useState<Checked | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [applying, setApplying] = useState(false)

  async function copyPrompt() {
    const prompt = buildPrompt()
    try {
      await navigator.clipboard.writeText(prompt)
      toast.success('Copied. Paste it into ChatGPT and add the details of your institute below it.')
    } catch {
      saveText('institute-setup-prompt.txt', prompt)
      toast.success('Saved as institute-setup-prompt.txt. Open it, copy everything and paste it into ChatGPT.')
    }
  }

  function check(text: string) {
    setChecked(checkSetup(text))
  }

  async function onFile(file: File | undefined) {
    if (!file) return
    const text = await file.text()
    setPasted(text)
    check(text)
  }

  async function apply() {
    if (!checked?.setup) return
    setApplying(true)
    try {
      const { setup } = checked
      if (Object.keys(setup.institute).length > 0) {
        const current = await getBranding()
        await updateBranding(
          mergeInstitute(
            {
              name: current.name,
              tagline: current.tagline,
              primaryColor: current.primaryColor,
              contactEmail: current.contactEmail,
              contactPhone: current.contactPhone,
              website: current.website,
              address: current.address,
              signatoryName: current.signatoryName,
              signatoryTitle: current.signatoryTitle,
            },
            setup.institute,
          ),
        )
      }
      if (setup.provided.length > 0) {
        const stored = await getSiteConfig()
        await updateSiteConfig(JSON.stringify(mergeSite(parseSite(stored.config), setup)))
      }
      await queryClient.invalidateQueries()
      toast.success('Done. The institute and the website now show the new details.')
      setConfirming(false)
      setChecked(null)
      setPasted('')
    } catch (error) {
      toast.error(apiErrorMessage(error, 'Could not apply the file. Nothing more was changed.'))
    } finally {
      setApplying(false)
    }
  }

  const errors = checked?.problems.filter((p) => p.level === 'error') ?? []
  const warnings = checked?.problems.filter((p) => p.level === 'warning') ?? []

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader>
          <CardTitle>Set up from one file</CardTitle>
          <CardDescription>
            Give your institute&apos;s details once and the whole website and every printed page follows: name, address, contact, the
            front page, questions and answers, trainers and more. Pictures are added on the Institute and Website tabs.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4 text-sm">
          <ol className="list-decimal space-y-1 pl-5">
            <li>
              Press <b>Download data sheet (PDF)</b>. It lists every detail the institute has to give, with an example for each.
            </li>
            <li>
              Press <b>Download prompt file</b> and attach it in ChatGPT (or press <b>Copy ChatGPT prompt</b> and paste it). Under it, write
              or paste the institute&apos;s details.
            </li>
            <li>
              Ask ChatGPT for the answer as a .json file and press <b>Upload .json file</b>, or paste its reply in the box below.
            </li>
            <li>Check the list of changes, then press <b>Apply</b>. Whatever the file leaves out stays as it is.</li>
          </ol>
          <div className="flex flex-wrap gap-2">
            <Button asChild size="sm">
              <a href="/institute-data-sheet.pdf" download>
                <FileTextIcon className="size-4" /> Download data sheet (PDF)
              </a>
            </Button>
            <Button size="sm" variant="outline" onClick={() => saveText('institute-setup-prompt.txt', buildPrompt())}>
              <DownloadIcon className="size-4" /> Download prompt file
            </Button>
            <Button size="sm" variant="outline" onClick={() => void copyPrompt()}>
              <CopyIcon className="size-4" /> Copy ChatGPT prompt
            </Button>
            <Button size="sm" variant="outline" onClick={() => fileInput.current?.click()}>
              <FileUpIcon className="size-4" /> Upload .json file
            </Button>
            <input
              ref={fileInput}
              type="file"
              accept=".json,application/json"
              className="hidden"
              onChange={(event) => {
                void onFile(event.target.files?.[0])
                event.target.value = ''
              }}
            />
            <Button size="sm" variant="outline" onClick={() => saveText('institute-setup-example.json', EXAMPLE_JSON, 'application/json')}>
              <DownloadIcon className="size-4" /> Example file (.json)
            </Button>
          </div>
          <div className="flex flex-col gap-2">
            <label htmlFor="setup-paste" className="font-medium">
              Or paste the reply here
            </label>
            <textarea
              id="setup-paste"
              rows={6}
              value={pasted}
              onChange={(event) => {
                setPasted(event.target.value)
                setChecked(null)
              }}
              placeholder='{ "institute": { "name": "..." }, "website": { ... } }'
              className="border-input rounded-md border bg-transparent px-3 py-2 font-mono text-xs shadow-xs outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
            />
            <div>
              <Button size="sm" variant="outline" disabled={!pasted.trim()} onClick={() => check(pasted)}>
                Check the file
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      {checked && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">What will change</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-4 text-sm">
            {checked.summary.length > 0 && (
              <ul className="flex flex-col gap-1.5">
                {checked.summary.map((line) => (
                  <li key={line} className="flex gap-2">
                    <CheckCircle2Icon className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden="true" />
                    {line}
                  </li>
                ))}
              </ul>
            )}
            {errors.length > 0 && (
              <ul className="flex flex-col gap-1.5 rounded-lg border border-destructive/40 bg-destructive/5 p-3 text-destructive">
                {errors.map((p) => (
                  <li key={p.text}>{p.text}</li>
                ))}
              </ul>
            )}
            {warnings.length > 0 && (
              <ul className="flex flex-col gap-1.5 rounded-lg border p-3 text-muted-foreground">
                {warnings.map((p) => (
                  <li key={p.text} className="flex gap-2">
                    <AlertTriangleIcon className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
                    {p.text}
                  </li>
                ))}
              </ul>
            )}
            <div>
              <Button disabled={!checked.setup} onClick={() => setConfirming(true)}>
                Apply to my institute and website
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      <Dialog open={confirming} onOpenChange={(open) => !applying && setConfirming(open)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Apply these changes?</DialogTitle>
            <DialogDescription>
              The details below replace the ones now on the website and in the institute. Pictures already added stay. You can change
              anything again afterwards on the Institute and Website tabs.
            </DialogDescription>
          </DialogHeader>
          <ul className="list-disc space-y-1 pl-5 text-sm">
            {checked?.summary.map((line) => (
              <li key={line}>{line}</li>
            ))}
          </ul>
          <DialogFooter>
            <Button variant="outline" disabled={applying} onClick={() => setConfirming(false)}>
              Cancel
            </Button>
            <Button disabled={applying} onClick={() => void apply()}>
              {applying ? <Loader2Icon className="size-4 animate-spin" /> : null} Apply
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

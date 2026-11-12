import { useState } from 'react'
import { FileTextIcon } from 'lucide-react'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { downloadStatementPdf } from '@/api/finance'
import { Button } from '@/components/ui/button'

/** Saves a student's statement of fees (every course and payment, and the balance) as a PDF. */
export function DownloadStatementButton({ studentId }: { studentId: number }) {
  const [busy, setBusy] = useState(false)

  async function download() {
    setBusy(true)
    try {
      await downloadStatementPdf(studentId)
    } catch (error) {
      toast.error(apiErrorMessage(error, 'The statement could not be downloaded. Please try again.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Button type="button" variant="outline" onClick={() => void download()} disabled={busy}>
      <FileTextIcon className="size-4" />
      {busy ? 'Preparing...' : 'Fee statement'}
    </Button>
  )
}

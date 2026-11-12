import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { getGstSettings, saveGstSettings } from '@/api/finance'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { formatDate } from '@/lib/format'
import { hasRole, useAuthStore } from '@/stores/auth-store'

const today = () => new Date().toISOString().slice(0, 10)

/** The GST rate, the institute's GSTIN and SAC. Only the administrator changes them; the rate applies from its start date. */
export function GstSettingsCard() {
  const queryClient = useQueryClient()
  const isAdmin = hasRole(useAuthStore((state) => state.roles), ['ADMIN'])
  const settings = useQuery({ queryKey: ['finance', 'gst-settings'], queryFn: getGstSettings })
  const [percent, setPercent] = useState('0')
  const [gstin, setGstin] = useState('')
  const [sac, setSac] = useState('')
  const [from, setFrom] = useState(today())

  useEffect(() => {
    if (!settings.data) return
    setPercent(String(settings.data.percent))
    setGstin(settings.data.gstin ?? '')
    setSac(settings.data.sacCode ?? '')
    if (settings.data.effectiveFrom) setFrom(settings.data.effectiveFrom)
  }, [settings.data])

  const save = useMutation({
    mutationFn: () => saveGstSettings({ percent: Number(percent) || 0, gstin: gstin.trim(), sacCode: sac.trim(), effectiveFrom: from || undefined }),
    onSuccess: () => {
      toast.success('GST settings saved')
      void queryClient.invalidateQueries({ queryKey: ['finance'] })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not save the GST settings.')),
  })

  const s = settings.data
  const on = (s?.percent ?? 0) > 0
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">GST</CardTitle>
        <CardDescription>
          {on
            ? `GST is ${s?.percent}% on fees${s?.effectiveFrom ? ` from ${formatDate(s.effectiveFrom)}` : ''}. Fees are taken as tax-inclusive and split into CGST and SGST on the receipt.`
            : 'GST is off. Fees are shown without tax.'}{' '}
          Ask your CA whether GST applies to your fees and at what rate (IT training is usually 18%).
        </CardDescription>
      </CardHeader>
      {isAdmin && (
        <CardContent className="flex flex-wrap items-end gap-3">
          <div className="flex flex-col gap-1">
            <Label htmlFor="gst-percent">GST rate (%)</Label>
            <Input id="gst-percent" type="number" min={0} max={28} step="0.01" className="w-28" value={percent} onChange={(e) => setPercent(e.target.value)} />
          </div>
          <div className="flex flex-col gap-1">
            <Label htmlFor="gst-gstin">GSTIN</Label>
            <Input id="gst-gstin" maxLength={15} placeholder="27ABCDE1234F1Z5" className="w-48 uppercase" value={gstin} onChange={(e) => setGstin(e.target.value)} />
          </div>
          <div className="flex flex-col gap-1">
            <Label htmlFor="gst-sac">SAC code</Label>
            <Input id="gst-sac" maxLength={8} placeholder="999293" className="w-28" value={sac} onChange={(e) => setSac(e.target.value)} />
          </div>
          <div className="flex flex-col gap-1">
            <Label htmlFor="gst-from">Applies from</Label>
            <Input id="gst-from" type="date" className="w-40" value={from} onChange={(e) => setFrom(e.target.value)} />
          </div>
          <Button disabled={save.isPending} onClick={() => save.mutate()}>Save GST settings</Button>
          <p className="basis-full text-xs text-muted-foreground">
            Receipts, payments and books dated before the start date keep showing no GST. A rate of 0 switches GST off.
          </p>
        </CardContent>
      )}
    </Card>
  )
}

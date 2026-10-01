import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

import { searchBatches } from '@/api/batches'
import { apiErrorMessage } from '@/api/client'
import { searchCourses } from '@/api/courses'
import {
  DEFAULT_WARNING,
  type MonitoringScope,
  type MonitoringSetting,
  type MonitoringSettingInput,
  listMonitoringSettings,
  removeMonitoringSetting,
  saveMonitoringSetting,
} from '@/api/monitoring'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

const SETTINGS_KEY = ['monitoring-settings']

const BLANK: MonitoringSettingInput = {
  scopeType: 'INSTITUTE',
  enabled: true,
  faceVisibility: true,
  cameraRequired: false,
  microphoneRequired: false,
  warningAfterSeconds: 10,
  showWarning: true,
  warningMessage: '',
  logEvents: true,
}

/** ADMIN: switch live-class camera monitoring on or off, for the whole institute or for one course, batch or class. */
export function MonitoringSettingsPage() {
  const queryClient = useQueryClient()
  const settings = useQuery({ queryKey: SETTINGS_KEY, queryFn: listMonitoringSettings })
  const courses = useQuery({ queryKey: ['monitoring', 'courses'], queryFn: () => searchCourses({}) })
  const batches = useQuery({ queryKey: ['monitoring', 'batches'], queryFn: () => searchBatches({}) })

  const [form, setForm] = useState<MonitoringSettingInput>({ ...BLANK, scopeType: 'COURSE' })

  const institute = settings.data?.find((s) => s.scopeType === 'INSTITUTE')

  const save = useMutation({
    mutationFn: (input: MonitoringSettingInput) => saveMonitoringSetting(input),
    onSuccess: (saved) => {
      toast.success(`Monitoring is ${saved.enabled ? 'ON' : 'OFF'} for this level.`)
      void queryClient.invalidateQueries({ queryKey: SETTINGS_KEY })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not save the setting.')),
  })

  const remove = useMutation({
    mutationFn: (id: number) => removeMonitoringSetting(id),
    onSuccess: () => {
      toast.success('Setting removed. That level now follows the one above it.')
      void queryClient.invalidateQueries({ queryKey: SETTINGS_KEY })
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Could not remove the setting.')),
  })

  function describe(s: MonitoringSetting): string {
    if (s.scopeType === 'INSTITUTE') return 'Whole institute'
    if (s.scopeType === 'COURSE') {
      return `Course: ${courses.data?.content.find((c) => c.id === s.scopeId)?.title ?? `#${s.scopeId}`}`
    }
    if (s.scopeType === 'BATCH') {
      return `Batch: ${batches.data?.content.find((b) => b.id === s.scopeId)?.batchCode ?? `#${s.scopeId}`}`
    }
    return `Class session #${s.scopeId}`
  }

  function set<K extends keyof MonitoringSettingInput>(key: K, value: MonitoringSettingInput[K]) {
    setForm((current) => ({ ...current, [key]: value }))
  }

  return (
    <div className="flex max-w-4xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Live class monitoring</h1>
        <p className="text-muted-foreground">
          Camera-based check that students keep their face visible. It is off unless you turn it on. The most
          specific setting wins: class, then batch, then course, then the whole institute.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Whole institute</CardTitle>
          <CardDescription>Applies to every live class that has no more specific setting.</CardDescription>
        </CardHeader>
        <CardContent className="flex items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <Badge variant={institute?.enabled ? 'default' : 'secondary'}>{institute?.enabled ? 'ON' : 'OFF'}</Badge>
            <span className="text-sm text-muted-foreground">
              {institute ? 'Set by an administrator.' : 'No setting yet, so monitoring is off.'}
            </span>
          </div>
          <Button
            variant="outline"
            disabled={save.isPending}
            onClick={() =>
              save.mutate({
                ...BLANK,
                ...(institute ?? {}),
                scopeType: 'INSTITUTE',
                warningMessage: institute?.warningMessage ?? '',
                enabled: !institute?.enabled,
              })
            }
          >
            Turn {institute?.enabled ? 'OFF' : 'ON'}
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Add or change a setting</CardTitle>
          <CardDescription>Saving replaces any setting already at that level.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <div className="flex flex-col gap-2">
            <Label htmlFor="mon-scope">Applies to</Label>
            <select
              id="mon-scope"
              className="h-9 rounded-md border bg-transparent px-2 text-sm"
              value={form.scopeType}
              onChange={(e) => setForm({ ...form, scopeType: e.target.value as MonitoringScope, scopeId: undefined })}
            >
              <option value="INSTITUTE">Whole institute</option>
              <option value="COURSE">A course</option>
              <option value="BATCH">A batch</option>
              <option value="SESSION">One class session</option>
            </select>
          </div>

          {form.scopeType === 'COURSE' && (
            <div className="flex flex-col gap-2">
              <Label htmlFor="mon-course">Course</Label>
              <select
                id="mon-course"
                className="h-9 rounded-md border bg-transparent px-2 text-sm"
                value={form.scopeId ?? ''}
                onChange={(e) => set('scopeId', e.target.value ? Number(e.target.value) : undefined)}
              >
                <option value="">Choose...</option>
                {courses.data?.content.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.title}
                  </option>
                ))}
              </select>
            </div>
          )}
          {form.scopeType === 'BATCH' && (
            <div className="flex flex-col gap-2">
              <Label htmlFor="mon-batch">Batch</Label>
              <select
                id="mon-batch"
                className="h-9 rounded-md border bg-transparent px-2 text-sm"
                value={form.scopeId ?? ''}
                onChange={(e) => set('scopeId', e.target.value ? Number(e.target.value) : undefined)}
              >
                <option value="">Choose...</option>
                {batches.data?.content.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.batchCode} - {b.courseTitle}
                  </option>
                ))}
              </select>
            </div>
          )}
          {form.scopeType === 'SESSION' && (
            <div className="flex flex-col gap-2">
              <Label htmlFor="mon-session">Timetable session id</Label>
              <Input
                id="mon-session"
                type="number"
                min={1}
                value={form.scopeId ?? ''}
                onChange={(e) => set('scopeId', e.target.value ? Number(e.target.value) : undefined)}
              />
            </div>
          )}

          <label className="flex items-center gap-2 text-sm">
            <Checkbox checked={form.enabled} onCheckedChange={(v) => set('enabled', v === true)} />
            Monitoring ON for this level
          </label>
          <label className="flex items-center gap-2 text-sm">
            <Checkbox checked={form.faceVisibility} onCheckedChange={(v) => set('faceVisibility', v === true)} />
            Check that the face is visible
          </label>
          <label className="flex items-center gap-2 text-sm">
            <Checkbox checked={form.cameraRequired} onCheckedChange={(v) => set('cameraRequired', v === true)} />
            Students must allow the camera to join
          </label>
          <label className="flex items-start gap-2 text-sm">
            <Checkbox checked={form.microphoneRequired} onCheckedChange={(v) => set('microphoneRequired', v === true)} />
            <span>
              Students must allow the microphone to join
              <span className="block text-xs text-muted-foreground">
                Permission only: nothing is listened to or recorded, because students speak in a live class. A note is
                kept if the permission is later removed.
              </span>
            </span>
          </label>
          <label className="flex items-center gap-2 text-sm">
            <Checkbox checked={form.showWarning} onCheckedChange={(v) => set('showWarning', v === true)} />
            Show the student a warning
          </label>
          <label className="flex items-center gap-2 text-sm">
            <Checkbox checked={form.logEvents} onCheckedChange={(v) => set('logEvents', v === true)} />
            Record events for trainer review
          </label>

          <div className="flex flex-col gap-2">
            <Label htmlFor="mon-after">Warn after the face is missing for (seconds)</Label>
            <Input
              id="mon-after"
              type="number"
              min={3}
              max={300}
              value={form.warningAfterSeconds}
              onChange={(e) => set('warningAfterSeconds', Number(e.target.value))}
            />
          </div>
          <div className="flex flex-col gap-2 sm:col-span-2">
            <Label htmlFor="mon-msg">Warning message</Label>
            <Input
              id="mon-msg"
              maxLength={200}
              placeholder={DEFAULT_WARNING}
              value={form.warningMessage}
              onChange={(e) => set('warningMessage', e.target.value)}
            />
          </div>
          <div>
            <Button
              disabled={save.isPending || (form.scopeType !== 'INSTITUTE' && !form.scopeId)}
              onClick={() => save.mutate(form)}
            >
              Save setting
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>All settings</CardTitle>
        </CardHeader>
        <CardContent>
          {settings.isLoading && <Skeleton className="h-24" />}
          {settings.data && settings.data.length === 0 && (
            <p className="text-sm text-muted-foreground">None yet. Monitoring is off everywhere.</p>
          )}
          {settings.data && settings.data.length > 0 && (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Level</TableHead>
                  <TableHead>Monitoring</TableHead>
                  <TableHead>Camera required</TableHead>
                  <TableHead>Mic required</TableHead>
                  <TableHead>Warn after</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {settings.data.map((s) => (
                  <TableRow key={s.id}>
                    <TableCell>{describe(s)}</TableCell>
                    <TableCell>
                      <Badge variant={s.enabled ? 'default' : 'secondary'}>{s.enabled ? 'ON' : 'OFF'}</Badge>
                    </TableCell>
                    <TableCell>{s.cameraRequired ? 'Yes' : 'No'}</TableCell>
                    <TableCell>{s.microphoneRequired ? 'Yes' : 'No'}</TableCell>
                    <TableCell>{s.warningAfterSeconds}s</TableCell>
                    <TableCell className="text-right">
                      <Button size="sm" variant="ghost" disabled={remove.isPending} onClick={() => remove.mutate(s.id)}>
                        Remove
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

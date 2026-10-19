import { useState } from 'react'

import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { cn } from '@/lib/utils'
import { AskQuestionPanel } from './ask-question-panel'
import { RoomControlsBody } from './host-controls-panel'
import { LiveCodePanel } from './live-code-panel'
import { MonitoringEventsPanel } from './monitoring-events-panel'

/** The trainer's/staff's sidebar in a live class: room controls, questions, a code runner and monitoring. */
export function HostSidePanel({ liveSessionId, classSessionId }: { liveSessionId: number; classSessionId: number }) {
  const [tab, setTab] = useState('controls')

  return (
    <aside
      // The code tab needs room for an editor, so the panel widens while it is open.
      className={cn(
        'dark flex shrink-0 flex-col overflow-y-auto border-l border-white/10 bg-[oklch(0.17_0.008_50)] p-3 text-foreground transition-[width]',
        tab === 'code' ? 'w-[42rem] max-w-[60vw]' : 'w-96',
      )}
    >
      <Tabs value={tab} onValueChange={setTab}>
        <TabsList className="mb-3 w-full">
          <TabsTrigger value="controls">Controls</TabsTrigger>
          <TabsTrigger value="questions">Questions</TabsTrigger>
          <TabsTrigger value="code">Code</TabsTrigger>
          <TabsTrigger value="monitoring">Monitoring</TabsTrigger>
        </TabsList>
        <TabsContent value="controls">
          <RoomControlsBody liveSessionId={liveSessionId} />
        </TabsContent>
        <TabsContent value="questions">
          <AskQuestionPanel liveSessionId={liveSessionId} classSessionId={classSessionId} />
        </TabsContent>
        <TabsContent value="code">
          <LiveCodePanel liveSessionId={liveSessionId} />
        </TabsContent>
        <TabsContent value="monitoring">
          <MonitoringEventsPanel classSessionId={classSessionId} />
        </TabsContent>
      </Tabs>
    </aside>
  )
}

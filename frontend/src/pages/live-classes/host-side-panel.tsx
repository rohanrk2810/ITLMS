import { useState } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'

import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { cn } from '@/lib/utils'
import { AskQuestionPanel } from './ask-question-panel'
import { RoomControlsBody } from './host-controls-panel'
import { LiveCodePanel } from './live-code-panel'
import { MonitoringEventsPanel } from './monitoring-events-panel'

const COLLAPSED_KEY = 'itilms.hostPanelCollapsed'

function storedCollapsed(): boolean {
  try {
    return localStorage.getItem(COLLAPSED_KEY) === '1'
  } catch {
    return false
  }
}

/**
 * The trainer's/staff's sidebar in a live class: room controls, questions, a code runner and monitoring. The arrow on its
 * edge tucks it away to give the video the whole width (and brings it back); the choice is remembered in this browser.
 */
export function HostSidePanel({ liveSessionId, classSessionId }: { liveSessionId: number; classSessionId: number }) {
  const [tab, setTab] = useState('controls')
  const [collapsed, setCollapsed] = useState(storedCollapsed)

  function toggle() {
    const next = !collapsed
    setCollapsed(next)
    try {
      localStorage.setItem(COLLAPSED_KEY, next ? '1' : '0')
    } catch {
      // Not remembered; it still works for this visit.
    }
  }

  return (
    <div className="relative flex shrink-0">
      <button
        type="button"
        onClick={toggle}
        aria-label={collapsed ? 'Show the side panel' : 'Hide the side panel'}
        title={collapsed ? 'Show the side panel' : 'Hide the side panel'}
        className="absolute top-3 -left-6 z-20 flex h-10 w-6 items-center justify-center rounded-l-md border border-r-0 border-white/10 bg-[oklch(0.17_0.008_50)] text-white/70 shadow transition-colors hover:text-white"
      >
        {collapsed ? <ChevronLeft className="size-4" /> : <ChevronRight className="size-4" />}
      </button>
      <aside
        // The code tab needs room for an editor, so the panel widens while it is open. Hidden, it stays mounted: its
        // lists keep their place and nothing reloads when it comes back.
        className={cn(
          'dark shrink-0 flex-col overflow-y-auto border-l border-white/10 bg-[oklch(0.17_0.008_50)] p-3 text-foreground transition-[width]',
          collapsed ? 'hidden' : 'flex',
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
    </div>
  )
}

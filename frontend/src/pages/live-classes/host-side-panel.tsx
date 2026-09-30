import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { AskQuestionPanel } from './ask-question-panel'
import { RoomControlsBody } from './host-controls-panel'

/** The trainer's/staff's sidebar in a live class: room controls and asking the class questions. */
export function HostSidePanel({ liveSessionId, classSessionId }: { liveSessionId: number; classSessionId: number }) {
  return (
    <aside className="flex w-96 shrink-0 flex-col overflow-y-auto border-l bg-card p-3">
      <Tabs defaultValue="controls">
        <TabsList className="mb-3 w-full">
          <TabsTrigger value="controls">Room controls</TabsTrigger>
          <TabsTrigger value="questions">Questions</TabsTrigger>
        </TabsList>
        <TabsContent value="controls">
          <RoomControlsBody liveSessionId={liveSessionId} />
        </TabsContent>
        <TabsContent value="questions">
          <AskQuestionPanel liveSessionId={liveSessionId} classSessionId={classSessionId} />
        </TabsContent>
      </Tabs>
    </aside>
  )
}

import { PracticeEditor } from '@/components/practice-editor'

/**
 * The trainer's code runner inside a live class: pick a language, write or paste code, run it and show the output,
 * all without leaving the room. It is the same editor and sandbox the students use for practice, so what the trainer
 * demonstrates behaves exactly as it will for them. Share the screen to show it to the class.
 */
export function LiveCodePanel({ liveSessionId }: { liveSessionId: number }) {
  return (
    <PracticeEditor
      // Drafts are kept per class; live classes use negative ids so they never clash with a lesson's.
      lessonId={-liveSessionId}
      language="PYTHON"
      starterCode={null}
      allowLanguageChoice
      title="Run code in this class"
      dark
    />
  )
}

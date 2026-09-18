import { QuizAttemptPage } from '@/pages/assessments/quiz-attempt-page'
import { QuizEditorPage } from '@/pages/assessments/quiz-editor-page'
import { useAuthStore } from '@/stores/auth-store'

export function QuizIndexPage() {
  const role = useAuthStore((state) => state.user?.role)
  return role === 'STUDENT' ? <QuizAttemptPage /> : <QuizEditorPage />
}

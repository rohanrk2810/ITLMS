import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { CreditCardIcon, MessageCircleQuestionIcon } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import { createOnlineOrder, getCheckoutConfig, payWithRazorpay, verifyOnlinePayment } from '@/api/online-payments'

import type { CourseSummaryResponse } from '@/api/courses'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { EnquiryForm } from '@/pages/home/enquiry-form'
import { formatMoney, hasCourseFee } from '@/lib/format'
import { hasRole, useAuthStore } from '@/stores/auth-store'

type Course = Pick<CourseSummaryResponse, 'id' | 'title'> & Partial<CourseSummaryResponse>

/**
 * The Buy and Inquiry buttons on a course card. Inquiry opens a short form that lands in the admission team's leads;
 * Buy offers to start the purchase (the course page for a signed-in student, sign-up otherwise) or to inquire first.
 */
export function CourseActions({ course }: { course: Course }) {
  const [mode, setMode] = useState<'buy' | 'inquiry' | null>(null)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const signedIn = useAuthStore((state) => state.status) === 'authenticated'
  const user = useAuthStore((state) => state.user)
  const roles = useAuthStore((state) => state.roles)
  const isStudent = signedIn && hasRole(roles, ['STUDENT'])
  const [paying, setPaying] = useState(false)
  const config = useQuery({
    queryKey: ['online-payments', 'config'],
    queryFn: getCheckoutConfig,
    enabled: isStudent && mode === 'buy',
    staleTime: 5 * 60_000,
  })
  const canPayOnline = isStudent && config.data?.enabled === true && hasCourseFee(course.fee ?? null)

  async function payOnline() {
    setPaying(true)
    try {
      const order = await createOnlineOrder(course.id)
      const result = await payWithRazorpay(order, { name: user?.fullName, email: user?.email, contact: user?.phone })
      if (!result) return // the student closed the payment window
      const status = await verifyOnlinePayment(result)
      void queryClient.invalidateQueries({ queryKey: ['courses'] })
      toast.success(status.message ?? 'Payment received.')
      setMode(null)
      if (status.status === 'ENROLLED') navigate(`/app/courses/${course.id}`)
    } catch (error) {
      toast.error(apiErrorMessage(error, error instanceof Error ? error.message : 'The payment could not be completed.'))
    } finally {
      setPaying(false)
    }
  }

  function startPurchase() {
    setMode(null)
    navigate(signedIn ? `/app/courses/${course.id}` : '/register')
  }

  return (
    <>
      <div className="flex gap-2">
        <Button type="button" size="sm" className="flex-1" onClick={() => setMode('buy')}>
          <CreditCardIcon /> Buy
        </Button>
        <Button type="button" size="sm" variant="outline" className="flex-1" onClick={() => setMode('inquiry')}>
          <MessageCircleQuestionIcon /> Inquiry
        </Button>
      </div>

      <Dialog open={mode === 'inquiry'} onOpenChange={(open) => !open && setMode(null)}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>Inquiry about {course.title}</DialogTitle>
            <DialogDescription>Leave your details and the admission team will call you back.</DialogDescription>
          </DialogHeader>
          <EnquiryForm courses={[course as CourseSummaryResponse]} courseId={course.id} />
        </DialogContent>
      </Dialog>

      <Dialog open={mode === 'buy'} onOpenChange={(open) => !open && setMode(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Buy {course.title}</DialogTitle>
            <DialogDescription>How would you like to go ahead?</DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-3">
            {canPayOnline && (
              <Button type="button" onClick={() => void payOnline()} disabled={paying}>
                <CreditCardIcon /> {paying ? 'Opening payment...' : `Pay ${formatMoney(course.fee ?? 0)} online`}
              </Button>
            )}
            <Button type="button" variant={canPayOnline ? 'outline' : 'default'} onClick={startPurchase}>
              <CreditCardIcon /> {signedIn ? 'Continue to the course' : 'Create an account and continue'}
            </Button>
            <Button type="button" variant="outline" onClick={() => setMode('inquiry')}>
              <MessageCircleQuestionIcon /> I have questions first: send an inquiry
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  )
}

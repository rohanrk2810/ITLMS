import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { CreditCardIcon, MessageCircleQuestionIcon } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { apiErrorMessage } from '@/api/client'
import type { CourseSummaryResponse } from '@/api/courses'
import { createBatchOrder, getCheckoutConfig, payWithRazorpay, verifyOnlinePayment } from '@/api/online-payments'
import type { PublicBatch } from '@/api/public-site'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { EnquiryForm } from '@/pages/home/enquiry-form'
import { formatMoney } from '@/lib/format'
import { useAuthDialog } from '@/stores/auth-dialog-store'
import { hasRole, useAuthStore } from '@/stores/auth-store'

/**
 * The Buy and Inquiry buttons on a batch card. A signed-in student with online payment set up pays the batch fee
 * through Razorpay and is placed in the batch with every course in it; anyone else is asked to sign in or register
 * first, or can send an inquiry.
 */
export function BatchActions({ batch }: { batch: PublicBatch }) {
  const [mode, setMode] = useState<'buy' | 'inquiry' | null>(null)
  const navigate = useNavigate()
  const showAuth = useAuthDialog((state) => state.show)
  const queryClient = useQueryClient()
  const signedIn = useAuthStore((state) => state.status) === 'authenticated'
  const user = useAuthStore((state) => state.user)
  const roles = useAuthStore((state) => state.roles)
  const isStudent = signedIn && hasRole(roles, ['STUDENT'])
  const [paying, setPaying] = useState(false)
  const title = batch.name?.trim() || batch.batchCode
  const config = useQuery({
    queryKey: ['online-payments', 'config'],
    queryFn: getCheckoutConfig,
    enabled: isStudent && mode === 'buy',
    staleTime: 5 * 60_000,
  })
  const canPayOnline = isStudent && config.data?.enabled === true

  async function payOnline() {
    setPaying(true)
    try {
      const order = await createBatchOrder(batch)
      const result = await payWithRazorpay(order, { name: user?.fullName, email: user?.email, contact: user?.phone })
      if (!result) return // the student closed the payment window
      const status = await verifyOnlinePayment(result)
      void queryClient.invalidateQueries({ queryKey: ['courses'] })
      void queryClient.invalidateQueries({ queryKey: ['public-batches'] })
      toast.success(status.message ?? 'Payment received.')
      setMode(null)
      if (status.status === 'ENROLLED') navigate('/app/courses')
    } catch (error) {
      toast.error(apiErrorMessage(error, error instanceof Error ? error.message : 'The payment could not be completed.'))
    } finally {
      setPaying(false)
    }
  }

  // The inquiry form wants a course; the batch's own course is the one the admission team will follow up on.
  const course = { id: batch.courseId, title: batch.courseTitles[0] ?? title } as CourseSummaryResponse

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
            <DialogTitle>Inquiry about {title}</DialogTitle>
            <DialogDescription>Leave your details and the admission team will call you back.</DialogDescription>
          </DialogHeader>
          <EnquiryForm courses={[course]} courseId={batch.courseId} />
        </DialogContent>
      </Dialog>

      <Dialog open={mode === 'buy'} onOpenChange={(open) => !open && setMode(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Join {title}</DialogTitle>
            <DialogDescription>
              {batch.courseTitles.join(', ')}. One fee of {formatMoney(batch.fee)} covers every course in the batch.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-3">
            {canPayOnline && (
              <Button type="button" onClick={() => void payOnline()} disabled={paying}>
                <CreditCardIcon /> {paying ? 'Opening payment...' : `Pay ${formatMoney(batch.fee)} online`}
              </Button>
            )}
            {signedIn && !isStudent && (
              <p className="text-sm text-muted-foreground">Only a student account can buy a batch. Send an inquiry instead.</p>
            )}
            {isStudent && config.data?.enabled === false && (
              <p className="text-sm text-muted-foreground">Online payment is not set up yet. Send an inquiry and we will call you.</p>
            )}
            {!signedIn && (
              <>
                <Button
                  type="button"
                  onClick={() => {
                    setMode(null)
                    showAuth('register')
                  }}
                >
                  <CreditCardIcon /> Create an account and continue
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    setMode(null)
                    showAuth('login')
                  }}
                >
                  I already have an account: sign in
                </Button>
              </>
            )}
            <Button type="button" variant="outline" onClick={() => setMode('inquiry')}>
              <MessageCircleQuestionIcon /> I have questions first: send an inquiry
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  )
}

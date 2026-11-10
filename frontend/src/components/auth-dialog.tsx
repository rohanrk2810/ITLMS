import { useEffect } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog'
import { LoginForm } from '@/pages/login-page'
import { RegisterForm } from '@/pages/register-page'
import { useAuthDialog } from '@/stores/auth-dialog-store'
import { hasRole, useAuthStore } from '@/stores/auth-store'

/**
 * Sign in and Register as a popup over the page the visitor is on, so they do not lose their place. The forms are
 * the same ones the /login and /register pages show, with the same checks. After signing in, a student on a public
 * page stays where they were (so a course they were about to buy is still in front of them, now with Pay online);
 * everyone else goes to the portal. A new account goes to My profile first, to add the photo.
 */
export function AuthDialog() {
  const open = useAuthDialog((state) => state.open)
  const show = useAuthDialog((state) => state.show)
  const close = useAuthDialog((state) => state.close)
  const status = useAuthStore((state) => state.status)
  const navigate = useNavigate()
  const { pathname } = useLocation()

  // Signed in by any route (a social login, another tab): nothing left to ask.
  useEffect(() => {
    if (status === 'authenticated' && open) close()
  }, [status, open, close])

  function signedIn() {
    const { roles } = useAuthStore.getState()
    close()
    const student = hasRole(roles, ['STUDENT']) && roles.length === 1
    // The front page sends a signed-in visitor to the portal by itself; a student on any other public page stays.
    if (!(student && pathname !== '/')) void navigate('/app', { replace: true })
  }

  function registered() {
    close()
    void navigate('/app/my-profile', { replace: true })
  }

  return (
    <Dialog open={open !== null} onOpenChange={(value) => !value && close()}>
      <DialogContent
        className={`max-h-[92vh] overflow-y-auto p-4 sm:p-6 ${open === 'register' ? 'sm:max-w-3xl' : 'sm:max-w-md'} [&_[data-slot=card]]:border-0 [&_[data-slot=card]]:bg-transparent [&_[data-slot=card]]:p-0 [&_[data-slot=card]]:shadow-none`}
      >
        <DialogTitle className="sr-only">{open === 'register' ? 'Create your student account' : 'Sign in'}</DialogTitle>
        <DialogDescription className="sr-only">
          {open === 'register' ? 'Create a student account.' : 'Sign in with your email or phone number.'}
        </DialogDescription>
        {open === 'login' && <LoginForm onSignedIn={signedIn} onCreateAccount={() => show('register')} onNavigate={close} />}
        {open === 'register' && <RegisterForm onRegistered={registered} onSignIn={() => show('login')} />}
      </DialogContent>
    </Dialog>
  )
}

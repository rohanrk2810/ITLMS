import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

import { BrandMark } from '@/components/brand-mark'
import { SiteFooter } from '@/components/site-footer'
import { ThemeToggle } from '@/components/theme-toggle'
import { Button } from '@/components/ui/button'
import { useAuthStore } from '@/stores/auth-store'

const NAV = [
  { to: '/courses', label: 'Courses' },
  { to: '/pricing', label: 'Pricing' },
  { to: '/about', label: 'About' },
  { to: '/contact', label: 'Contact' },
]

/**
 * The frame of every public page that is not the front page: top bar, a title, the content, and the footer. Pages
 * such as /courses, /pricing, /about and /contact all use it, so they look and behave alike.
 */
export function SiteShell({
  title,
  intro,
  children,
  narrow,
}: {
  title: string
  intro?: string
  children: ReactNode
  /** A reading column for text pages. */
  narrow?: boolean
}) {
  const status = useAuthStore((state) => state.status)
  return (
    <div className="flex min-h-svh flex-col bg-background text-foreground">
      <header className="sticky top-0 z-30 border-b bg-background/90 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-6xl items-center justify-between gap-3 px-4">
          <Link to="/" className="flex min-w-0 items-center gap-2 font-semibold">
            <BrandMark className="size-6" />
          </Link>
          <nav aria-label="Main" className="hidden items-center gap-1 md:flex">
            {NAV.map((item) => (
              <Link key={item.to} to={item.to} className="rounded-md px-3 py-1.5 text-sm text-muted-foreground hover:bg-muted hover:text-foreground">
                {item.label}
              </Link>
            ))}
          </nav>
          <div className="flex items-center gap-2">
            <ThemeToggle />
            {status === 'authenticated' ? (
              <Button asChild size="sm">
                <Link to="/app">Open my portal</Link>
              </Button>
            ) : (
              <>
                <Button asChild size="sm" variant="ghost">
                  <Link to="/login">Sign in</Link>
                </Button>
                <Button asChild size="sm">
                  <Link to="/register">Register</Link>
                </Button>
              </>
            )}
          </div>
        </div>
      </header>

      <main id="main" className="flex-1 px-4 py-10">
        <div className={`mx-auto ${narrow ? 'max-w-3xl' : 'max-w-6xl'}`}>
          <div className="mb-8 text-center">
            <h1 className="text-3xl font-semibold tracking-tight sm:text-4xl">{title}</h1>
            {intro && <p className="mx-auto mt-2 max-w-2xl text-muted-foreground">{intro}</p>}
          </div>
          {children}
        </div>
      </main>

      <SiteFooter />
    </div>
  )
}

import type { ReactNode } from 'react'

import { SiteFooter } from '@/components/site-footer'
import { SiteNav } from '@/components/site-nav'

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
  return (
    <div className="flex min-h-svh flex-col bg-background text-foreground">
      <SiteNav />

      <main id="main" className="flex-1 px-4 py-10">
        <div className={`page-in mx-auto ${narrow ? 'max-w-3xl' : 'max-w-6xl'}`}>
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

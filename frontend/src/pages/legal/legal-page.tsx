import { Link } from 'react-router-dom'

import { BrandMark } from '@/components/brand-mark'
import { LegalLinks } from '@/components/legal-links'
import { useBranding } from '@/lib/use-branding'

import { LEGAL_DOCS, LEGAL_UPDATED, type LegalSlug } from './legal-content'

/** Public: privacy, terms, cookie and refund policies share one layout. */
export function LegalPage({ slug }: { slug: LegalSlug }) {
  const { branding, name } = useBranding()
  const doc = LEGAL_DOCS[slug]

  return (
    <div className="min-h-svh bg-muted/40 px-4 py-8">
      <main className="mx-auto flex max-w-3xl flex-col gap-6 rounded-xl bg-background p-6 shadow-sm sm:p-10">
        <Link to="/" className="flex items-center gap-2 text-sm font-semibold" aria-label={`${name} home`}>
          <BrandMark className="size-8" />
        </Link>
        <header className="flex flex-col gap-1">
          <h1 className="text-2xl font-semibold">{doc.title}</h1>
          <p className="text-sm text-muted-foreground">{doc.summary}</p>
          <p className="text-xs text-muted-foreground">
            {name} · Last updated {LEGAL_UPDATED}
          </p>
        </header>
        {doc.sections.map((section) => (
          <section key={section.heading} className="flex flex-col gap-2">
            <h2 className="text-lg font-medium">{section.heading}</h2>
            {section.body.map((line) =>
              line.startsWith('- ') ? (
                <ul key={line} className="ml-5 list-disc text-sm">
                  <li>{line.slice(2)}</li>
                </ul>
              ) : (
                <p key={line} className="text-sm leading-relaxed">
                  {line}
                </p>
              ),
            )}
          </section>
        ))}
        <section className="flex flex-col gap-1 border-t pt-4 text-sm">
          <h2 className="text-lg font-medium">Contact</h2>
          <p>{name}</p>
          {branding?.address && <p>{branding.address}</p>}
          {branding?.contactEmail && (
            <p>
              Email: <a className="underline" href={`mailto:${branding.contactEmail}`}>{branding.contactEmail}</a>
            </p>
          )}
          {branding?.contactPhone && <p>Phone: {branding.contactPhone}</p>}
        </section>
        <LegalLinks className="border-t pt-4" />
      </main>
    </div>
  )
}

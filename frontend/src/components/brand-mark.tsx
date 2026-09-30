import { GraduationCap } from 'lucide-react'

import { assetUrl } from '@/api/branding'
import { useBranding } from '@/lib/use-branding'

/** The institute's logo (or a default icon) beside its name. */
export function BrandMark({ className = 'size-5' }: { className?: string }) {
  const { branding, name } = useBranding()
  const logo = assetUrl(branding?.logoUrl ?? null)

  return (
    <>
      {logo ? (
        <img src={logo} alt="" className={`${className} shrink-0 object-contain`} />
      ) : (
        <GraduationCap className={className} />
      )}
      <span className="truncate">{name}</span>
    </>
  )
}

import { useEffect, useState } from 'react'
import { ChevronDownIcon, MenuIcon, MessageCircleQuestionIcon, PhoneIcon, XIcon } from 'lucide-react'
import { Link, useLocation } from 'react-router-dom'

import { BrandMark } from '@/components/brand-mark'
import { EnquiryBlock } from '@/components/enquiry-block'
import { ThemeToggle } from '@/components/theme-toggle'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { useBranding } from '@/lib/use-branding'
import { useAuthStore } from '@/stores/auth-store'

const LINKS = [
  { to: '/', label: 'Home' },
  { to: '/courses', label: 'Courses' },
  { to: '/programs', label: 'Programs' },
  { to: '/pricing', label: 'Pricing' },
  { to: '/about', label: 'About' },
  { to: '/contact', label: 'Contact' },
]

const RESOURCES = [
  { to: '/blog', label: 'Blog' },
  { to: '/career-guide', label: 'Career Guide' },
  { to: '/learning-paths', label: 'Learning Paths' },
  { to: '/free-resources', label: 'Free Resources' },
  { to: '/success-stories', label: 'Success Stories' },
  { to: '/community', label: 'Student Community' },
  { to: '/faq', label: 'FAQ' },
]

/**
 * The top bar of every public page: Home, Courses, Programs, Pricing, About and Contact, a Resources menu, the
 * institute phone number, an Inquiry button, and sign-in. It stays at the top, gains a shadow once the page is
 * scrolled, underlines the page you are on, and becomes a menu button on a phone.
 */
export function SiteNav() {
  const { branding } = useBranding()
  const status = useAuthStore((state) => state.status)
  const { pathname } = useLocation()
  const [scrolled, setScrolled] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const [resourcesOpen, setResourcesOpen] = useState(false)
  const [inquiryOpen, setInquiryOpen] = useState(false)
  const phone = branding?.contactPhone?.trim()

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8)
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  const inResources = RESOURCES.some((item) => item.to === pathname)

  return (
    <header
      className={`sticky top-0 z-40 border-b backdrop-blur transition-all duration-300 ${
        scrolled ? 'bg-background/95 shadow-md' : 'bg-background/80'
      }`}
    >
      <div className={`mx-auto flex max-w-6xl items-center gap-3 px-4 transition-all duration-300 ${scrolled ? 'h-14' : 'h-16'}`}>
        <Link to="/" className="flex min-w-0 items-center gap-2 font-semibold transition-transform duration-200 hover:scale-[1.03]" aria-label="Home">
          <BrandMark className="size-7" />
        </Link>

        <nav aria-label="Main" className="ml-4 hidden items-center lg:flex">
          {LINKS.map((item) => (
            <NavItem key={item.to} to={item.to} active={pathname === item.to}>
              {item.label}
            </NavItem>
          ))}
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button
                type="button"
                className="group relative flex items-center gap-1 rounded-md px-3 py-2 text-sm text-muted-foreground outline-none transition-colors hover:text-foreground focus-visible:ring-[3px] focus-visible:ring-ring/50 data-[state=open]:text-foreground"
                data-active={inResources}
              >
                Resources
                <ChevronDownIcon className="size-3.5 transition-transform duration-200 group-data-[state=open]:rotate-180" />
                <Underline active={inResources} />
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start" className="w-52">
              {RESOURCES.map((item) => (
                <DropdownMenuItem key={item.to} asChild>
                  <Link to={item.to} className="cursor-pointer">
                    {item.label}
                  </Link>
                </DropdownMenuItem>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>
        </nav>

        <div className="ml-auto flex items-center gap-2">
          {phone && (
            <a
              href={`tel:${phone.replace(/\s+/g, '')}`}
              className="hidden items-center gap-1.5 rounded-md px-2 py-1.5 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground xl:flex"
            >
              <PhoneIcon className="size-4 text-primary" />
              {phone}
            </a>
          )}
          <Button size="sm" variant="outline" className="hidden sm:inline-flex" onClick={() => setInquiryOpen(true)}>
            <MessageCircleQuestionIcon /> Inquiry
          </Button>
          <ThemeToggle />
          {status === 'authenticated' ? (
            <Button asChild size="sm">
              <Link to="/app">Open my portal</Link>
            </Button>
          ) : (
            <>
              <Button asChild size="sm" variant="ghost" className="hidden sm:inline-flex">
                <Link to="/login">Login</Link>
              </Button>
              <Button asChild size="sm">
                <Link to="/register">Register</Link>
              </Button>
            </>
          )}
          <Button
            size="icon"
            variant="ghost"
            className="lg:hidden"
            aria-label={menuOpen ? 'Close menu' : 'Open menu'}
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((open) => !open)}
          >
            {menuOpen ? <XIcon /> : <MenuIcon />}
          </Button>
        </div>
      </div>

      {menuOpen && (
        <nav aria-label="Mobile" className="menu-down flex max-h-[75svh] flex-col gap-1 overflow-y-auto border-t px-4 py-3 lg:hidden">
          {LINKS.map((item) => (
            <Link
              key={item.to}
              to={item.to}
              onClick={() => setMenuOpen(false)}
              className={`rounded-md px-3 py-2.5 text-sm transition-colors hover:bg-muted ${pathname === item.to ? 'bg-muted font-medium text-foreground' : ''}`}
            >
              {item.label}
            </Link>
          ))}
          <button
            type="button"
            aria-expanded={resourcesOpen}
            onClick={() => setResourcesOpen((open) => !open)}
            className="flex items-center justify-between rounded-md px-3 py-2.5 text-left text-sm transition-colors hover:bg-muted"
          >
            Resources
            <ChevronDownIcon className={`size-4 transition-transform duration-200 ${resourcesOpen ? 'rotate-180' : ''}`} />
          </button>
          {resourcesOpen && (
            <div className="menu-down ml-3 flex flex-col gap-1 border-l pl-3">
              {RESOURCES.map((item) => (
                <Link
                  key={item.to}
                  to={item.to}
                  onClick={() => setMenuOpen(false)}
                  className={`rounded-md px-3 py-2 text-sm transition-colors hover:bg-muted ${pathname === item.to ? 'bg-muted font-medium' : 'text-muted-foreground'}`}
                >
                  {item.label}
                </Link>
              ))}
            </div>
          )}
          <div className="mt-2 flex flex-wrap items-center gap-2 border-t pt-3">
            {phone && (
              <Button asChild size="sm" variant="outline">
                <a href={`tel:${phone.replace(/\s+/g, '')}`}>
                  <PhoneIcon /> {phone}
                </a>
              </Button>
            )}
            <Button
              size="sm"
              onClick={() => {
                setMenuOpen(false)
                setInquiryOpen(true)
              }}
            >
              <MessageCircleQuestionIcon /> Inquiry
            </Button>
            {status !== 'authenticated' && (
              <Button asChild size="sm" variant="ghost">
                <Link to="/login" onClick={() => setMenuOpen(false)}>
                  Login
                </Link>
              </Button>
            )}
          </div>
        </nav>
      )}

      <Dialog open={inquiryOpen} onOpenChange={setInquiryOpen}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>Talk to us</DialogTitle>
            <DialogDescription>Leave your details and the admission team will call you back.</DialogDescription>
          </DialogHeader>
          <EnquiryBlock />
        </DialogContent>
      </Dialog>
    </header>
  )
}

function NavItem({ to, active, children }: { to: string; active: boolean; children: React.ReactNode }) {
  return (
    <Link
      to={to}
      data-active={active}
      className="group relative rounded-md px-3 py-2 text-sm text-muted-foreground transition-colors hover:text-foreground data-[active=true]:font-medium data-[active=true]:text-foreground"
    >
      {children}
      <Underline active={active} />
    </Link>
  )
}

/** A line that grows under a link when it is hovered, and stays for the page you are on. */
function Underline({ active }: { active: boolean }) {
  return (
    <span
      aria-hidden="true"
      className={`absolute inset-x-3 -bottom-px h-0.5 origin-left rounded-full bg-primary transition-transform duration-300 ${
        active ? 'scale-x-100' : 'scale-x-0 group-hover:scale-x-100'
      }`}
    />
  )
}

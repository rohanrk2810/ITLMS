import { Moon, Sun } from 'lucide-react'

import { useTheme } from '@/lib/use-theme'
import { Button } from '@/components/ui/button'

export function ThemeToggle() {
  const { theme, toggle } = useTheme()
  return (
    <Button variant="ghost" size="icon" aria-label="Switch to the other color mode" onClick={toggle}>
      {theme === 'dark' ? <Sun /> : <Moon />}
    </Button>
  )
}

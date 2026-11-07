# Design system

The visual rules of the IT-ILMS web app. The single source of truth for colour is `frontend/src/index.css`; components are in `frontend/src/components/ui/`. This page explains them so new screens look like old ones.

Stack: Tailwind CSS v4 (tokens declared with `@theme inline`), shadcn/ui "new-york" style written by hand (no CLI needed; `components.json` is there for `npx shadcn add`), Radix primitives, `lucide-react` icons, `sonner` toasts, Chart.js for charts, Monaco for code.

---

## 1. Principles

1. **One accent.** A warm orange (hue about 41) is the only brand colour. Buttons, focus rings and the active menu item all use it, in light and dark, so the accent reads as one.
2. **Tokens, not colours.** Use `bg-primary`, `text-muted-foreground`, `border-border`. Never a hex value or a raw Tailwind colour in a page.
3. **Look can be changed by the admin.** Institute name, brand colour, login background, page background, menu order and labels are settings (see section 8); do not hard-code anything that Customize can change.
4. **Dense but calm.** This is a work tool used all day: small controls (`h-9`), clear spacing, no decoration that does not carry meaning.
5. **Every state is designed.** Loading (skeleton), empty (one plain sentence), error (toast plus red fields), and disabled.

## 2. Colour tokens

All values are `oklch`. Light mode is a cream page with white cards; dark mode is near black.

| Token | Light | Dark | Use |
|---|---|---|---|
| `--background` | `0.99 0.01 75` | `0.145 0.005 60` | Page |
| `--foreground` | `0.205 0.01 40` | `0.985 0 0` | Body text |
| `--card` / `--popover` | white | `0.205 0.008 50` | Panels, menus |
| `--primary` | `0.646 0.222 41.116` | `0.705 0.213 47.604` | Main action, active item, focus |
| `--primary-foreground` | `0.985 0 0` | `0.145 0.01 40` | Text on primary |
| `--secondary`, `--accent` | `0.954 0.038 75.164` | `0.269 / 0.3` warm greys | Hover and soft fills |
| `--muted` / `--muted-foreground` | `0.97 0.015 75` / `0.5 0.03 45` | `0.269 0.012 50` / `0.708 0.02 60` | Quiet areas, helper text |
| `--destructive` | `0.577 0.245 27.325` | `0.704 0.191 22.216` | Delete, errors, the assist/stop states |
| `--border`, `--input` | `0.93 0.025 70` | white at 10% / 15% | Lines, fields |
| `--ring` | `0.705 0.213 47.604` | same | Focus outline |
| `--sidebar*` | white panel, orange active item | `0.17 0.008 50` | Left menu |

Radius: `--radius` is 0.625rem; `rounded-sm/md/lg/xl` derive from it.

Status words (PRESENT, ABSENT, PASSED, TERMINATED, ...) are shown with `Badge` (variants `default`, `secondary`, `destructive`, `outline`), not ad-hoc colours; keep one meaning per variant across pages.

## 3. Typography and spacing

System font stack from Tailwind defaults (no web font to download). Page title `text-2xl font-semibold` with a `text-muted-foreground` line under it; section title `text-lg font-medium`; body `text-sm`; helper and table meta `text-xs text-muted-foreground`. Pages use `flex flex-col gap-6` and a `max-w-3xl` column for forms and lists; wide tables use the full width. Spacing is the Tailwind 4-pixel scale: `gap-2` inside a control group, `gap-3` between cards, `gap-6` between sections, `p-6` page padding.

## 4. Components (`components/ui`)

`alert`, `avatar`, `badge`, `button`, `card`, `checkbox`, `dialog`, `dropdown-menu`, `input`, `label`, `progress`, `radio-group`, `separator`, `sheet`, `skeleton`, `sonner`, `switch`, `table`, `tabs`.

| Component | Rule |
|---|---|
| `Button` | Variants `default` (one per view), `outline`, `secondary`, `ghost`, `destructive`, `link`. Sizes `sm`, `default`, `lg`, `icon`. An icon goes before the label (`size-4`). |
| `Dialog` | For creating or editing one thing; footer holds the primary action. Use `Sheet` for the mobile menu only. |
| `Card` | One idea per card; list items are cards with the action on the right. |
| `Table` | Tabular data only; wrap in a scroll container on small screens. |
| `Badge` | Short status words; never a sentence. |
| `Skeleton` | While a query loads; never a bare spinner on a page. |
| `Switch` / `Checkbox` | Settings that apply at once use `Switch`; choices in a form use `Checkbox`. |
| Toasts | `toast.success` after a save, `toast.error(apiErrorMessage(...))` after a failure. |

Add a new shared component here only when two screens need it; otherwise keep it beside the page.

## 5. Layout

`layouts/app-layout.tsx`: the app is exactly the height of the window (`h-svh overflow-hidden`); the left menu (`w-64`, collapsible with the edge arrow, remembered per browser) and the top bar (`h-14`) stay put and only `<main>` scrolls. On phones the menu is a `Sheet`. Pages ease in (`animate-in fade-in-0 slide-in-from-bottom-1`) and colours ease when the theme changes; both are switched off for people who ask their system for reduced motion (`index.css`).

Auth pages use `layouts/auth-layout.tsx` with the institute's login background.

## 6. Forms and errors

- Required fields are marked; a refused request names each field ("Duration minutes: must be at least 1"), the field turns red and shows the reason (`components/field-error-highlighter.tsx`, fed by the `itilms:field-errors` event from `api/client.ts`).
- Labels sit above inputs (`Label`), helper text under them. Never use placeholder as the only label.
- Destructive actions ask for confirmation and say what will be lost.

## 7. Icons, charts, code

- Icons: `lucide-react`, `size-4` in buttons and menus, `size-5` in headers. One icon per meaning across the app (the sidebar icon for a page is declared in `components/nav-items.ts`).
- Charts: Chart.js through `react-chartjs-2` (`components/charts`), colours read from the CSS tokens so dark mode works.
- Code: Monaco editor (`components/code-editor.tsx`) with language suggestions; results in `code-result-panel.tsx`.

## 8. Theme and customization

- **Theme:** `.dark` on `<html>`, toggled by `ThemeToggle` and stored by `lib/use-theme.ts`.
- **Branding** (ADMIN, `/app/branding`): institute name, tagline, logo, favicon, brand colour, contact details, certificate signatory, sign-in and app background; read publicly before login (`lib/use-branding.ts`, `BrandingEffect`).
- **Customize** (ADMIN, `/app/customize`): which menu items each role sees, their order and labels, per-student menu, and an app background picture with a strength slider. The picture and strength are CSS variables (`--ui-bg-image`, `--ui-bg-strength`); cards and menus keep their solid colours so text stays readable (`ui-has-bg` rules in `index.css`).

## 9. Accessibility

- Focus is always visible (`--ring`); do not remove outlines.
- Icon-only buttons need `aria-label`.
- Colour is never the only signal: errors also have text, statuses also have words.
- Keep contrast: text on `primary` uses `primary-foreground`.
- Interactive targets are at least `size-8`.

## 10. Adding a screen

1. Page component in `src/pages/<area>/`, lazy-loaded in `App.tsx`.
2. Title block (title plus one muted sentence), then content in cards.
3. Query with TanStack Query; show `Skeleton`, `EmptyState` (icon, title, hint) for an empty list, and errors via toast. A crash while rendering is caught by `ErrorBoundary` in `App.tsx` (Try again / Reload page), so a failed screen never turns into a blank page.
4. Add the menu item and its roles in `nav-items.ts` and the same roles on the route.
5. Check light and dark, and a narrow window.

## Name and text size

- Until the institute's branding loads, or if the server is down, the name shown is `SutraVidya` (`DEFAULT_NAME` in `use-branding.ts`, and the `<title>` in `index.html`).
- The portal's text-size setting (Customize, `fontScale`) applies inside the app only. `AuthLayout` resets the root size, so sign-in, register and reset-password stay at the normal size.
- Any component that renders a `<Link>` must sit inside `<BrowserRouter>`; one placed outside it crashes the whole app to a blank page (this happened with the cookie notice).

# Penny UI — Modern Minimal / Contemporary Dark

Approved31October2026: A for light, C for dark, plus System mode. The preference belongs to the browser and persists across login/logout; it never changes account permissions or session behavior.

## Visual system
The authoritative palette and component rules live in `frontend/src/themes.css`. Existing feature CSS uses semantic `--ui-*` tokens; compatibility aliases keep `--crm-*` and `--penny-*` consumers in the same system. Components must use these entries, not hardcoded colors.

| Role | Light A | Dark C |
|---|---|---|
| Canvas | #FAFBF9 | #11151B |
| Surface | #FFFFFF | #1A2029 |
| Primary text | #172F29 | #F0EEE9 |
| Secondary text | #58695E | #A4B0BC |
| Accent | #247655 | #E6B873 |
| Border | #DCE5DD | #394551 |
| Typeface | Manrope400–800 | IBM Plex Sans400–700 |

Fonts are self-hosted; OFL licenses ship alongside them. Use tabular numerals for money, counts and tables. Body14px, page title27–30px, section19–21px, labels12–14px; mobile form fields16px. Spacing primarily8/12/16/24/32px. Radius9px light,5px dark; dialogs12px. Keep borders and surfaces quiet; shadows identify overlays.

A uses an open KPI strip and side-by-side analysis. C emphasizes commercial analysis on graphite surfaces, warm amber action accents and a wider lead chart. Both retain labeled sidebar navigation rather than ambiguous icons alone. Navigation groups, API contracts, permissions, all chart span controls and annual comparison remain unchanged.

## Components and interaction
- ThemeSelector is shared by shell and authentication: Sistema / Chiaro / Scuro. Default System. Respond live to OS changes and other tabs; storage failures do not block use. Early HTML bootstrap prevents a light flash before rendering.
- Primary actions use accent/on-accent; secondary actions surface/text/border. Destructive actions use danger-soft/danger. Focus is a3px accent outline. Disabled controls remain recognizable and noninteractive.
- Inputs/selects are44px high. Search, segmented filters and actions remain aligned and wrap on mobile. Do not change business validations in visual work.
- Existing ResourceTable keeps sorting, filtering, column preferences, pagination and XLSX. Horizontal scrolling stays inside the table.
- Existing DetailModal keeps native focus protection, Escape and focus restoration. Theme applies to its backdrop, header, forms and dossier content.
- Prospect/customer, internal/third-party, site types and contact roles retain text labels and distinct semantic colors. Legal/info blue, administrative/violet, operational/success green, commercial/rose, warehouse/warning amber.
- Preserve loading/error/empty states and retry actions. Respect reduced-motion preferences. No continuous decorative animation.

## Verification/deployment
Build and frontend tests run only on CT111. Browser rendering uses the compiled actual app with intercepted fixture API responses in an isolated candidate container: no fixture data enters production. Production deployment changes only the frontend image; backend/database and Proxmox configuration remain untouched.

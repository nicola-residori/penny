# Approved dashboard composition
User correction: match the approved A/C prototypes structurally, not only via color tokens.
- Compact Panoramica header, current-year/monthly defaults, real new-client action.
- Preserve advanced span/customer/archive filters and annual comparison; collapse explanatory help.
- Four lead indicators, three line charts with period totals and selected-point readouts.
- A: horizontal charts, customer table + deadline column below. C: lead chart + stacked secondary charts alongside deadlines, customer table below.
- Existing authorized customer and analytics APIs feed a bounded five-row client preview; exact financial strings and XLSX preserved. Deadline APIs feed next30days with event type and origin labels.
- Reuse actual CustomersPanel dossier/editor in detailOnly mode; preserve filter state while refreshing after close.
- Historical overview, exact chart table and module shortcuts retained as secondary tools. No backend/data changes.
Verification: focused TDD for composition and refresh, permission/export/deadline regressions, full frontend suite, CT111 build, actual Chromium desktop/tablet/mobile capture of both themes, independent source/visual review. Deploy frontend only with rollback; dedicated branch, no merge.

Results:148tests/39files passed onCT111; TypeScript+Vite build passed. Actual-app Chromium fixture checks in both themes1440/768/390: no JS errors, document overflow or axe WCAG2A/AA findings in inspected states. Dossier contains no second customer table, Escape preserves span2mon and active annual comparison, new-client cancellation closes dialog. Static review findings corrected: archive/trash dismissal, refresh without remount, deadline event labels, XLSX waits for exact totals. Header search uses a portal to the existing shell slot; fallback search stays inside the component when embedded independently. SVG legacy !important min-width removed so compact charts fit mobile while dense series retain inline scrolling widths. No new product dependencies/API/backend changes.

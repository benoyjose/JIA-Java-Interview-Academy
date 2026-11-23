# Admin web accessibility baseline

This is the initial executable baseline for issue #196, not a claim of full WCAG conformance.

## Implemented in this slice
- Workspace navigation is exposed as a named complementary landmark.
- Skip-to-main-content link is the first interactive element in the authenticated shell and is visible on keyboard focus.
- Main content is programmatically focusable after skip navigation.
- Navigation glyphs and avatar initials are decorative to assistive technology; controls retain accessible names and active-page state.
- High-visibility `:focus-visible` outlines are applied to links, buttons, form controls and tab stops.
- Reduced-motion preferences suppress nonessential animation and transitions.

## Regression checks
- Playwright verifies skip-link visibility/focus, hash navigation, main landmark, named workspace sidebar and current-page navigation semantics.
- Existing role-navigation E2E cases verify permissions do not remove accessible names or expose unauthorized navigation.

## Remaining audit before release
- Run axe or equivalent automated WCAG checks against login, lesson editor, review queue, taxonomy, question bank and users screens.
- Manually verify keyboard-only operation, screen-reader announcements, zoom/reflow at 200–400%, contrast in all themes, validation/error focus, dialog focus trapping/restoration and touch target sizing.
- Record exceptions with rationale and owner; remediate critical/serious findings before release.

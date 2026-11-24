# Accessibility baseline for learner and admin clients

Status: measurable quality baseline and verification checklist for issue #196.

## Required interaction baseline
- All interactive controls have a programmatic name, role and state; icon-only controls expose a meaningful label.
- Keyboard-only users can reach, operate and leave every workflow. Web focus order follows visual/semantic order, focus is visible, and modal focus is contained and restored.
- Android Compose screens use semantic roles, headings, state descriptions and live regions only where they communicate meaningful updates. Do not announce decorative content or every list-item change.
- Text supports system font scaling without clipping or loss of actions. Layouts reflow at narrow widths and landscape orientation.
- Minimum target size: 48 dp Android and 24 CSS px web (prefer 44 CSS px for primary touch actions); spacing prevents adjacent accidental activation.
- Color is not the sole carrier of status; text, icon or shape accompany color. Verify contrast against WCAG 2.2 AA: 4.5:1 normal text, 3:1 large text and essential UI graphics.
- Forms expose labels, required state, inline errors and a reachable error summary after failed submission. Errors preserve user-entered values.
- Loading, empty, offline, success and failure states are explicit, actionable and accessible to assistive technology.
- Authentication, paywall, lesson player, quiz, progress, downloads and account deletion receive priority because they block core user journeys.

## Automated checks
Web/admin:
- Run axe-core against stable page fixtures in Playwright or Vitest where available; fail CI on serious and critical violations.
- Add keyboard interaction tests for navigation, dialogs, lesson editor and validation summary.
- Keep an allowlist with owner, rationale and expiry; no blanket suppression.

Android:
- Add Compose UI tests for semantic labels/roles, focus traversal, state announcements and large-font layouts.
- Run Accessibility Scanner and manual TalkBack verification on representative journeys.
- Avoid assertions tied to localized visible text when a stable semantics identifier is available.

## Manual release checklist
1. TalkBack and VoiceOver: sign in, browse curriculum, open lesson, answer quiz, download/open offline content, review progress, change reminder preferences, delete account.
2. Keyboard: tab/shift-tab, enter/space, escape, arrow-key behavior for composite widgets; no keyboard trap.
3. Text scaling: 200% web zoom and Android font scale 1.3–2.0; all critical actions remain available.
4. Contrast and non-color status check in light and dark themes.
5. Reduced motion and animation safety; no flashing content.
6. Network interruption: accessible error and retry state; focus does not jump unexpectedly.

## Definition of done
A client workflow is not considered accessible merely because automated scans pass. CI checks, assistive-technology walkthrough, documented exceptions and regression tests are required. Track findings as issues with severity, affected journey, reproduction and owner. Fix blocking barriers before release; publish accepted non-blocking exceptions with a remediation date.

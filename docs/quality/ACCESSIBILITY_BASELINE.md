# Accessibility and UI Quality Baseline

This baseline applies to Android learner screens and the web administration portal. It defines acceptance checks; passing this document does not imply the current UI has been audited or fixed.

## Interaction and semantics
- Every actionable control has a meaningful accessible name, role, and state.
- Decorative icons are excluded from accessibility traversal; informative visuals have concise descriptions.
- Focus order follows the visual and task order; keyboard users can reach and operate all web controls without a pointer.
- Focus is visible and is not trapped except inside modal dialogs, where focus is contained and restored on close.
- Touch targets meet platform guidance or have a documented exception with adjacent spacing.

## Text and visual presentation
- Support system font scaling and dynamic text without clipping, overlap, or loss of actions.
- Do not communicate status by color alone; pair color with text, shape, or icon semantics.
- Verify contrast for text, controls, and focus indicators against WCAG 2.2 AA targets.
- Respect reduced-motion preferences and avoid unnecessary animated transitions.

## Screen states
Every primary screen must define loading, empty, success, recoverable error, and offline states. Error messages identify the action and a safe recovery path without exposing backend details.

## Validation
- Compose UI tests assert semantics labels, roles, and state descriptions for key flows.
- Web tests cover keyboard navigation, focus restoration, and accessible form errors.
- Include manual screen-reader and large-font checks in release QA.
- Record accessibility defects with screen, steps, expected behavior, and assistive technology.

## Release gate
Accessibility review is required for authentication, lesson reading, quizzes, progress/bookmarks, and CMS publishing flows. Any exception must be documented with owner, rationale, and remediation date.
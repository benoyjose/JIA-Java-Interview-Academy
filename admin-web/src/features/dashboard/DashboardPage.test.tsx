import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { DashboardPage } from "./DashboardPage";

describe("DashboardPage initial render", () => {
  it("exposes an accessible reporting-period selector and loading status", () => {
    const html = renderToStaticMarkup(<DashboardPage />);
    expect(html).toContain('aria-label="Reporting period"');
    expect(html).toContain('role="status"');
    expect(html).toContain("Loading academy analytics");
    expect(html).toContain("Last 7 days");
    expect(html).toContain("Last 30 days");
    expect(html).toContain("Last 90 days");
  });
});

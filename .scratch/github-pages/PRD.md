# GitHub Pages: do-over and keep it updated

Status: needs-triage
Labels: area:docs, area:ci

## Problem Statement

The Subspace Infinity GitHub Pages site is stale and broken in ways a visitor will notice:

- `docs/index.html` still ships with the original template's placeholder copy (`Your title here`, `A brief description of your site for search engines`, `Information about the author here`).
- The site's Discord invite (`discord.gg/3n7ZvfvD`) does not match the active invite in `README.md` (`discord.gg/tfyWxbK`).
- Stylesheet pulls Google Fonts over plain `http://`, and depends on jQuery 3.4.1 from a CDN to do client-side `#hash` routing for a three-section SPA (Home / About / Contact).
- `_config.yml` at the repo root sets the Jekyll `jekyll-theme-cayman` theme — but the published site is a hand-rolled HTML/CSS/JS page under `docs/`, so the theme directive and the actual page contradict each other. Whatever GitHub Pages publishes today is the result of that ambiguity, not a deliberate design.
- Meanwhile the README, `RELEASE-NOTES.md`, `docs/setup-guide.md`, `docs/quick-reference.md`, `docs/developer-guide.md`, and `CONTRIBUTING.md` have all grown into the real source of truth. The Pages site references none of them.
- There is no GitHub Actions workflow that builds or deploys Pages, so even after a fix the site has no mechanism to stay in sync with the repo.

A new visitor landing on the GitHub Pages URL gets a worse first impression of the project than the README — the opposite of what a project landing page should do.

## Solution

Treat the GitHub Pages site as a thin presentation layer over markdown that already lives in the repo. Two coordinated changes:

1. **Do-over.** Replace the placeholder hand-rolled site with a Jekyll-built site (using the already-configured `jekyll-theme-cayman`, or an equivalent minimal theme) whose content is generated from existing markdown — the README, the setup/quick-reference/developer guides, and the release notes. The site becomes the public-facing index of those documents, not a parallel set of content to maintain.
2. **Keep-it-updated.** Add a `.github/workflows/pages.yml` workflow that builds Jekyll on every push to the default branch and deploys via the official `actions/deploy-pages` action, so the site automatically reflects whatever the repo currently says. Establish the rule that the site does not own primary content — every page either *is* a markdown file from the repo (front-matter included) or links to one — so future doc edits cannot drift away from the site.

Net effect: a visitor opening the GitHub Pages URL sees a current, accurate landing page (project description, screenshots, download link, setup link, release notes) that matches the README, and the site stays in sync without anyone remembering to update a parallel `docs/index.html`.

## User Stories

1. As a first-time visitor arriving from a search engine, I want the GitHub Pages site to clearly explain what Subspace Infinity is, so that I can decide within seconds whether the project is relevant to me.
2. As a first-time visitor, I want the page title and meta description to reflect the actual project, so that browser tabs and search-result snippets are not "Your title here".
3. As a first-time visitor, I want to see screenshots of the game on the landing page, so that I get a visual sense of what I'd be running.
4. As a first-time visitor, I want a prominent "Download" call-to-action that points to the latest itch.io build, so that I can try the game without cloning a repo.
5. As a first-time visitor, I want a working Discord invite link, so that I can reach the community without bouncing off a dead invite.
6. As a returning visitor, I want the Discord link on the site to match the one in the README, so that I'm not confused about which server is canonical.
7. As a developer evaluating the project, I want a one-click path from the landing page to the setup guide, so that I do not have to dig through the GitHub UI to find it.
8. As a developer, I want the setup guide rendered as a Pages article (not a raw GitHub markdown view), so that I get a polished reading experience and can deep-link to a section.
9. As a developer, I want the developer guide (module-writing concepts) reachable from the site nav, so that I can learn how to extend the server before cloning the repo.
10. As a developer, I want a quick-reference page on the site, so that I can copy common Gradle commands without opening a terminal-pager view of `quick-reference.md`.
11. As a community member, I want a release-notes page on the site, so that I can scan recent changes without opening `RELEASE-NOTES.md` in raw form.
12. As a community member, I want each release on the site to be deep-linkable (e.g. `#v1-0-11`), so that I can share a link to "the release where X landed".
13. As a contributor, I want the site to link to `CONTRIBUTING.md`, so that the contribution flow is discoverable from the public landing page.
14. As a contributor, I want the site to link to the GitHub repo, the issue tracker, and the discussions tab, so that I can act on what I read.
15. As a maintainer, I want the site to be built and deployed by a GitHub Actions workflow on every push to the default branch, so that I do not have to remember to redeploy.
16. As a maintainer, I want the workflow to fail loudly if Jekyll build fails, so that broken markdown does not silently publish a broken site.
17. As a maintainer, I want the workflow to use the official `actions/configure-pages` + `actions/deploy-pages` actions, so that the deploy mechanism is the GitHub-recommended path and survives action deprecations.
18. As a maintainer, I want a single rule for content authorship — "the site does not own primary content; it surfaces markdown that lives in the repo" — so that future doc updates land in one place and the site picks them up automatically.
19. As a maintainer, I want the site to display the current release version somewhere visible, sourced from `build.gradle` or the latest git tag, so that the landing page does not advertise an outdated version after a release.
20. As a maintainer, I want the legacy `docs/index.html`, `docs/script.js`, `docs/style.css`, `docs/background.jpg`, and `docs/logo.jpg` either deleted or repurposed under the new build, so that we do not leave orphan template files in the repo that future contributors mistake for the live site.
21. As a maintainer, I want the Jekyll config (`_config.yml`) committed at a known location and minimal, so that swapping themes or adjusting the site title is a one-file change.
22. As a maintainer, I want a local preview command (`bundle exec jekyll serve`, or equivalent) documented in a short README under the site source, so that I can preview changes before pushing.
23. As a maintainer, I want the workflow to be skippable for commits that do not touch site sources (markdown / `_config.yml` / `docs/` / images), so that we do not pay CI minutes on every Java edit.
24. As a maintainer, I want the GitHub Pages "Source" setting moved from "Deploy from a branch" to "GitHub Actions", so that the workflow is the only deploy path and there is no second route that can publish stale content.
25. As a maintainer, I want broken outbound links on the site (404s, dead Discord invites) to be detected automatically on a slow cadence (weekly), so that link rot is caught before users hit it.
26. As an automation reviewer, I want the workflow to run on a non-trivial subset of the repo only (paths-filter), so that the action log shows clearly which pushes were "site changes" vs "code changes".
27. As an accessibility-conscious visitor, I want the site to use a recognisable theme with sensible defaults (heading hierarchy, contrast, mobile responsiveness), so that the page is readable without bespoke CSS maintenance.
28. As a visitor on a slow connection, I want the site to avoid loading multiple unnecessary CDNs (jQuery, normalize.css, Google Fonts), so that the page loads quickly even on mobile data.
29. As a security-conscious visitor, I want all external resources loaded over HTTPS, so that the page does not generate mixed-content warnings.
30. As a maintainer, I want the new site's information architecture (which pages exist, what they link to) committed as part of this PRD's implementation, so that "what should be on the site" is not relitigated every time someone proposes a change.

## Implementation Decisions

**Site framework: Jekyll on `jekyll-theme-cayman`.** It is already partially configured via `_config.yml`, supported natively by GitHub Pages, requires no Node toolchain in CI, and renders markdown directly so the "site doesn't own primary content" rule is easy to enforce. If a richer theme is desired later (e.g. `just-the-docs` for sidebar navigation), that's a one-line `_config.yml` change and out of scope for this PRD.

**Site source location: `docs/`.** Keep using the existing `docs/` directory as the Jekyll site root (matches the repo's current layout, avoids a top-level rename). Move the Jekyll config from `/_config.yml` into `docs/_config.yml` so the entire site lives under one folder.

**Content sourcing rule.** The site contains:
- An `index.md` landing page with project intro, screenshots, download link, Discord link, and a small navigation block.
- Pass-through pages that include or link to `README.md`, `CONTRIBUTING.md`, `RELEASE-NOTES.md`, `docs/setup-guide.md`, `docs/quick-reference.md`, and `docs/developer-guide.md`. Where Jekyll cannot include a file from outside its source root, prefer linking to the rendered GitHub view rather than duplicating content.
- No bespoke HTML/CSS/JS beyond what the theme provides.

**Discord invite consolidation.** A single canonical Discord invite, sourced from the README. The site references the README's invite rather than hardcoding its own.

**Build + deploy pipeline (the "deep module" of this PRD).** A new `.github/workflows/pages.yml` that:
- Triggers on `push` to the default branch (`infinity`), filtered to paths under `docs/**`, `*.md`, `_config.yml`, and the workflow file itself.
- Triggers on `workflow_dispatch` so a maintainer can force a rebuild.
- Uses `actions/checkout`, `actions/configure-pages`, `actions/jekyll-build-pages`, and `actions/deploy-pages` — the official Pages-deploy chain. No third-party deploy actions.
- Runs in a single job, ubuntu-latest, with `permissions: pages: write, id-token: write, contents: read`.
- Concurrency group `pages` with `cancel-in-progress: false` so a fast follow-up push does not abort an in-flight deploy.

**GitHub Pages settings change (one-time, manual).** In repo Settings → Pages, switch "Source" from "Deploy from a branch" to "GitHub Actions". This is a manual step the maintainer performs once; the PRD documents it but no code performs it.

**Cleanup of the old hand-rolled assets.** Delete `docs/index.html`, `docs/script.js`, `docs/style.css`, and the unused jQuery + normalize CDN links. Keep `docs/background.jpg` and `docs/logo.jpg` only if reused by the new site; otherwise delete them too. Net deletion of dead code is preferred over leaving "for-reference" artifacts.

**Release-version display.** The landing page surfaces the latest released version. Approach: a Jekyll `_data` file (`docs/_data/release.yml`) holds the current version string, and the existing release workflow (`release.yml`) is extended to update that file as part of the version bump commit. This avoids a runtime API call from the site and keeps the version display reliable even if the Pages build runs without network access to GitHub's API.

**Link-rot guard (recurring).** A weekly scheduled workflow runs a link checker (`lychee` action) over the published site and opens an issue (or comments on a tracking issue) if dead links are found. This is the "keep-it-updated" mechanism for outbound links specifically — the rest of the content stays fresh because it's sourced from the same markdown the developers edit.

**Information architecture (final pages).**
- `/` (index) — project intro, screenshots, "Download / GitHub / Discord / Setup Guide" CTAs, current version, recent release notes excerpt.
- `/setup/` — renders `docs/setup-guide.md`.
- `/quick-reference/` — renders `docs/quick-reference.md`.
- `/developer-guide/` — renders `docs/developer-guide.md`.
- `/contributing/` — renders `CONTRIBUTING.md`.
- `/releases/` — renders `RELEASE-NOTES.md`.
- All pages share the Cayman theme's default layout; no per-page CSS.

## Testing Decisions

A "good test" here means: verifies behaviour the user (visitor or maintainer) would observe, not implementation details of the Jekyll build.

The two pieces worth testing in CI:

1. **Jekyll build succeeds.** The `pages.yml` workflow's build step *is* the test — if Jekyll fails on a malformed front-matter or broken `include`, the workflow fails and no deploy happens. No additional unit test needed.
2. **Outbound link health.** The weekly `lychee` scheduled run is the regression test for link rot. Failure mode is "issue gets opened", not "deploy is blocked", because dead Discord invites should not block unrelated documentation pushes.

What we are explicitly *not* testing:
- Visual / layout regressions. The theme's defaults are stable and out of our control; pixel-comparison testing is a maintenance burden disproportionate to the risk.
- Markdown rendering correctness per page. If `setup-guide.md` renders cleanly on github.com, it will render cleanly under Jekyll's `kramdown` — no value in asserting that.

Prior art for workflow tests in this repo: `.github/workflows/main.yml` and `.github/workflows/release.yml` are the existing patterns for build + deploy workflows. The new `pages.yml` should mirror their style (Java setup excluded; Pages-specific actions included).

## Out of Scope

- Migrating to a richer documentation framework (MkDocs, Docusaurus, Astro, VitePress). Possible future evolution; not warranted today given the volume of documentation.
- Custom domain, DNS configuration, or HTTPS certificate management. Default `*.github.io` URL is sufficient.
- A search box, full-text search index, or `algolia`-style search integration.
- Internationalisation / translated pages.
- Auto-generated Javadoc hosted on the same site. (If desired, that's a separate PRD with very different mechanics.)
- A blog or news section beyond release notes.
- Comments / Disqus / discussion embeds on pages.
- Analytics (GA, Plausible, etc.).
- Generating per-arena, per-ship, or per-config-tier documentation from Groovy scripts. The site is project-level, not gameplay-data-level.
- Auto-publishing screenshots from the running game; screenshots remain manually curated under `screenshots/`.

## Further Notes

- The existing `_config.yml` at the repo root contains only `theme: jekyll-theme-cayman`. Moving it under `docs/` and expanding it (`title:`, `description:`, `url:`, `repository:`, `defaults:` for layout, plus `include: [README.md]` if README is referenced via Jekyll include) is a small change but worth doing carefully — the `url:` and `baseurl:` fields determine whether internal links resolve correctly under `https://assofohdz.github.io/Subspace-Infinity/`.
- The `infinity` default branch (per `git status` context) means the `pages.yml` `on.push.branches` should list `infinity`, not `main`. Mirror the existing workflows' branch filters.
- The existing `release.yml` already touches `RELEASE-NOTES.md` indirectly via the version bump process described in `CLAUDE.md`. Adding the release-version data file update is a one-line extension to the existing release commit, not a new pipeline.
- The "Pages source = GitHub Actions" setting must be changed by a repo admin in Settings → Pages before the new workflow's deploys take effect. Until then, the old "deploy from branch" mode keeps publishing the legacy `docs/` HTML — so cleanup of old files should land *together* with the source-mode flip, not before.
- This PRD is self-contained and does not depend on any other open PRD in `.scratch/`. The closest neighbour is `.scratch/server-web-interface/PRD.md`, which concerns a runtime web UI for the running server (players, bans, modules) — a different feature with no shared surface area.

## Implementation Status

**Phase 1 (shipped, 2026-05-03).** Single-page player-facing landing committed at [`docs/index.md`](../../docs/index.md), rendered by `jekyll-theme-cayman` against [`docs/_config.yml`](../../docs/_config.yml). Audience scoped to *players* (audience split with the contributor-facing `README.md` was clarified during scoping — see `project_audience_split.md` in user memory). Page blocks: hero (Cayman default), About Subspace Continuum, About Subspace Infinity, honest pre-alpha status, itch.io download CTA, Discord + related-Subspace-communities list, contributor footnote pointing to GitHub. Legacy hand-rolled assets deleted: `docs/index.html`, `docs/script.js`, `docs/style.css`, `docs/logo.jpg`, `docs/background.jpg`, root `/_config.yml`. Discord invite consolidated to README's canonical `discord.gg/FXqNB6N` (PRD's earlier `tfyWxbK` reference was already stale at PRD-authoring time). Pages still deploys via "Deploy from a branch" (`infinity` / `/docs`); no source-mode flip yet.

**Deferred to later phases.**
- `pages.yml` GitHub Actions deploy workflow + the "Pages source = GitHub Actions" repo settings flip.
- Multi-page IA (`/setup/`, `/quick-reference/`, `/developer-guide/`, `/contributing/`, `/releases/`). The existing markdown guides remain in `docs/` as raw files reachable via the README, *not* surfaced on the Pages site (per the audience split — they are contributor docs).
- `lychee` weekly link-rot guard.
- Release-version display (`docs/_data/release.yml` + `release.yml` extension).
- Screenshots block on the landing page (TODO — no curated screenshots yet).

The Phase 1 landing is additive: none of the deferred items require it to be rewritten.

## Comments

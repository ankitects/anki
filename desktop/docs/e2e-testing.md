# End-to-End Testing with Playwright

Playwright drives a real headless Anki instance via its mediasrv HTTP API.
Tests live in `ts/tests/e2e/` and are entirely separate from the Vitest unit tests.

## Prerequisites

Build Anki at least once before running e2e tests:

```shell
just build
```

That's it. `just test-e2e` automatically installs Playwright's Chromium browser
into `out/playwright-browsers/` on the first run (idempotent on subsequent runs).

## Running tests

### Managed mode (CI-style)

Playwright starts and stops a throwaway Anki instance automatically:

```shell
just test-e2e
```

The first run can be slow (~60 s) because Anki must fully initialise before tests start.

### Reuse-server mode (recommended for development)

Start Anki once in a separate terminal, then reuse it across multiple test runs:

```shell
# Terminal 1 — keep running
./run

# Terminal 2 — fast iteration
ANKI_E2E_REUSE_SERVER=1 just test-e2e
```

### Interactive UI mode

Open Playwright's browser UI to inspect each test step with snapshots:

```shell
ANKI_E2E_REUSE_SERVER=1 just test-e2e --ui
```

## Writing tests

Add test files to `ts/tests/e2e/` with the `.test.ts` suffix and import from
`./fixtures` instead of directly from `@playwright/test`:

```typescript
import { expect, test } from "./fixtures";

test("my feature works", async ({ page }) => {
    await page.goto("/some-anki-page");
    await expect(page.locator("#some-element")).toBeVisible();
});
```

`fixtures.ts` re-exports `expect` and a pre-configured `test` object. Add
shared fixtures there as new features require them.

## Calling the Anki API from tests

Anki's `/_anki/` endpoints accept and return protobuf-encoded binary payloads
(`Content-Type: application/binary`). Use `page.request.post` with
`Buffer.from(protoMsg.toBinary())` and decode the response with the matching
generated type from `ts/lib/generated/`.

## Accessing Anki pages

Anki's mediasrv serves the following page families over HTTP:

| URL pattern                  | Description                   |
| ---------------------------- | ----------------------------- |
| `/graphs`                    | Statistics graphs (SvelteKit) |
| `/deck-options/[deckId]`     | Deck options (SvelteKit)      |
| `/congrats`                  | Post-study screen (SvelteKit) |
| `/card-info/[cardId]`        | Card info (SvelteKit)         |
| `/_anki/pages/congrats.html` | Legacy congrats page          |
| `/favicon.ico`               | Mediasrv liveness probe       |

The add-card editor (`/editor/?mode=add`) requires a dedicated HTTP endpoint
that is not yet present in upstream Anki. It will be available once the
editor-as-web-page work (issue #3830) is merged.

## CI

The e2e tests run as part of the `check-linux` job in `.github/workflows/ci.yml`,
after the regular build and test steps. Screenshots and traces from failed runs
are uploaded as artifacts and kept for 7 days.

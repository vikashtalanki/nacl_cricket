# Summary backend (Google Apps Script)

`stats.html`'s **Summary → Save Season** feature persists data to a tiny Google Apps
Script web app so edits survive reloads and are shared across laptops/devices.
`Code.gs` in this folder is the source for that web app. It is **not** executed from
this repo — it lives in Google Apps Script. This copy is kept here for version control.

## Deploy / update

1. Open <https://script.google.com> → **New project** → paste all of `Code.gs`.
2. Edit `setPin()` to your own PIN, then **Run → `setPin`** once and grant permissions.
3. **Deploy → New deployment → Web app**
   - Execute as: **Me**
   - Who has access: **Anyone**
4. Copy the `…/exec` URL and set it as `script_url` near the top of `stats.html`.

When you change `Code.gs`, redeploy with **Deploy → Manage deployments → Edit → New version**
(the `/exec` URL stays the same).

## Data model

Stored under key `summary` as JSON:

```json
{
  "SPRING 2026": {
    "type": "360",
    "divs": [
      { "champ": "SEAMERS", "champScore": "150/4(20)", "champLogo": "https://…",
        "runner": "BULLETS", "runnerScore": "140(20)", "runnerLogo": "https://…",
        "ground": "Anthony 1, Hayward", "date": "2026-02-16" },
      { "...Division B..." }, { "...Division C..." }, { "...Division D..." }
    ]
  }
}
```

On page load `stats.html` calls `loadSummaryFromStore()` which fetches this object
(JSONP) and layers each saved season over the hardcoded tables — replacing a season
that already exists, or inserting a new one at the top.

## Notes

- The PIN is kept server-side (Script Property). The browser prompts for it on first
  save and caches it in `localStorage` (`smPin`). It only guards writes.
- Writes POST `text/plain` JSON (a "simple" request) to avoid the CORS preflight that
  Apps Script cannot answer; the browser can't read the response (that's expected).
- Rankings can later reuse the same endpoint with `key=rankings`.

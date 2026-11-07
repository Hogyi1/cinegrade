# CineGrade: the last stretch

The desktop app is feature complete. What's left is connecting it to the backend (expected in about 2 days), then the nice-to-haves. After that, it's ready to release.

**Who writes what:** `[YOU]` is core logic and wiring. `[AI]` is FXML, CSS and controls, tagged in `AI_GENERATED.md`.

---

## TODO (needed for release)

- [ ] **`UserManager` against the backend [YOU]:** login, logout, register, keep the session, and check it at startup (see *Session* below). `User` gets the tokens. `changeUser` and `logout` already exist, and they stay the single place the active user changes.
- [ ] **Connectivity check [YOU]:** a `BooleanProperty online` that the UI binds to. Check it at startup and after any failed request, and recheck every ~30 s while offline.
- [ ] **The bridge [YOU]:** `CineGradeBridge`, the Java object the WebView page can call (see *Bridge* below).
- [ ] **Share on presets [YOU], menu item [AI]:** a *Share* item in the preset's right-click menu. It sends the preset with the user's token to the API. Disabled when offline or logged out.
- [ ] **Login from the avatar circle [YOU wiring, AI layout]:**
  - Offline: the circle is hidden or greyed out, with an "Offline" tooltip.
  - Online and logged out: a `Login · Register` button where the circle is.
  - Logged in: the circle with the avatar, and a menu with *Logout*.
- [ ] **Socials URL from config:** `SocialsController.SOCIALS_URL` is hard-coded to `http://localhost:4200`. Move it to `AppConfig` next to the API base URL, so the cloud deploy only means a config change.
- [x] `PresetManager` is app level (made in `App`, not in `CatalogScopeFactory`), so the bridge can add presets with no catalog open.
- [x] Socials scene: WebView, logo and Projects button.
- [x] Removed: shader hot reload, the test panel, the old plans.

## Nice to have

- [ ] **Curves, several channels:** master plus R, G and B. They're stored in the existing `curvesJson`, and the shader gets a 256-entry LUT per channel in the UBO.
- [ ] **Color wheels:** lift, gamma and gain, each with hue and saturation (ASC CDL).
- [ ] **HSL sampler:** pick a colour in the viewport, and its HSL band lights up so dragging moves that band's sliders (targeted adjustment).
- [ ] **`MigrationService` for old catalogs:** opening a catalog made by an older schema adds the missing columns and tables instead of failing. Store a `schema_version` in `catalog_info` and run the steps in order.

---

## How the desktop and the backend connect

### Separate projects

The Spring Boot app is **its own project** (its own repo or a sibling folder with its own `pom.xml`). It is never a dependency or module of the desktop app. The two only talk HTTP (REST + JSON). The desktop needs nothing from Spring, and the backend can be deployed and run without JavaFX.

### Session (how long a user stays logged in)

Use two tokens:

| Token | Lifetime | Where it lives |
|---|---|---|
| Access token (JWT) | ~15 min | Memory only, in `UserManager` |
| Refresh token | ~30 days, replaced on every use | App DB, `users` table |

- **At startup:** if a refresh token is stored and we're online, call `POST /auth/refresh`. Success gives a new access token and means logged in. A `401` means it expired or was revoked, so clear it and treat the user as logged out. Offline means keep the stored user, show "Offline", and disable sharing.
- **On requests:** add `Authorization: Bearer <access>`. On a `401`, refresh once and retry once. If it fails again, log out.
- That's why you don't check the token on every app action, only at startup and when the server rejects a request.

### Login (loopback)

1. The circle's *Login* opens the backend's login page in the system browser, with a random `state` and `redirect_uri=http://127.0.0.1:<free port>/callback`.
2. The desktop runs a tiny one-shot HTTP server on that port (`com.sun.net.httpserver.HttpServer`, which is part of the JDK, so no dependency).
3. After login, the backend redirects to the callback with a one-time `code`. The desktop checks `state`, exchanges the code for the tokens (`POST /auth/token`), stores the refresh token, then closes the server.
4. *Register* is the same flow, starting on the register page.

The WebView doesn't need its own login. The page asks the bridge for the token (below), so the desktop is the only place that holds it.

### Bridge (`JSObject`)

```java
webView.getEngine().getLoadWorker().stateProperty().addListener((obs, old, state) -> {
    if (state == Worker.State.SUCCEEDED && isOurOrigin(engine.getLocation())) {
        JSObject window = (JSObject) engine.executeScript("window");
        window.setMember("cinegrade", bridge);   // keep `bridge` in a field: the WebView only holds it weakly
    }
});
```

`CineGradeBridge` is a **small class with only the methods the page may call**. Never hand a manager to JavaScript, because the page can call every public method of whatever object it gets.

| Method | What it does |
|---|---|
| `isDesktop()` | Lets Angular show the desktop-only buttons |
| `getAccessToken()` | Angular uses the desktop's login |
| `downloadPreset(String presetId)` | Java downloads `/api/presets/{id}` itself with `HttpClient`, then validates and saves it through `PresetManager` |
| `isOnline()` / `isLoggedIn()` | For the page's own UI |

Rules:
- **Take only ids from JS, never preset content.** Java fetches the data itself. The page is just another untrusted source, so the preset goes through the same `validate` as a file import.
- Bridge calls arrive on the FX thread, so HTTP runs on `ioPool`, and the result returns through `Platform.runLater`. To tell the page it's done, call `engine.executeScript("window.onCinegrade && window.onCinegrade('presetSaved', '<id>')")`.
- Only attach the bridge when the page is on our origin, so a link to another site can't call it.

### Share

`ShareService.share(Preset, PhotoEntry current)` [YOU] wraps the preset JSON (it already has a UUID, so the server can spot duplicates), the user's token, and, when a photo is open, the before and after JPEGs and a 33³ `.cube` from the existing export and LUT renderers. It sends one multipart `POST /api/presets`. The menu item stays disabled unless `online && loggedIn`.

### Where things are made

| Object | Scope | Why |
|---|---|---|
| `UserManager`, connectivity, `PresetManager`, `CineGradeBridge`, `ApiClient` (one `HttpClient`, base URL, auth header) | App, in `App` | They must work with no catalog open (Socials scene) |
| `ShareService` | App | It only needs `ApiClient` and the renderers. The photo is a parameter |
| `EditingManager` and the rest | Catalog scope | Unchanged |

---

## Before release

- Run the whole flow once on a clean machine: install, login, download a preset from Socials, apply it, share one back.
- `src/test` is still empty. At least test `Preset` JSON round-trips and validation, and `CubeWriter` under a Hungarian locale.

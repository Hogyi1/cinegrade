# CineGrade – Demo Sprint Plan (v3)

> Replaces the 32-session plan in `PLAN.md`. Revised 2026-10-25.
> **Day 0** = today (6 h rehaul). **Days 1–8** = Mon 10-26 → Mon 11-02 (demo, localhost).
> Then about 15 days of documentation, run alongside cloud hosting and bugfixes, before the final defense.

## 0. Verdict on the old plan

**Not viable as written. A cut-down version is.** The summer engine (`C:\github-projects\JNA vs JNI`) makes it easier than v2 assumed.

| Problem in old `PLAN.md` | Why it matters |
|---|---|
| Days 1–2 go to filmstrip filters, presets/history/tags tabs, a floating Task Manager and an LRU cache | None of these are in the thesis abstract. The engine still has to be ported, and grading, LUT export and persistence don't exist yet. |
| A second GLFW context, a std140 UBO, Bradford CAT, clarity/dehaze | Too much for handwritten code in 8 days. One render thread can run viewport, export *and* LUT jobs as queued work. |
| Shaders are `#version 430` but the context is 3.3 | macOS stops at 4.1, and the abstract says *cross-platform*. Use `#version 330 core`. |
| Texture / Clarity / Dehaze | These are spatial operations. **A 3D LUT cannot represent them.** Your headline feature is "profile → .cube", so the grade must be per-pixel only, or the exported LUT won't match the viewport. |
| "Social tab: WebView **or** native JavaFX browser" | The abstract commits to **WebView**. |
| Camera model typed in by hand on upload | The abstract says EXIF is extracted **automatically** from uploaded media. The backend has to do that. |
| Likes / downloads | The abstract says users *rate* posts ("értékelhetik"). Use ratings. |
| 12–16 h days, every day for 8 days | Plan for 12 h. A 16 h day is only for the slip buffer. |

### What the abstract requires (the must-have list)
1. JavaFX desktop app, OpenGL GPU rendering, real-time, non-destructive editing.
2. Custom colour profiles → standard `.cube` 3D LUT at several sizes (17/33/65).
3. Local processing and image export.
4. Angular + Spring Boot community platform, also embedded in the desktop app through **WebView**.
5. Share settings with an interactive **before/after** view; browse and **rate** other users' work.
6. **One-click import** of shared grading settings into the editor.
7. **Automatic EXIF extraction** from uploaded media for categorisation.
8. **Search and filter** LUTs.

### Ownership tags
- **[YOU]**: you write it by hand.
- **[AI]**: Claude generates it (FXML/CSS, Angular, scaffolding, DTOs, DDL, Docker, pom). It gets tagged per `AI_GENERATED.md`.
- **[AI→READ]**: AI-generated, then you read through it and annotate until you can defend every line.

---

## 1. Codebase findings

### Bugs
| # | Where | Bug |
|---|---|---|
| B1 | `ImportManager.java:155` + `:161`, `PhotoManager.java:80` | Every imported photo is added to `allPhotos` **twice**: per batch, then again in `onSucceeded`. |
| B2 | `PhotoManager.java:57-60` | `setAll` runs inside `runLater`, then `allPhotos.stream()` runs immediately while the list is still empty. `lastImportPhotos` is never filled on load. |
| B3 | `PhotoDao.java:65` vs `ImportService.java:178` | The DAO calls `commit()` inside `commitBatch`'s transaction. Photos commit even if metadata and edit rows roll back, so the import isn't atomic. |
| B4 | One shared `Connection` | The import worker toggles `autoCommit` while the FX thread writes ratings on the same connection. |
| B5 | `StorageService.java:151` → `ImportService.java:116` | `atomicMove` returns `null` on failure, which leads to an NPE. Throw instead. |
| B6 | `CatalogManager.java:76` | `deleteCatalog` closes the **global** catalog connection, even when another catalog is open. |
| B7 | `hs_err_pid*.log` (all 10) | Every crash is at **app exit**: `Platform.exit → WinWindow.close` jumps into code from a DLL that has already been unloaded. That's typical of a Windows shell extension hooked in by `FileChooser`/`DirectoryChooser`. It happens after `stop()`, so data is safe. **Low priority; post-demo.** |

### Porting issues in the summer engine
| Where | Issue |
|---|---|
| `App.java` (summer) | `glfwInit`/`glfwCreateWindow` run on the FX thread, while `glfwPollEvents` runs on the render thread. GLFW requires these on **one** thread. Do every GLFW call on the render thread. |
| `vertex.vert`/`fragment.frag` | `#version 430` on a 3.3 core context. Use `#version 330 core`. |
| `LibRawConverter` | `output_color 3` is **WideGamut**, yet it's displayed as sRGB, so colours are wrong. `no_auto_bright 0` means each image gets its own random brightening. Use `output_color 1`, `no_auto_bright 1`. |
| `uploadTexture` | 16-bit RGB rows are `width*6` bytes. Set `glPixelStorei(GL_UNPACK_ALIGNMENT, 2)`, or odd widths will shear. |
| `loadThumbnail` | Rotation is hard-coded to 90° left. Use the EXIF orientation you already read at import. |
| Render loop | Busy loop with `sleep(5)` and volatile flags. Use a `BlockingQueue` of jobs and render on demand. |
| Shutdown | No GL resource cleanup, and `System.exit` in `stop()`. Make the engine `AutoCloseable`. |

Already good, so keep it: rendering into a **viewport-sized FBO** (sampling the full-res texture, so no proxy is needed), the **double PBO**, two alternating `WritableImage`s, the "latest click wins" guard on decode, and LibRaw's `setPixelBytes`, which copies before `clear_mem` (no use-after-free).

### Architecture
- **`CatalogContext`**: see §2. Keep the idea, fix the shape.
- **Classes create their own dependencies**: `ImportManager` creates `new ExifToolService()` (`:91`), `ImportService` creates 3 DAOs + 2 services. These should come in through the constructor.
- **`CatalogManager` (384 lines) and `ImportManager` (402 lines)** each hold 3–4 jobs. See §2 for the seams to cut along.
- **DAOs own transactions.** Add a `TransactionRunner`.
- **Dead code:** `ApplicationManager` (empty), `TransitionFactory` (unused). Delete both.
- **Layering:** `domain/` imports JavaFX, `infra` and even `app`. Fix this post-demo (Day P1), before the documentation diagrams are drawn.
- **No tests** (`src/test` is empty).

---

## 2. Context design – your question answered

> "CatalogContext is bad, but how else can I manage it? Or load everything and pass the catalog to the managers on the fly?"

**Keep the per-catalog context. The idea is right; only its shape is wrong.** A container whose lifetime equals "one open catalog" is what DI frameworks call a *scope* (Spring's request scope and Guice's child injectors are the same pattern).

**"Pass the catalog on the fly" is worse.** Every manager would either hold mutable "current catalog" state, or every method would grow a `Catalog` parameter. Nobody would own the connection's lifetime, and switching catalogs would leave stale lists, half-finished tasks and open statements behind. The scope gives you exactly one place to open and one place to close.

What is actually wrong with `CatalogContext` today:
1. **It builds its own contents** (`new PhotoManager(...)` in the constructor). It is a factory and a holder at once.
2. **Public fields, and controllers receive the whole context.** That makes it a *service locator*: a controller's real dependencies are hidden.
3. **It lives in `domain`, but depends on `app`.**

Fixed shape:
```text
AppScope            (app lifetime)       ThemeManager, ThreadPoolManager, TaskManager, AppDatabase,
                                         CatalogManager, UserManager, SceneManager, RenderEngine (GL thread – one per app)
CatalogScope        (one open catalog)   record: catalog, db, photoManager, importManager, editingManager,
                    implements AutoCloseable          exportManager, thumbnailService ...
CatalogScopeFactory open(Catalog, AppScope) -> CatalogScope   ← the ONLY place with `new` for catalog things
```
- **Close order:** build a small `ResourceStack` (a `Deque<AutoCloseable>`). The factory pushes each resource as it creates it, and `close()` pops in **reverse order** (LIFO): managers first, then DAOs, then the connection. This is how try-with-resources orders things too, so it's easy to explain in the thesis.
- **Controllers get only what they use:** `register(ImportPanelController.class, () -> new ImportPanelController(scope().importManager(), scope().thumbnailService()))`, where `scope()` = `catalogManager.currentScope()`. Your `ControllerFactory` stays as it is.
- **`CatalogDatabase` stops being a singleton.** It becomes an instance owned by the scope, which removes B6 entirely.

### Splitting the two big managers (without blowing them apart)
Use IntelliJ *Refactor → Extract Delegate*. It's mechanical and keeps behaviour identical.
- **`ImportManager`** → `StagingArea` (master/filtered/sorted lists, predicate binding, counts, select/remove: a view model, about 150 lines) + `StagingPipeline` (probe → batch queue → `stageNextBatch` → cancel, about 80 lines). `ImportManager` stays as the facade, plus `importPhotos`. The session temp-directory code disappears on its own once staging thumbnails live in memory.
- **`CatalogManager`** → `ProjectFiles` (validate SQLite header, resolve `.cgproj`, ensure dirs, size calculation: pure filesystem, easy unit tests) + `CatalogScopeFactory` (open/create). `CatalogManager` keeps the recents list, open/create orchestration and `currentScope`.

---

## 3. Storage design (decided)

| Item | Where | Portable? |
|---|---|---|
| Catalog, metadata, edit settings, profiles/presets | `Name.cgproj` (SQLite) | yes |
| **Thumbnails + previews** | **BLOBs in `photo_blobs` inside `<project>/cache/previews.db`** (separate SQLite, `PreviewDatabase`, held by `CatalogScope`) | yes, and deletable: it regenerates |
| Originals, **COPY/MOVE** | `<project>/media/<import>/file`, stored as a **relative path** | yes, because the project folder is the unit |
| Originals, **ADD** | stay where they are, stored as an **absolute path**, flagged `linked` | local only, by design (same as Lightroom) |

- On another machine, linked photos show your existing "missing" badge. A "Relink folder…" action goes in the post-demo bucket.
- Delete `cache/thumbnails`, `temp/imports` and `settings` from `ensureDirectories`.

```text
BlobStore           (interface) put/get/exists/delete(photoId, BlobKind)
  └ SqliteBlobStore(PreviewDatabase)  photo_blobs(photo_id, kind, mime, width, height, data, created_at) PK(photo_id, kind)
                                      no FK: photos lives in the other file → deleting a photo calls BlobStore.delete explicitly
ThumbnailFactory    (interface) byte[] create(PhotoEntry)   – LibRaw embedded thumb → JPEG companion → downscale source
ThumbnailService    (class)     getOrCreate(photo): memory map → BlobStore → factory → BlobStore.put   (read-through)
```
A missing thumbnail then needs no special handling: `getOrCreate` heals it on first access.

---

## 4. Day-by-day sessions

A 12 h day is 4 sessions: **S1 08:00–11:00 · S2 11:30–14:30 · S3 15:30–18:30 · S4 19:00–22:00**.
**S5 22:00–00:30** is overflow only. Every session ends with a commit, and every day ends with its exit check.

---

### DAY 0 – Sun 10-25 (today, 6 h) · Rehaul

**S0.1 (1.5 h) – Scopes [YOU]**
- `CatalogScope` record + `CatalogScopeFactory` + `ResourceStack`. `CatalogDatabase` becomes a per-scope instance (fixes B6).
- Controllers get concrete dependencies. Delete `ApplicationManager` and `TransitionFactory`.
- *Done when:* open catalog A → back to the menu → open B → open A again, and the logs show each connection closing exactly once, in LIFO order.

**S0.2 (1.5 h) – Transactions and bugs [YOU]**
- `TransactionRunner.inTransaction(...)`. Remove `commit/rollback/setAutoCommit` from the DAOs.
- One single-thread **DB write executor** for all catalog writes (fixes B3/B4).
- Fix B1, B2, B5.
- *Done when:* importing 30 photos while clicking ratings gives exactly 30 rows and 30 list entries, with correct ratings after a restart.

**S0.3 (2 h) – Thumbnail BLOBs [YOU] + DDL [AI]**
- [AI ✅ done 10-25] `photo_blobs` DDL in `PreviewDatabase` (`cache/previews.db`), opened/closed by the scope.
- [AI] `photos.storage_mode` (`MANAGED`/`LINKED`). Drop `thumbnail_path`.
- [YOU] `BlobStore`, `ThumbnailFactory`, `ThumbnailService`.
- [YOU] `ImportService`: photo rows in one `inTransaction` per batch on the catalog DB; thumb BLOBs written **after** that commit on the preview DB (two files = no shared transaction; a lost thumb just regenerates). COPY/MOVE store relative paths; ADD stores absolute + `LINKED`.
- [YOU] `ImportStageService` keeps staging thumbnails as `byte[]` in `StageEntry` (no temp files).
- *Done when:* after an import there is no thumbnail file on disk. Deleting `cache/previews.db` + reopen regenerates them. Copying the project folder elsewhere still opens every MANAGED photo.

**S0.4 (1 h) – Extract `StagingArea` from `ImportManager` [YOU, IntelliJ Extract Delegate]**

> If the day overruns, S0.4 moves to Day 4 S4.4. S0.1–S0.3 must finish today.

---

### DAY 1 – Mon 10-26 · ~~Port the summer engine~~ → slipped, replanned below (10-27)

---

### DAY 2 – Tue 10-27 + DAY 3 – Wed 10-28 · GPU workers (replanned 10-27)

**Goal: both GPU workers fully implemented by the end of Wed 10-28.**

**Design (decided 10-27)**
- **Two contexts, one `GpuWorker` class, two instances.** `interactive` = viewport only (latest-wins). `background` = export + thumbnail jobs (FIFO). The viewport never waits for an export. More than 2 contexts gives no speed-up (one GPU), only more VRAM.
- Every worker owns its **own instances** of `ShaderProgram`, `FullscreenQuad`, `ImageTexture`, `FrameBuffer`, `PixelReader`, `BaseBlockUniform`, created on its own thread. GL object IDs only mean something inside their context. Shared between workers: shader source strings, `DecodedImage`, `EditState`, the std140 writer. **No context sharing** (would need fences).
- **No stripes for the demo.** Full-res export in context B is about 0.9 GB VRAM for 45 MP in total. Keep a `vec2 stripe` uniform = `(0, 1)` in the vertex shader, so stripes stay an export-only change later.
- `GL_MAX_TEXTURE_SIZE` and `GL_MAX_VIEWPORT_DIMS` are **queried and logged** at startup, not required to be 32768 (Intel iGPUs report 16384, which still covers every camera). Throw `TEXTURE_TOO_LARGE` per image if a side exceeds it.
- **Decode is not on a GL thread:** `LibrawService.decode` on the cpuPool → `worker.submit(() -> texture.upload(img))` → `memFree`.
- UBO uploaded every draw (a few hundred bytes, free). Tone curves go in a 256×1 `R16F` texture, not a UBO array (std140 pads array elements to 16 bytes).

```
engine/
  RenderEngine      app-scoped, AutoCloseable. glfwInit, creates BOTH hidden windows on its thread,
                    glfwMakeContextCurrent(NULL), hands one window to each worker. close(): background → interactive → glfwTerminate
  GpuWorker         own Thread (not a pool), BlockingQueue<Runnable>, init(): makeContextCurrent + GL.createCapabilities
                    <T> CompletableFuture<T> submit(Callable<T>)          FIFO lane
                    void requestLatest(GradeSettings)                     latest-wins: AtomicReference + 1 wake-up token
  gl/   ShaderProgram, FullscreenQuad, ImageTexture (UNPACK_ALIGNMENT 2), FrameBuffer, PixelReader (double PBO),
        UniformBuffer (binding 0), GlBuffers (not "BufferUtils": clashes with org.lwjgl.BufferUtils)
  grade/   GradeUniforms (EditState → std140 ByteBuffer)
  render/  ViewportRenderer (interactive) · ExportRenderer + ThumbnailRenderer (background)
```

**Tue 10-27 – interactive worker [YOU]**
- S2.1: `RenderEngine` + `GpuWorker` + `gl/` classes; solid-colour shader on screen through `PixelReader` into a test harness `Stage`.
- S2.2: `LibrawService.decode(Path) → DecodedImage` (`output_color 1`, `no_auto_bright 1`, **no** `set_user_mul`). JPEG/PNG via ImageIO. `ImageTexture.upload`.
- S2.3: `ViewportRenderer`: exposes `ReadOnlyObjectProperty<Image> frame` + `resize(w, h)` (× `outputScaleX`, debounced). Orientation as UV rotation in the vertex shader.
- S2.4: UBO + `GradeUniforms` with exposure only, `requestLatest` from a harness slider.
- *Done when:* the harness shows a RAW and a JPEG (portrait + landscape), correctly oriented, exposure slider live, window resize works.

**Wed 10-28 – background worker [YOU]**
- S3.1: Second window/context + `GpuWorker` instance; start/close order logged.
- S3.2: `ThumbnailRenderer`: `half_size=1` decode → 512 px FBO (rotated aspect) → readback → JPEG → `thumbnailService.put` → `photo.thumbnailVersion++` on the FX thread.
- S3.3: `ExportRenderer`: full-res FBO → one-shot `glReadPixels` → `BufferedImage` → ImageIO JPEG, as a `TaskManager` task (`-Xmx2g`).
- S3.4: Export running while dragging the viewport slider; shutdown with pending jobs (futures fail cleanly).
- *Done when:* exporting a RAW while dragging exposure keeps the viewport responsive, the exported JPEG matches the viewport, and closing the app mid-export logs a clean shutdown.

**Next, once the workers are done [YOU]:**
`PhotoLibrary` (view model like `StagingArea`) → `PhotoManager` → filmstrip (horizontal `ListView` + `FilmstripCell`, LRU `Image` cache keyed by `id + thumbnailVersion`) → `EditingManager` (open photo with latest-click-wins, preview vs commit, undo/redo, copy/paste). [AI] `EditSlider`, `EditSection`, right panel built from a `GradeParam` enum, histogram canvas.

---

### ~~DAY 2 – Tue 10-27~~ Colour pipeline (the thesis core) – moved after the GPU workers + EditingManager

**S2.1 – `GradeSettings` model [YOU]**
- An immutable `record` with `withX(...)` copies and a `version` field. Gson to and from JSON. **The same JSON is the platform "recipe".**
- [AI] DDL: `edit_states(photo_id, settings_json, version, updated_at)`.
- Tests [YOU]: round-trip; unknown fields ignored; missing fields default.

**S2.2 – Basic grade shader [YOU]**
- **Working space:** LibRaw outputs sRGB-encoded 16-bit. The shader linearizes → applies operations → encodes to sRGB. This matches the LUT input domain (sRGB in, sRGB out). Write it down; the documentation needs it.
- Order: WB as linear RGB gains → exposure 2^EV → contrast (pivot 0.18) → highlights/shadows/whites/blacks (luminance smoothstep masks) → vibrance/saturation. **Per-pixel only.**

**S2.3 – Sliders → render [AI UI] + [YOU scheduling]**
- Latest-wins coalescing: an `AtomicReference<GradeSettings>` holds the pending settings, and the render thread draws the newest one when it's free. This replaces the volatile flags.

**S2.4 – Tone curves [YOU math] + [AI canvas]**
- Fritsch–Carlson monotone cubic → 256-entry table per channel (master, R, G, B) → lookup texture.
- Tests: monotonic input gives monotonic output; the identity curve is the identity; the endpoints hold.
- *Exit check:* exposure + contrast + S-curve on a RAW in real time.

---

### ~~DAY 3 – Wed 10-28~~ Wheels, LUT, persistence, export – moved (export itself is now in the Wed 10-28 worker plan)

**S3.1 – Colour wheels [YOU math] + [AI control]**: ASC CDL (slope/offset/power + saturation) behind the Lift/Gamma/Gain UI. It's standard, citable and trivially LUT-able.

**S3.2 – 3D LUT generator [YOU]: the headline feature**
- Build an N³ identity lattice as an (N·N)×N float texture and render it through **the same shader** into an `RGBA32F` FBO, as a `RenderEngine` job. Read it back and write `.cube` for N = 17/33/65.
- A colour profile is a named `GradeSettings` in `presets` [AI save/list/apply UI].
- Tests: default settings → identity LUT (error < 1e-4).
- *Done when:* the `.cube` loaded into Resolve or Photoshop matches the viewport.

**S3.3 – Persistence and undo [YOU]**
- Debounced autosave (400 ms `PauseTransition`) through the write executor. Reload when switching photos.
- Undo/redo is a deque of immutable `GradeSettings`. Ctrl+Z / Ctrl+Y.

**S3.4 – Image export [YOU] + [AI panel]**
- Full-resolution render job → JPEG/PNG via ImageIO, as a `TaskManager` task.
- Also produce **before + after JPEGs (1600 px)** for sharing.
- *Exit check:* import → grade → export JPEG + `.cube` → restart → edits are still there.

---

### DAY 4 – Thu 10-29 · Hardening → **desktop feature freeze at 22:00**

- **S4.1** – Before/after in the viewport (`\` toggle) [YOU]. Optional histogram [YOU]: computed on the CPU from the BGRA bytes you already read back (about 30 lines) + [AI] canvas.
- **S4.2** – Tests [AI JUnit 5 pom setup] + [YOU]: in-memory SQLite DAOs, `TransactionRunner` rollback, `ThumbnailService` healing, scope close order, LUT identity, spline, JSON. Aim for 20–30 meaningful tests.
- **S4.3** – Full-scenario bug bash: 50 mixed RAW+JPEG files (COPY) + 10 (ADD) → grade → export → close → **move the project folder** → reopen → MANAGED photos are intact and LINKED ones show as missing (or intact, on the same machine).
- **S4.4** – Slip buffer. If it's free, do the `CatalogManager` → `ProjectFiles` extraction.

---

### DAY 5 – Fri 10-30 · Backend (Spring Boot 3)

- **S5.1 [AI→READ]** Scaffold: Java 21, Postgres in `docker-compose.yml`, **Flyway**, springdoc. Entities: `User`, `Post` (title, description, tags, camera_make/model, lens, iso, focal, avg_rating, rating_count, created_at), `Tag`, `Rating(user, post, stars 1–5)`.
- **S5.2 [AI→READ]** Auth: register/login, BCrypt, JWT through `spring-boot-starter-oauth2-resource-server` + Nimbus with an HMAC key (less code to defend than a hand-written JJWT filter). Public GET, authenticated writes.
- **S5.3 [YOU]**
  - `MediaStorage` interface → `LocalFileStorage` now, S3/Azure Blob later (the modularity story).
  - Multipart `POST /api/posts` with before, after, recipe.json (validated) and `.cube`.
  - **`ExifExtractionService`** using `metadata-extractor` on the before image.
- **S5.4** [YOU] `PostSpecifications` (text, camera, lens, tag, min rating, sort, paging) + [AI→READ] rating, `/recipe`, `/lut` and `/media/**` endpoints.
- *Exit check (Swagger):* register → upload → filter by camera → rate → download.

### DAY 6 – Sat 10-31 · Frontend (Angular), mostly [AI]

- **S6.1** Scaffold, dark tokens, auth + interceptor. **Smoke-test `localhost:4200` in a JavaFX `WebView` immediately.**
- **S6.2** Feed + filter bar + before/after slider cards.
- **S6.3** Detail page (large slider, EXIF panel, star rating, downloads), upload, login/register.
- **S6.4** [YOU] Read-through, CORS, empty/error states.

### DAY 7 – Sun 11-01 · The bridge [YOU]

- **S7.1** Community tab `WebView`. `JSObject.setMember("cinegrade", bridge)` after `SUCCEEDED`. **Keep a strong field reference to `bridge`**: WebView holds it weakly, and it will vanish after GC.
- **S7.2** `applyRecipe(postId)`: Java fetches `/recipe` itself with `HttpClient` (trust boundary, so validate it) → apply to the active photo, pushed to the undo stack.
- **S7.3** `setToken(jwt)` from the frontend after login. The desktop "Share" button sends before/after JPEGs + a 33³ `.cube` + the recipe as a multipart POST.
- **S7.4** End-to-end round trip and fixes.

### DAY 8 – Mon 11-02 · Freeze and demo

- **S8.1** Seed 12–15 posts from different cameras [AI script].
- **S8.2** Demo script + 2 timed rehearsals + a **backup video**. Prepare a demo project with photos already imported.
- **S8.3** `AI_GENERATED.md` audit. Run `git tag demo-1`.
- **S8.4** No new code. Sleep.

---

## 5. Post-demo: documentation (about 15 days), cloud and bugfixes

Mornings go to documentation, afternoons to code.

| Days | Code (afternoons) | Documentation (mornings) – ELTE IK structure |
|---|---|---|
| P1–P2 | Layering cleanup (JavaFX out of `domain`), `CatalogManager` split if still pending, more tests | Introduction, specification, use cases (`USER_STORIES.md`, `*.puml`) |
| P3–P5 | **Cloud:** Dockerfiles, prod compose, Caddy (HTTPS), a VPS or Azure for Students; `S3MediaStorage`; desktop `platform.baseUrl` in `app.config` | User documentation (desktop + web, screenshots) |
| P6–P9 | Demo feedback fixes, "Relink folder…" for LINKED photos, exit crash (B7), cut items | Developer documentation: scopes/DI, storage design, render pipeline, colour math (CDL, Fritsch–Carlson, LUT lattice), bridge, REST API |
| P10–P12 | Spring integration tests (Testcontainers) | Testing chapter |
| P13–P15 | Final cloud rehearsal | Summary, future work, proofreading, `AI_GENERATED.md` appendix |

## 6. Cut list (not before the demo)
Filmstrip filter/sort, desktop ratings/flags/colour labels, tags tab, floating Task Manager window, a second GL context, UBO, texture/clarity/dehaze, Bradford CAT, profile pages/avatars, "featured" feed, likes/download counters.

## 7. Risk register
| Risk | Mitigation |
|---|---|
| The ported engine misbehaves inside the real app (threading) | Day 1 S1.1 test harness first; all GLFW calls on one thread. |
| Engine slips past Day 3 | Day 4 S4.4 buffer. Cut the wheels before cutting the LUT export. |
| WebView can't run the Angular build | Smoke test on Day 6 S6.1. Fallback: a lower ES target in `browserslist`. |
| Exit crash (B7) during the demo | It happens after `stop()`, so no data loss. Don't close the app on stage; the backup video covers the rest. |

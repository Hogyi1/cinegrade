# CineGrade: last desktop week (export, LUT, curves, wheels, presets, history)

## Context

`ExportRenderer.render` is done. One week is left on the desktop app (about 11-02 to 11-09), then the backend starts. Still missing: batch export, LUT export, curves and wheels, presets, and undo/history. `EditingManager` (571 lines) also needs its decoding moved out.

**Who writes what:** `[YOU]` is core logic and wiring, with sketches below. `[AI]` is FXML, CSS, custom controls and DDL, tagged in `AI_GENERATED.md`. UI is listed by hand, with no loops over enums.

---

## Your questions, answered

| Question | Answer |
|---|---|
| Do I need a new texture class and reader for the LUT? | **No.** You don't need an input texture at all: the shader works out each lattice colour from the pixel position. `FrameBuffer` gets an internal-format parameter, and the readback is one `glReadPixels` call. |
| 16F or 32F? | `RGBA32F`. The FBO must be RGBA, because `RGB16F` isn't guaranteed to be renderable. 16F only gives about 11 bits near white, and 32F costs just 4 MB at 65³. |
| Can a LUT be turned back into an EditState? | **No.** A LUT only stores results, and many different edits give the same table. Conversion is one-way, so the preset has to be the source. |
| Share the LUT or the preset? | **The preset is the source, and the LUT is generated from it.** The DB saves only the preset. A LUT is generated when exporting or sharing, so it can't go stale. |
| Does the backend need GPU rendering? | **Not for this.** The desktop uploads `recipe.json`, a 33³ `.cube` and before/after JPEGs (already S7.3 in `SPRINT_PLAN.md`). To apply a look to another photo, the backend does a trilinear LUT lookup on the CPU. It won't include Texture, Clarity or Dehaze. |
| Should `.cgpreset` be a Java object? | **Use JSON (Gson, already in the pom).** Spring can read it. Running `ObjectInputStream` on a file downloaded from someone else can execute code, so don't use it for shared files. |
| Is a for loop right for export? | Yes, and your reasoning holds. Each photo is a separate GPU job, so thumbnail jobs can get in between photos instead of waiting for the whole batch. |

**Lumix Lab caveat:** as far as I remember, camera LUTs expect V-Log input, while ours are sRGB in and sRGB out (not checked). So our LUTs suit editors like Resolve or Photoshop, but a camera would need a V-Log version later. That's an extra input conversion in `latticeColor()`, and it's outside this week.

**Video editor workflow:** V-Log → Rec.709 → our LUT. Rec.709 and sRGB share primaries and differ only in the transfer curve. With "Exclude Exposure & WB" on, the shift should be minimal, because the remaining steps work on encoded values. With them included, shadows can shift a bit. Optional later: a "Rec.709 (gamma 2.4)" LUT option that converts the curve going in and out. Check this on day 2 in Resolve on a Rec.709 timeline.

---

## 1. Decoding: `PhotoDecoder` [YOU]

New class, `infra/services/libraw/PhotoDecoder.java`. It takes `readImage` and `decodeForThumbnail` out of `EditingManager`.

```java
public class PhotoDecoder implements AutoCloseable {   // one per thread, owns one LibrawService
    public DecodedImage decode(PhotoEntry photo, BooleanSupplier cancelled);
    public DecodedImage decode(PhotoEntry photo) { return decode(photo, () -> false); }
}
```

| User | Thread | Cancelled by |
|---|---|---|
| Viewport | `decoderThread` | `stale` |
| Thumbnails | cpuPool | never |
| Export | the export task thread | `isCancelled` |

- There's one class, not two. "Can be stopped" is just the `BooleanSupplier` you pass.
- Move `asShotOf` and `withAsShot` to `WhiteBalanceSolver` (section 3b), because export needs them too.
- **Existing bug this fixes:** `EditingManager:83` creates a LibRaw handle on `decoderThread`, ignores the one passed in, and never closes it. Create the decoder on that thread and close it there in `close()`, and remove the unused `LibrawService` from `CatalogScopeFactory:69`.

---

## 2. Batch export [YOU], panel layout [AI]

**`ExportManager(photoManager, editingManager, catalogTx, editStateDao, exportRenderer, taskManager, exifToolService)`**

```java
public ExportBatchService exportCurrent(ExportOptions o);
public ExportBatchService exportSelected(ExportOptions o);
public ExportBatchService exportAll(ExportOptions o);
public void cancelExport();
```

`PhotoExportOptions` record: `destination, format, quality, resizeLongEdge, keepExif, namingTemplate, conflictMode`.

**`domain/tasks/ExportBatchService`** follows `ThumbnailBatchService`, and `TaskManager.configure` registers it:

```java
try (PhotoDecoder decoder = new PhotoDecoder()) {       // created on this thread, so one LibRaw per thread
    Map<String, EditState> edits = loadSnapshots();     // read once at the start, later edits don't affect the batch
    for (PhotoEntry photo : photos) {
        if (isCancelled()) break;
        DecodedImage decoded = decoder.decode(photo, this::isCancelled);
        Pixels pixels = renderer.render(editFor(photo, decoded), decoded)
            .whenComplete((v, ex) -> memFree(decoded.data()))   // NOT in a finally
            .get().orElseThrow();
        write(pixels, photo);                           // one broken photo logs a warning, the batch continues
        updateProgress(...);
    }
}
```

**Things that will bite:**
1. **Native memory:** `cancel()` interrupts `.get()` while the GPU may still be reading `decoded`. If you free it in a `finally`, the JVM can crash. Free it in `whenComplete`.
2. **As-shot white balance:** photos that were pasted onto or never opened have `asShot = 0`. Apply `withAsShot(edit, asShotOf(decoded))` as `EditingManager:429` does, and use `new EditState(id)` when there's no DB row.
3. **Eye toggles:** split `effective()` so export uses the bypass part but ignores before/after. Otherwise pressing `\` during an export writes originals.
4. **Partial files:** write to `name.jpg.part`, then move it into place. An interrupt can otherwise leave half a JPEG.
5. **Name clashes:** a RAW+JPEG pair both produce `IMG_1.jpg`, so always go through `conflictMode`.
6. **VRAM:** the FBO, texture and both PBOs stay at full size after an export. Add `ExportRenderer.release()`, which resizes them to 1×1, and call it when the batch ends.
7. **Encoding:** make the raster-wrapping part of `ThumbnailFactory.fromPixels` a public `toBufferedImage(bgra, w, h)` and reuse it. It doesn't copy and has the right row order.

**Small `ExportRenderer` cleanup:** the Javadoc still mentions thumbnails and `MAX_EDGE`, and the `close()` log says "Thumbnail renderer".

**Panel:**
- **Wire:** current, selected and all scopes, format, quality, resize, EXIF, destination, naming, conflict, progress, cancel.
- **Hide for now [AI]:** By tag (no tags exist), Color Space (sRGB only), the export preset combo.
- **Add later if time allows:** Visible and Last import. They need new getters on `PhotoManager`.

---

## 3. LUT export [YOU], LUT tab wiring [YOU]

**`fragment.frag`**, using a width of N and height of N²:

```glsl
uniform int lutSize;   // 0 = normal photo
vec3 latticeColor() {
    ivec2 p = ivec2(floor(gl_FragCoord.xy));
    return vec3(p.x, p.y % lutSize, p.y / lutSize) / float(lutSize - 1);
}
// in main():
vec3 source = lutSize > 0 ? latticeColor() : texture(texImage, uv).rgb;
```

With this layout, reading the pixels row by row **is already `.cube` order** (R fastest, B slowest), so nothing needs re-sorting.

**`ExportRenderer.renderLut`:**

```java
public CompletableFuture<float[]> renderLut(EditState lutState, int size) {
    // lutFbo: its own FrameBuffer(1, 1, GL_RGBA32F), resized to (size, size * size)
    // bind, use shader, upload block, linearInput = 0, lutSize = size, draw
    // glReadPixels(0, 0, size, size * size, GL_RGB, GL_FLOAT, floatBuffer)
    // finally: lutSize = 0
}
```

**Things that will bite:**
1. **Reset `lutSize` to 0 in a `finally`.** `ThumbnailRenderer` and `ExportRenderer` share the background worker's one shader, so otherwise the next thumbnail comes out as a rainbow.
2. **Spatial settings must be zero.** Texture, Clarity and Dehaze are already in the shader, and they'd blur between neighbouring lattice cells and produce garbage. Add `GradeParam.isSpatial()` and zero those params before rendering.
3. **`Locale.ROOT` in `String.format`.** On a Hungarian Windows, the default locale writes `0,123456`, which breaks the `.cube` file.
4. **"Exclude Exposure & WB":** set exposure to 0, temperature to neutral, tint to as-shot, offset to 0. Keep the source photo's as-shot values, so the LUT stores the shift from neutral and not a fixed Kelvin.

**`infra/utils/lut/CubeWriter`:** writes `TITLE`, `LUT_3D_SIZE`, `DOMAIN_MIN`, `DOMAIN_MAX`, then one `%.6f %.6f %.6f` line per entry.

**`ExportManager.exportLut(EditState source, LutOptions options)`:** the tab's checkboxes zero the excluded groups, then it renders and writes on ioPool.

**[AI]** Change the LUT tab's notice so it also lists Texture, Clarity and Dehaze.

---

## 3b. Eyedropper and Auto white balance [YOU]

Rename `WhiteBalancer` to `engine/grade/WhiteBalanceSolver`, which both features then share. This fills the empty `EditingManager.autoWhiteBalance(UV)`.

```java
public static Optional<Result> fromCamera(float[] camMul, float[] preMul);    // today's estimate, neutral = 5500 K
public static Optional<Result> fromSample(float[] linearRgb, float neutralKelvin, float asShotTint);
    // m[i] = 1 / sample[i], then the same solve; returned tint = solved tint + asShotTint
private static Optional<Result> solve(double[] m, double neutralKelvin);      // gains(kelvin, neutralKelvin)
```

`ViewportRenderer.sample(EditState defaults, ViewTransform view)`, on the interactive worker, because it already holds the texture:
1. Render into a 5×5 `RGBA32F` FBO with `editState.defaults()`, so no current grade or white balance affects the sample.
2. **Eyedropper:** zoom = `5 / imageWidth`, centred on `ViewportNavigator.toTextureUV`. **Auto:** `ViewTransform.FIT`, which gives a grey-world average.
3. Average the pixels, convert sRGB to linear, then call `fromSample`.

Rules:
- Reject samples with a clipped or near-black channel, with a notice like "Pick another spot".
- RAW: set `temperature` and `tint`.
- JPEG/TIFF: set `temperatureOffset = 100 * toSliderPosition(kelvin)`, and keep temperature at 5500.
- Save it through `applySettings`, so it's one undoable step.
- The shader's uv zoom is the same on both axes, so on a landscape photo the sample covers about 5×3 source pixels. That's fine.

UI: an eyedropper button and an Auto button in the white balance row [AI layout, YOU wiring].

**Test:** a grey patch reads neutral after the click, on both a 3200 K and a 5500 K photo.

---

## 4. Curves [YOU math], curve control [AI]

- **Data:** master, R, G and B point lists, stored in the existing `curvesJson` field and `curves_json` column, so no schema change. The value is null when there's no curve.
- **Spline:** Fritsch-Carlson monotone cubic, sampled to 256 entries per channel. It's monotone, so the curve can't overshoot and invert tones.
- **To the GPU:** add `vec4 curve[256]` to the **existing UBO** (4 KB, under the guaranteed 16 KB minimum). `BaseBlockUniform.convertTo` fills it. No new texture class, and all three renderers get it automatically.
- **Shader:** `applyCurves(c)` looks up the two nearest entries and mixes them. It runs after `applyTones`, in display space.
- **Bypass:** add `Section.CURVES`. `effective()` needs an explicit curves reset, because curves aren't a `GradeParam`.
- **Pane:** re-add the Curves pane removed on 11-01, with a canvas control and a channel selector.

## 5. Color wheels [YOU math], wheel control [AI]

- **Params:** `LIFT`, `GAMMA` and `GAIN` already exist. Add `LIFT_HUE`, `LIFT_SAT`, `GAMMA_HUE`, `GAMMA_SAT`, `GAIN_HUE` and `GAIN_SAT` as `GradeParam`s, so they work with presets, history and bypass without extra code. Storage is `float[9]` in `EditState`.
- **To the GPU:** Java converts hue, saturation and luminance to RGB, then sends 3 `vec4`s in the UBO.
- **Shader:** ASC CDL (slope, offset, power), as `SPRINT_PLAN.md` S3.1 decided. It runs after `applyCurves`, before vibrance.
- **Side effect:** this needs a new `wheels` column, so existing catalogs won't have it and need recreating.

---

## 6. Presets [YOU], dialog and list UI [AI]

**The checkboxes decide what gets stored, so unchecked values simply aren't in the preset:**

```java
public class Preset {
    int formatVersion = 1;
    long id;                          // local DB id, not in the file
    String name, author, description, category;
    Instant createdAt, updatedAt;
    Map<String, Float> values;        // GradeParam name -> value, ONLY the checked ones
    WhiteBalanceType whiteBalance;    // null if white balance is unchecked
    Curves curves;                    // null if unchecked
    void applyTo(EditState target);   // goes through GradeParam.write
}
```

"What else" beyond author, name and createdAt: `formatVersion` (for loading old files), `updatedAt` (presets can be modified), `description` and `category` (the column exists, and search needs them). A preview image can come with the share flow.

It never stores `photoId`, `asShot*`, `lutPath` or EditState's `updatedAt`, because those belong to one photo.

**Storage:**
- **Table:** use the existing **`global_presets`** in the app DB, so presets work in every catalog, not one catalog each.
- **No schema change:** the whole preset goes into `edit_parameters_json`, and `name`, `category` and `created_at` already exist.
- **DAO:** move `PresetDao` from `dao/catalog` to `dao/app`, accessing the DB the way `UserDao` does.
- **Leftover:** the catalog's `presets` table stays unused.

**`PresetManager`** sits at app level, next to `CatalogManager`: `list`, `save`, `update`, `delete`, `importFile(File)`, `exportFile(Preset, File)`.

**Opening a file works like `CatalogManager.openProject`:** check the file, show a confirm dialog with name, author and what's included, then save.
- Files from other people are untrusted, so clamp values to `GradeParam` min and max, and ignore unknown names.
- Use the existing `LUT_FILE_INVALID` style for a new `ErrorCode`.

**`EditingManager.applyPreset(Preset)`:**
- Works on the current photo and the multi-selection.
- **Unlike `pasteSettings`, it must read each photo's edit from the DB first.** A preset is partial, so a blind write would wipe the unchecked settings.
- If the preset has `whiteBalance = AS_SHOT`, call `applyWhiteBalance`, and keep the target's own as-shot values.

**Save dialog [AI layout, YOU wiring]:** `createPresetBtn` opens it, with name, author (prefilled from `UserManager.getUsername()`, editable), description, and checkboxes listed by hand:
- White Balance and Exposure, **unchecked by default**, because they depend on the scene
- Contrast, Highlights, Shadows, Whites, Blacks
- Texture, Clarity, Dehaze
- Vibrance, Saturation
- HSL: a parent checkbox plus Hue, Sat and Lum children
- Curves
- Wheels

**Left panel [AI]:** replace the fake "Kodak Portra" rows with real rows and a right-click menu: Update with current, Rename, Delete, Export `.cgpreset`, Export `.cube`.

---

## 7. History [YOU], notice label and list [AI]

```java
public record HistoryEntry(String description, Set<String> photoIds, Runnable undo, Runnable redo) {}

public class HistoryManager {   // one per CatalogScope, not persisted
    private final LinkedList<HistoryEntry> undoStack, redoStack;   // max 100, removeFirst() drops the oldest
    private boolean replaying;
    public void record(HistoryEntry e);       // ignored while replaying; clears redoStack
    public void undo(); public void redo();   // set replaying = true around the Runnable
    public void forgetPhoto(String photoId);  // drops entries touching a removed photo
    public ReadOnlyStringProperty noticeProperty();
}
```

`PhotoManager` and `EditingManager` take it in their constructors and call `record`. Everything runs on the FX thread.

**You need `redo` as well as `undo`, and the `replaying` flag is essential.** Without the flag, undo calls `updateRating`, which records a new entry, and Ctrl+Z just flips back and forth forever.

**Things that will bite:**
1. **Slider "before" value:** by the time `commit` runs, `preview` has already changed the edit. Keep a `lastCommitted` snapshot in `EditingManager`, and update it on photo load, on commit and in `applyAsShot`.
2. **Use whole-state snapshots for edits.** One `restoreEdit(photo, EditState)` covers sliders, white balance, paste, reset and presets, and new curves or wheels work automatically. If the photo isn't open, it saves to the DB and updates the thumbnail.
3. **Paste and presets on non-open photos:** read their "before" state from the DB first, because `pasteSettings` doesn't today.
4. **Multi-photo rating, label and like:** capture a `Map<PhotoEntry, oldValue>`, because the photos may differ. Record the entry in `thenRun`, after the DB write succeeded.

| Undoable | Not undoable |
|---|---|
| Slider commit, WB preset, paste, reset, apply preset, rating, label, like, comment | Delete from disk, remove from catalog, import |

**One exception to "every action has an opposite":** a deleted file is gone, and removing from the catalog cascades away the edit and metadata. Both of those call `forgetPhoto` instead, and keep their confirm dialog.

**UI:**
- Ctrl+Z and Ctrl+Y in `InputListener` (neither is bound yet) [YOU]
- A label on the viewport bound to `noticeProperty`, fading after 2 seconds, e.g. "Exposure +0.50" or "Undo: Rating" [AI]
- A real list in `historyStepsContainer` [AI]
- Hide the `takeSnapshotBtn` button [AI]

The `edit_history` table stays unused.

---

## Week order

Curves and wheels come before presets, so `Preset` is only designed once. History comes after presets, so it covers all of them.

| Day | Work | Done when |
|---|---|---|
| 1 | Commit the current work. `PhotoDecoder`, batch export, Images tab | 20 mixed photos export while dragging a slider, and cancel works |
| 2 | `renderLut`, `CubeWriter`, LUT tab, then eyedropper and Auto WB (same float FBO) | Identity test passes, the `.cube` matches the viewport in Resolve or Photoshop, and a grey patch goes neutral |
| 3 | Curves | S-curve matches in viewport, export and LUT |
| 4 | Wheels | Same check |
| 5 | Presets, dialog, `.cgpreset` | Partial preset keeps the unchecked values, and a file round-trip works |
| 6 | History and notice | Undo and redo work for each undoable action, the 101st entry drops the oldest |
| 7 | Bug bash and tests | Full flow works |

**If time runs short, cut in this order:** history list panel → Visible and Last import scopes → EXIF copy → wheels. Keep the LUT export and curves.

**After this week:** the backend, using the upload described in the first table.

## Todo

- [ ] **`RelinkManager` [YOU], relink action [AI]:** right-click a missing photo, then Relink. A `FileChooser` opens with the initial file name set to the missing file's exact name. When a file is picked, every other missing photo whose file name is in that folder is relinked too. Update the path in the DB, clear `missingPhoto` and bump the thumbnail version.
- [ ] **`PresetManager` [YOU]:** section 6 (create, apply, partial presets, `.cgpreset` import and export).
- [ ] **Social media bridge [YOU]:** the upload from the desktop to the backend (`recipe.json`, the 33³ `.cube`, before and after JPEGs), S7.3 and DAY 7 in `SPRINT_PLAN.md`.
- [ ] **Skip decoding missing photos [YOU]:** `EditingManager.showOnGpu` still tries to decode a missing original, so selecting one logs an error and shows a toast every time. Return early when `photo.isMissingPhoto()` (the viewport already shows the thumbnail and a Missing badge).

---

## Verification

**Tests:**
- Default `EditState` gives an identity LUT. Run at 17, 33 and 65 and assert a tiny error, then tighten the limit once you see the real sRGB round-trip error.
- `Preset` JSON round-trip, clamping and unknown names
- Spline is monotone and identity at default
- `HistoryManager` cap and replay
- `CubeWriter` under a Hungarian locale

**By hand:**
- Exported JPEG matches the viewport
- LUT applied in Resolve or Photoshop matches a render with no spatial settings
- Thumbnails stay normal after a LUT export
- Closing the app mid-export shuts down cleanly with no JVM crash

**Note:** `src/test` is empty, and `mvn` isn't on PATH on this machine, so the build goes through IntelliJ or needs Maven added.

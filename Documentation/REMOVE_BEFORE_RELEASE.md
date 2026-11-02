<!-- [AI-GENERATED] -->
# Remove before release

Dev-only code that must not ship. Every piece is marked in the source with
`DEV: ... see Documentation/REMOVE_BEFORE_RELEASE.md`, so a search for `REMOVE_BEFORE_RELEASE` finds them all.

## Shader hot reload (added 2026-10-31)

Saving `src/main/resources/shaders/fragment.frag` or `vertex.vert` while the app runs from the IDE rebuilds the
shader on both GPU workers and redraws the viewport. A shader that does not compile is only logged.

| File | What to remove |
| :--- | :--- |
| `src/main/java/hu/elte/ik/thesis/cinegrade/engine/ShaderHotReload.java` | the whole file |
| `src/main/java/hu/elte/ik/thesis/cinegrade/app/main/App.java` | the `resources.push(new ...ShaderHotReload(...))` line in `createDatabaseManagers` (and its comment) |
| `src/main/java/hu/elte/ik/thesis/cinegrade/engine/GpuWorker.java` | `reloadShader(...)` |
| `src/main/java/hu/elte/ik/thesis/cinegrade/engine/gl/ShaderProgram.java` | `reload(...)` and `compileSource(...)` |
| `src/main/java/hu/elte/ik/thesis/cinegrade/engine/renderer/ViewportRenderer.java` | `rerender()` |
| `AI_GENERATED.md` | the rows of the methods above |
| `Documentation/REMOVE_BEFORE_RELEASE.md` | this section (or the whole file if it is the last one) |

After removing: `mvn compile` must still pass, nothing else calls these.

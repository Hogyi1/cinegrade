<!-- [AI-GENERATED] -->
# CineGrade

A desktop photo grading app (ELTE IK thesis). You can do non-destructive editing on the GPU through OpenGL, export to JPEG/TIFF and to `.cube` 3D LUTs, and save looks as presets. The community platform, a separate Angular and Spring Boot project, is built into the app through a WebView.

## Requirements

- JDK 21 or newer
- Maven
- A GPU that supports OpenGL 3.3

## Running

```
mvn javafx:run
```

The main class is `hu.elte.ik.thesis.cinegrade.app.main.Launcher`. App data (logs, the app DB) is in `~/.cinegrade`.

## Key bindings

| Keys | Action | Class |
| :--- | :--- | :--- |
| `Ctrl+Tab` | Next panel (Import → Develop → Export) | `NavigationController` |
| `Ctrl+I` | Import panel | `NavigationController` |
| `Ctrl+D` | Develop panel | `NavigationController` |
| `Ctrl+E` / `Ctrl+M` | Export panel | `NavigationController` |
| `Alt+Enter` | Import the selected photos | `ImportPanelController` |
| `Ctrl+A` | Select all staged photos | `ImportPanelController` |
| `Ctrl+Shift+A` | Deselect all staged photos | `ImportPanelController` |
| `Ctrl+C` | Copy edit settings | `DevelopmentPanelController` |
| `Ctrl+V` | Paste edit settings | `DevelopmentPanelController` |
| `Ctrl+Shift+R` | Reset photo to defaults | `DevelopmentPanelController` |
| `Ctrl+Shift+S` | Save the current edit as a preset | `DevelopmentPanelController` |
| `Ctrl+Shift+B` | Before / after | `DevelopmentPanelController` |
| `Ctrl+Z` | Undo | `DevelopmentPanelController` |
| `Ctrl+Y` / `Ctrl+Shift+Z` | Redo | `DevelopmentPanelController` |
| `Tab` | Cycle zoom: Fit → 150% → 200% | `DevelopViewportController` |
| `+` / `Numpad +` | Zoom in one step (+0.25) | `DevelopViewportController` |
| `−` / `Numpad −` | Zoom out one step | `DevelopViewportController` |
| `Esc` | Cancel the white balance picker | `DevelopViewportController` |
| `0–5` / `Numpad 0–5` | Set rating | `DevelopFilmstripController` |
| `Q` | Red label | `DevelopFilmstripController` |
| `W` | Yellow label | `DevelopFilmstripController` |
| `E` | Green label | `DevelopFilmstripController` |
| `R` | Blue label | `DevelopFilmstripController` |
| `T` | Clear label | `DevelopFilmstripController` |
| `Ctrl+Shift+C` | Edit comment | `DevelopFilmstripController` |
| `Ctrl+L` | Like / unlike | `DevelopFilmstripController` |
| `Delete` | Remove from catalog | `DevelopFilmstripController` |
| `Shift+Delete` | Delete from disk | `DevelopFilmstripController` |
| `Ctrl+Alt+L` | Toggle the liked filter | `DevelopFilmstripController` |
| `Ctrl+Alt+C` | Toggle the commented filter | `DevelopFilmstripController` |
| `Ctrl+Alt+E` | Toggle the edited filter | `DevelopFilmstripController` |

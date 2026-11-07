<!-- [AI-GENERATED] -->
# CineGrade

A desktop photo grading app (ELTE IK thesis). You can do non-destructive editing on the GPU through OpenGL, export to JPEG/TIFF and to `.cube` 3D LUTs, and save looks as presets. The community platform, a separate Angular and Spring Boot project, is built into the app through a WebView.

## Szakdolgozat

**GPU-gyorsított színkorrekciós asztali alkalmazás közösségi platformmal**

A szakdolgozatom célja egy komplex, cross-platform asztali digitális színkorrekciós alkalmazás és közösségi platform fejlesztése. A szoftver JavaFX alapokra épül, és egy OpenGL-alapú, GPU-gyorsított renderelést alkalmaz, amely valós idejű, nem destruktív képszerkesztési élményt biztosít egy professzionális felületen. A rendszer lehetővé teszi az egyedi színprofilok létrehozását, amelyekből különböző precizitású szabványos 3D LUT (.cube) fájlok generálhatók. A felhasználók lokálisan végezhetik el a képek feldolgozását és exportálását.

Az alkalmazás részeként egy webalapú közösségi platform is készül, amely Angular frontenddel és Spring Boot alapú backenddel valósul meg, és JavaFX WebView segítségével az asztali alkalmazásba is integrálásra kerül, ezáltal a közösségi modul a böngészőből és az alkalmazáson belül is elérhetőek lesznek. A felhasználók megoszthatják beállításaikat egy interaktív „Előtte/Utána” nézetben, valamint mások munkáit böngészhetik és értékelhetik. A rendszer támogatja a színkorrekciós beállítások egy-kattintásos importálását, valamint a feltöltött médiákból automatikusan kinyeri a releváns EXIF metaadatokat a pontosabb kategorizálás érdekében. A felhasználók a LUT-ok között különböző beállítások alapján kereshetnek és szűrhetnek, ezzel javítva a felhasználói élményt.

## Design

The UI designs are in Figma: [CineGrade designs](https://www.figma.com/design/CxwyS1wmSNo4WbsDM1xqLJ/CineGrade?node-id=153-103)

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

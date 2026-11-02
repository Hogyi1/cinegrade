## Key bindings

| KeyCode | Action | Class |
| :--- | :--- | :--- |
| `Ctrl+Tab` | Next panel (Import → Develop → Export) | `NavigationController` |
| `Alt+Enter` | Import the selected photos | `ImportPanelController` |
| `Ctrl+A` | Select all staged photos | `ImportPanelController` |
| `Ctrl+Shift+A` | Deselect all staged photos | `ImportPanelController` |
| `Ctrl+C` | Copy edit settings | `DevelopmentPanelController` |
| `Ctrl+V` | Paste edit settings | `DevelopmentPanelController` |
| `Ctrl+Shift+R` | Reset photo to defaults | `DevelopmentPanelController` |
| `Tab` | Cycle zoom: Fit → 150% → 200% | `DevelopViewportController` |
| `+` / `Numpad +` | Zoom slider up one step (+0.25) | `DevelopViewportController` |
| `−` / `Numpad −` | Zoom slider down one step | `DevelopViewportController` |
| `0–5` / `Numpad 0–5` | Set rating | `DevelopFilmstripController` |
| `Q` | Red label | `DevelopFilmstripController` |
| `W` | Yellow label | `DevelopFilmstripController` |
| `E` | Green label | `DevelopFilmstripController` |
| `R` | Blue label | `DevelopFilmstripController` |
| `T` | Clear label | `DevelopFilmstripController` |
| `Ctrl+Alt+C` | Edit comment | `DevelopFilmstripController` |
| `Ctrl+L` | Like / unlike | `DevelopFilmstripController` |
| `Delete` | Remove from catalog | `DevelopFilmstripController` |
| `Shift+Delete` | Delete from disk | `DevelopFilmstripController` |
| `Ctrl+Alt+L` | Toggle the liked filter | `DevelopFilmstripController` |
| `Ctrl+Alt+C` | Toggle the commented filter (never fires, see below) | `DevelopFilmstripController` |
| `Ctrl+Alt+E` | Toggle the edited filter | `DevelopFilmstripController` |
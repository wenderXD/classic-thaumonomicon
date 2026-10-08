# Classic Thaumonomicon

A client-side addon for [Thaumaturge](https://github.com/Leclowndu93150/Thaumaturge) (Minecraft 26.1.2, NeoForge) that gives the Thaumonomicon's research map its Thaumcraft 4 look:

- a book-sized pane with a carved, rune-cut wooden frame, instead of a brass frame round the whole screen
- a violet nebula behind the tree that drifts slower than the tree as you drag
- category tabs down the side of the pane, and a search tab at the bottom of the left column
- research on stone plates: square for a category's main line, hexagonal for side research, round for milestones, parchment for hidden discoveries, and corner studs on special entries
- tendrils between entries: dark once done, green and waving when an entry is next, blue when it is further off
- research you can start next pulses, research out of reach is dim and its name is written in the enchanting-table script
- a dark warp haze under forbidden research, and a sparkle on new research and new pages

Only the look changes. Visibility, unlocking, the clicks and the packets all follow Thaumaturge's own rules, and an entry's pages are still Thaumaturge's book screen.

## Settings

Mods → Classic Thaumonomicon → Config, or `config/classic_thaumonomicon-client.toml`:

- `enabled` (default on): turn the classic map on or off without removing the mod.
- `classicBackgrounds` (default on): one nebula for every Thaumaturge category, as in Thaumcraft 4. Eldritch and other mods' categories always keep their own backgrounds.

## Building

Java 25 (Gradle downloads it if needed):

```powershell
.\gradlew.bat build              # build/libs/classic-thaumonomicon-<version>.jar
.\gradlew.bat runClient          # dev client with Thaumaturge, Curios and Lithostitched
.\gradlew.bat runBookVisualCheck # screenshots the book into runs/book-check/screenshots, then quits
```

Thaumaturge is compiled against and run from its CurseForge release (`curse.maven:thaumaturge-1628024`). To update it, change `thaumaturge_curse_file` in `gradle.properties`.

## Credits

- **Thaumaturge** by Leclowndu93150 and its contributors. This addon reads its API and reuses its search icons at runtime, and ships no Thaumaturge code. The only Thaumaturge assets it carries are the Thaumonomicon and the "TT" logo inside its own icon (see below).
- **Frame, plates, tabs, nebula and sparkle** are redrawn art from Thaumaturge's Legacy by wenderXD, made by its generators (`art/gui/thaumonomicon.py`, `art/fx/particles.py`). Each source file is recorded as original in that project's `tools/asset_provenance.csv`:
  - `book_frame.png` ← `textures/gui/gui_research.png`
  - `book_nebula.png` ← `textures/gui/gui_researchback.png`
  - `sparkle.png` ← row 5 of `textures/misc/particles.png`
- **Warp haze** (`forbidden.png`) is drawn by this addon's `art/forbidden_haze.py`. The redrawn tainted-node frame Thaumcraft 4 used for it is a small core that hides entirely under a plate, so the haze is generated fresh: run `python art/forbidden_haze.py` to rebuild it.
- **Mod icon** (`logo.png`) is put together by `art/mod_icon.py` from the frame, nebula and sparkle above, plus Thaumaturge's Thaumonomicon item and its "TT" logo, both read from the Thaumaturge jar.
- Original Thaumcraft and its Thaumonomicon design by Azanor. No Thaumcraft code or textures are included.

# Classic Thaumonomicon

A client-side addon for [Thaumaturge](https://github.com/Leclowndu93150/Thaumaturge) (Minecraft 1.21.1, NeoForge). It makes the Thaumonomicon's research map look like it did in Thaumcraft 4:

- a book-sized pane with a carved wooden frame, instead of a brass frame around the whole screen
- a violet nebula behind the tree that scrolls slower than the tree when you drag
- category tabs down the side of the pane, with a search tab at the bottom of the left column
- research on stone plates: square for a category's main line, hexagonal for side research, round for milestones, parchment for hidden discoveries, and corner studs on special entries
- tendrils between entries: dark once done, green and waving when an entry is next, blue when it is further off
- research you can start next pulses, and research out of reach is dim with its name in the enchanting table script
- a dark warp haze under forbidden research, and a sparkle on new research and new pages

Only the look changes. Visibility, unlocking, clicks and packets follow Thaumaturge's rules, and an entry's pages are still Thaumaturge's book screen.

This is the 1.21.1 branch. The `main` branch has the 26.1.2 version.

## Settings

In game: Mods > Classic Thaumonomicon > Config. Or edit `config/classic_thaumonomicon-client.toml`.

- `enabled` (default on): turns the classic map on or off without removing the mod.
- `classicBackgrounds` (default on): one nebula for every Thaumaturge category, as in Thaumcraft 4. Eldritch and other mods' categories always keep their own backgrounds.

## Building

Thaumaturge's 1.21.1 branch has no published build yet, so the addon compiles against a local one. Build it once and copy the jar into `libs/`:

```sh
git clone -b 1.21.1 https://github.com/Leclowndu93150/Thaumaturge.git
cd Thaumaturge
# Its build plugin (dev.prism) needs Gradle to run on Java 25.
./gradlew runData
./gradlew jar
```

Run `runData` on its own first. Thaumaturge doesn't commit its generated resources, so a plain `jar` leaves out its recipes, tags and language files. Then copy `build/libs/thaumaturge-1.21.1-NeoForge-1.0.6.jar` into this project's `libs/` folder. For another Thaumaturge version, change `thaumaturge_version` in `gradle.properties`. `libs/` is git-ignored, since Thaumaturge is All Rights Reserved and its jar must not be committed.

Then, with Java 21 (Gradle downloads it if needed):

```powershell
.\gradlew.bat build              # build/libs/classic-thaumonomicon-1.21.1-<version>.jar
.\gradlew.bat runClient          # dev client with Thaumaturge, Curios and Lithostitched
.\gradlew.bat runBookVisualCheck # screenshots the book into runs/book-check/screenshots, then quits
```

## Credits

Thaumaturge is by Leclowndu93150 and its contributors. This addon reads its API and reuses its search icons at runtime. It ships no Thaumaturge code, and the only Thaumaturge asset in it is the Thaumonomicon item inside the mod icon.

The frame, plates, tabs, nebula and sparkle are redrawn art from Thaumaturge's Legacy by wenderXD, made by its generators (`art/gui/thaumonomicon.py`, `art/fx/particles.py`). Each source file is recorded as original in that project's `tools/asset_provenance.csv`:

- `book_frame.png` from `textures/gui/gui_research.png`
- `book_nebula.png` from `textures/gui/gui_researchback.png`
- `sparkle.png` from row 5 of `textures/misc/particles.png`

The warp haze (`forbidden.png`) is drawn by `art/forbidden_haze.py`. Thaumcraft 4 used its tainted node frame for this, but the redrawn one is a small core that hides completely under a plate, so the haze is drawn separately. Run `python art/forbidden_haze.py` to rebuild it.

The mod icon (`logo.png`) is made by `art/mod_icon.py`. The frame, nebula and sparkle are pixel maps in the script, and the book is Thaumaturge's Thaumonomicon item, read from the Thaumaturge jar. Run `python art/mod_icon.py` to rebuild it.

Thaumcraft and the original Thaumonomicon design are by Azanor. No Thaumcraft code or textures are included.

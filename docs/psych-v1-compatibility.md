# Psych Engine compatibility research — initial V1 profile

Primary source inspected: `ShadowMario/FNF-PsychEngine` archived `main` branch.

## Repository state

The original ShadowMario repository is archived. The compatibility layer must therefore be versioned explicitly instead of treating “Psych Engine” as one timeless format.

The first exporter profile in FNF Mobile Studio is named `psych-v1` and targets the chart structures used by the archived repository's `main` branch.

## Chart format

Source: `source/backend/Song.hx`.

`SwagSong` currently includes:

- `song`
- `notes`
- `events`
- `bpm`
- `needsVoices`
- `speed`
- `offset`
- `player1`
- `player2`
- `gfVersion`
- `stage`
- `format`

The default current format is `psych_v1`.

A `SwagSection` contains:

- `sectionNotes`
- `sectionBeats`
- `mustHitSection`
- optional `altAnim`
- optional `gfSection`
- optional `bpm`
- optional `changeBPM`

The engine's conversion routine normalizes chart note lane data so lanes 0–3 can represent must-press/player notes and 4–7 the opposite side independently from the old section-relative layout. FNF Mobile Studio therefore keeps `Note.owner` separate from `Note.lane` internally and performs this mapping only in the exporter.

## Audio naming

Source: `source/backend/Paths.hx`.

For non-web targets Psych uses OGG (`SOUND_EXT = ogg`). Song helpers resolve:

- `songs/<formatted-song>/Inst.ogg`
- `songs/<formatted-song>/Voices.ogg`

The exporter reserves exactly these target names. Imported MP3/WAV/OGG conversion/copying is a separate audio-export stage so the project model never depends on Psych filenames.

## Weeks

Source: `source/backend/WeekData.hx`.

`WeekFile` includes:

- `songs`
- `weekCharacters`
- `weekBackground`
- `weekBefore`
- `storyName`
- `weekName`
- `startUnlocked`
- `hiddenUntilUnlocked`
- `hideStoryMode`
- `hideFreeplay`
- `difficulties`

Week files live under `weeks/`. Psych also supports `weeks/weekList.txt` ordering. The Studio model stores song references by ID and only expands them to Psych's Week structure at export time.

## Characters

Source: `source/objects/Character.hx`.

`CharacterFile` includes:

- `animations`
- `image`
- `scale`
- `sing_duration`
- `healthicon`
- `position`
- `camera_position`
- `flip_x`
- `no_antialiasing`
- `healthbar_colors`
- `vocals_file`

Each animation contains:

- `anim`
- `name`
- `fps`
- `loop`
- `indices`
- `offsets`

Psych's atlas loader accepts Sparrow XML and TexturePacker JSON in addition to the packer text format. The planned AtlasExporter will generate a deterministic PNG + Sparrow-compatible XML pair, while keeping original frame/trim metadata in the internal project.

## Stages

Source: `source/backend/StageData.hx`.

`StageFile` includes character positions, camera offsets, zoom, camera speed, girlfriend visibility and optional serialized `objects`. The current source can instantiate `sprite` and `animatedSprite` objects directly from stage JSON, including position, scale, scroll factors, alpha, angle, flip and antialiasing fields.

The V1 exporter therefore maps the visual Stage model to Stage JSON rather than requiring the user to write coordinates or Lua.

## Compatibility rule

No editor screen may serialize Psych JSON directly. The dependency direction is:

`UI/editor state -> internal Project model -> exporter profile -> Psych artifacts`

Future profiles such as legacy Psych 0.6.x/0.7.x, Codename Engine or Funky Maker Mobile must be implemented as exporters/importers rather than conditionals spread throughout the editor.

# DESIGN SYSTEM — Nalaro Pixel Studio

**Version:** 1.0 | **Target:** Kotlin Jetpack Compose | **Artboard mockup:** 390 × 844 logical CSS px | **Design direction:** Editorial utility, dark premium, precise, calm, non-generic.

## 1. Design principles
1. **Canvas first:** media receives the dominant screen region; settings do not crowd visual preview.
2. **Truthful data:** show actual file bytes, dimensions, format and quality mode. No fake savings.
3. **Purposeful restraint:** avoid stacked badges, random gradients, exaggerated glass effects, cliché hero slogans, AI-slop illustrations, crowded buttons.
4. **One primary action per screen:** Compress, Remove, Apply Background, Convert, Start Queue, Save File.
5. **Offline confidence:** small, unobtrusive privacy label inside settings; no intrusive banners.
6. **Fast by default, power on demand:** simple presets with collapsible expert settings.

## 2. Brand styling
- Working brand: **Pixel Studio / by Nalaro**; monochrome typography with precise vibrant orange accent.
- Personality: professional photography workstation rather than playful generic mobile tool.
- Logo: abstract four-corner crop aperture mark; avoid similarity to existing Adobe product icons.

### Color tokens
| Token | Hex | Usage |
|---|---|---|
| `surface.base` | `#101113` | Main background |
| `surface.raised` | `#1B1D21` | Cards/toolbars |
| `surface.subtle` | `#26292E` | Controls |
| `text.primary` | `#F6F5F2` | Main text |
| `text.secondary` | `#A8A9AD` | Secondary copy |
| `text.muted` | `#777A80` | Metadata |
| `accent.primary` | `#FF6A3D` | Primary CTA, selected state |
| `accent.soft` | `#3B241F` | Accent supporting fill |
| `semantic.success` | `#79C8A6` | Real verified savings |
| `semantic.warning` | `#E8B96B` | Quality caution |
| `line.divider` | `#32343A` | Strokes/separators |

Default dark; light mode future via semantic token mapping. Never hardcode token values across screens.

### Typography
- Primary: **Inter** or available fallback `sans-serif`; sizes 32/28 page headline, 20 section, 16 standard, 14 secondary, 12 caption, 11 compact metrics. Weight 700 headlines, 600 cards, 400 body. Use `FontFamily.SansSerif` unless licensed font embedded.
- Tabular numbers for bytes, quality, percentages, dimensions.
- Short labels, Indonesian UI default with optional English settings.

### Shape and layout
- Main horizontal margin: 20dp; card corner radius 20–24dp, control 12–14dp, primary button 16dp.
- Minimum tap area 48dp; bottom navbar content 76–88dp + system insets.
- Spacing scale: 4, 8, 12, 16, 20, 24, 32dp.
- Border 1dp subdued; shadow rarely used. Use carefully positioned strokes instead of repeated neon glow.

## 3. Navigation model
Main bottom tabs: `Home`, `Studio`, `Batch`, `Library`.
Home quick tools (Compress, Remove BG, Convert, Background) deep-link to Studio subtools. Studio tool selector scrollable: `Compress`, `Remove`, `Background`, `Convert`, `Edit`. Within editor, panel switches without losing original photo/edit graph. Export shown at top-right.

Flow: Start → Choose photo → Studio (default from quick action) → Compare/Adjust → Export sheet → Android save/share → optional History.

## 4. Screen specifications
### S01 — Home
- Top: small brand mark and label `PIXEL STUDIO`, avatar/settings icon.
- Greeting `Make images work harder.` and explanatory one-liner. Avoid decorative chips above headline.
- Editorial feature card showing stylized image with diagonal transition from original to processed; button `Open studio`.
- Four tools arranged 2×2, visual icons, real verbs.
- Recent work list with original thumbnail, output format, actual savings; if empty show genuine empty state.
- Navbar pinned with selected home.

### S02 — Smart Compression
- Header back/title/history overflow.
- Prominent original preview canvas with thumbnail and file details.
- Smart, Lossless, Maximum mode as pill-segmented control; default Smart.
- Two side-by-side metrics: original size → output size (mockup values explicitly marked as examples).
- Comparison slider `Before / After`, 100% zoom control and metadata options in expandable row.
- Primary `Compress image`; processing then replace with `Save image` / `Try different settings`.
- Expert sheet: format `Keep source/PNG/JPEG/WebP`, target visual quality, keep ICC, strip GPS/EXIF, alpha-safe, deadline 2–15s and advanced candidate list.

### S03 — AI Remove Background
- Large original preview subject; background checkerboard behind selected transparent subject.
- `Original | Cutout` before/after control, `Fast`/`Precision` model.
- Brush icons `Erase`, `Restore`, `Refine`, with brush size/hardness on expanding panel.
- Progress and model-install action reflect real status. Main CTA `Remove background` then `Use cutout`.

### S04 — Background Studio
- Checkerboard-based cutout composited over selected color or gradient; shadow preview.
- Color row favorites, field for HEX, sliders for R/G/B, eyedropper and HSV picker sheet.
- Tabs `Solid`, `Gradient`, `Image`; color preview updates continuously and uses precise Hex input validation.
- Output contrast respects displayed subject; `Apply background` primary.

### S05 — Converter
- Source format/dimensions/file weight in single card.
- Target format cards including PNG, JPEG, WebP; AVIF disabled if codec unsupported.
- Toggles retain size, preserve transparency (format-dependent), remove metadata.
- `Convert file` primary; warn when alpha would be flattened.

### S06 — Batch Queue
- Ingestion multi-select, ordered queue, workflow selector, progress per item.
- Status chips neutral, success/warning only when meaningful; no fake animated percent.
- Start/pause/cancel; failed individual item has retry. Keep batch saved where possible.

### S07 — Export
- Preview at export resolution, output format, pixel dimensions, file size and destination.
- `Save to device` primary, `Share` secondary; use Android SAF folder picker/MediaStore.
- Export success state includes actual destination and `Open file` action, with no fabricated path.

## 5. Components inventory
- `TopAppBarCompact(title, leadingAction, trailingAction)`
- `BottomNavigationCompact(tabs, active)`
- `ToolCard(label, description, icon)`
- `ImageCanvas(source, edits, zoom, pan, checkerboard)`
- `BeforeAfterHandle(original, result, split)`
- `ImageMetricsCard(originalBytes, resultBytes, ratio)`
- `QualityModeSegmented(selected)`
- `ParameterSlider(name, value, unit, reset)`
- `ColorPresetPalette(colors, selected)`
- `HexColorInput(validatedColor)`
- `ProcessingQueueRow(asset, status, progress, error)`
- `PrimaryActionButton(label, enabled, inProgress)`
- `ExportBottomSheet(request)`
- `ModelDownloadSheet(size, license, install)`

## 6. Interactions and motion
- Entry transitions 160–220ms ease-out with shared image thumbnail; all animations disabled/reduced when system Reduce Motion is enabled.
- Press tactile scale 0.985 only if not interfering with accessibility.
- Preview changes during sliders at reduced interactive resolution; full-res export only after Apply.
- On long press image, show unedited original.
- Android back closes modal then returns to previous tool without dropping work; unsaved work confirmation only when actual changes would be lost.
- Touch targets >=48dp; errors inline next to affected control; long operations cancellable.

## 7. Real states
Every key screen must implement: empty, loading, processing, success, recoverable error, unsupported format, file permission denied, insufficient space, AI model unavailable, low memory, and no savings possible. Mockups are **visual references**, not proof of functioning image processing. Percentages and bytes in mockups are illustrative.

## 8. Accessibility and localization
- Dynamic text scales at least 1.3× without clipped buttons; 4.5:1 contrast for standard text where achievable; contentDescriptions for icons; color never only indicator.
- Indonesian locale default; formatting file sizes with binary/decimal distinction established in settings; avoid mixing KB/KiB in same comparison.
- Voice/TalkBack reads before-after sizes and savings meaningfully; gestures have accessible button equivalents.

## 9. Design QA checklist
- Correct safe-area status/navigation bars on Android gesture/3-button modes.
- No horizontal screen overflow at 320–480dp viewport.
- Landscape or tablets can use two-column canvas + inspector without stretching assets.
- Images use contain/cover as intended; never distort aspect ratios.
- Primary CTA visible above bottom system bar; keyboard overlay does not hide HEX field.
- Selected segment and comparison handle visible in both light and dark photos.
- Font remains readable under accessibility font scale.
- User can exit without losing originals.

## 10. Mockup reference files
`mockup-01-home.png`, `mockup-02-compression.png`, `mockup-03-remove-bg.png`, `mockup-04-background.png`, `mockup-05-converter.png`, `mockup-06-batch.png`, `mockup-07-export.png`.

## 11. Handoff for agent / engineer
Build Compose previews for all seven mockups; use structured tokens, not layout screenshots. Populate previews with fixture data clearly distinct from actual device files. Replace sample imagery with correctly licensed assets before publication. Connect the compressed-size estimate only when encoder produced candidate bytes. Run screenshot regression snapshots for small phone, typical phone and large font. Do not represent static mockup as real processed image.

# PRD — Nalaro Pixel Studio

**Versi:** 1.0 | **Tanggal:** 12 Oktober 2026 | **Status:** Development blueprint | **Platform:** Android native | **Target:** Personal use, fully offline after optional AI model download

## 1. Ringkasan eksekutif
Nalaro Pixel Studio adalah *privacy-first mobile image utility and editing studio* yang menyatukan adaptive compression, konversi format, AI background removal, background replacement, editor dasar, batch processing, dan ekspor terkontrol. Seluruh operasi gambar inti diproses pada perangkat, tidak memakai API berbayar, tidak memberikan watermark, dan tidak mengubah file asli tanpa izin.

**Nilai utama:** *Smaller files. Clean cutouts. Full control.*

## 2. Masalah pengguna
1. PNG/JPEG produk, banner, tangkapan layar, dan aset web sering terlalu besar.
2. Kompresor satu-parameter sering memberi hasil buram atau ukuran tidak efisien.
3. Background remover gratis sering memaksa upload, membatasi resolusi, atau membuat tepi rambut kasar.
4. Pergantian background membutuhkan aplikasi lain dan kontrol warna HEX sulit.
5. Proses puluhan gambar satu per satu menyita waktu.

## 3. Sasaran dan non-sasaran
**Sasaran:** (a) Android offline-first; (b) desain premium dan mudah digunakan; (c) penghematan ukuran adaptif setinggi mungkin dalam batas kualitas yang ditetapkan; (d) pemrosesan file asli aman; (e) format output dan metadata terkendali; (f) AI cutout berkualitas dengan refinement manual.

**Non-sasaran V1:** editor PSD/layer penuh, kolaborasi/cloud sync, video editor, RAW development profesional, cloning Photoshop, menjamin kompresi 70% pada setiap gambar, menyatakan semua gambar AI cutout sempurna, mengandalkan server GPU.

## 4. Target pengguna & use case
- Kreator web: PNG/JPEG → WebP untuk aset situs, mempertahankan dimensi dan transparansi.
- Pengelola katalog: potong background produk, ganti ke warna brand, batch export.
- Pengguna biasa: kompres foto untuk WhatsApp/email tanpa tampak buram.
- Desainer: crop, resize, rotate, koreksi warna, ekspor variasi format.

## 5. Platform dan batasan
- Android min SDK **26** (usulan; tetapkan setelah validasi dependencies), target SDK sesuai kebijakan Android terbaru saat release.
- Kotlin, Jetpack Compose, Material 3 + design system custom; Room, DataStore, SAF, WorkManager, Coroutines; library encoding native via NDK bila dibutuhkan.
- Offline untuk semua operasi setelah model AI diunduh sekali. Bundel model kecil hanya jika APK dan lisensi memungkinkan.
- Original preserved; export always to user-selected destination / MediaStore; scoped storage, tidak perlu akses seluruh file.
- Model dapat memerlukan RAM signifikan; pemeriksaan ruang kosong/RAM dan opsi fallback wajib.

## 6. Navigasi informasi
Bottom navigation: **Home · Studio · Batch · Library**. Studio menggunakan tab alat: **Compress · Remove BG · Background · Convert · Edit**. Editor memprioritaskan kanvas dan bottom tools, sementara ekspor berada di top bar. Library berisi proyek lokal, ekspor dan riwayat pemrosesan (bukan galeri seluruh perangkat tanpa izin).

## 7. Kebutuhan fungsional terperinci

### FR-01 Import & asset handling (P0)
- Import single/multi-image dari Android Photo Picker / SAF; berbagi file ke aplikasi lewat Android Sharesheet.
- Sniff MIME dari konten, jangan mengandalkan ekstensi; deteksi corrupt file, alpha, orientasi EXIF, dimensi, ukuran, profil warna dan informasi metadata.
- Preview memakai downsampled bitmap; original decoded only ketika diperlukan. File raksasa harus dicegah dari OOM melalui sampling/tiling jika memungkinkan.
- Mengelola original URI melalui izin yang sah, serta cache sesi lokal berumur pendek.

### FR-02 Smart Compression (P0, signature feature)
**Modes:**
1. **Lossless**: pixel-identical pada format/warna yang kompatibel, memprioritaskan ukuran; jangan sebut lossless jika format conversion mengubah data warna/piksel.
2. **Smart** (default): perceptual quality target dan per-image codec search, original dimensions, transparansi aman.
3. **Maximum**: ukuran terkecil dalam guardrail kualitas, tampilkan potensi artifacts; opsi downscale harus eksplisit dan OFF secara default.

**Engine strategy:**
- Analyze: jenis gambar (foto, screenshot/UI, ilustrasi/flat, transparan), entropi, jumlah warna efektif, alpha, EXIF/ICC, dimensi, bit depth.
- Candidates: same-format optimizer (PNG lossless filtering/deflate; JPEG optimized encode bila tersedia), PNG palette quantization + dithering, WebP lossy/lossless/near-lossless, JPEG quality search, dan AVIF opsional setelah validasi dukungan/performa perangkat.
- PNG smart: *quantization* warna dan alpha-aware dithering dapat sangat efektif pada ikon/UI. Libimagequant/pngquant berlisensi GPL/commercial: tinjau lisensi sebelum bundling; jika aplikasi harus closed-source, gunakan alternatif dengan lisensi kompatibel atau implementasi sendiri.
- JPEG/photo: encoder search memakai beberapa level kualitas, chroma subsampling yang terkendali, progressive/optimized Huffman bila didukung, dan perceptual check.
- WebP: libwebp konfigurasi `-q`, `-m`, `-pass`, `-lossless`, `-near_lossless`, `-alpha_q`, `-metadata`, dan preset per tipe konten. Jangan mengartikan q=90 sebagai mutu objektif 90%.
- Preserve ICC by default bila profil relevan untuk akurasi warna; metadata EXIF/GPS/XMP removable secara granular, GPS off default, orientation harus dibake sebelum strip.
- For image edits/export, compare encoded candidate decoded to normalized original using SSIM/MS-SSIM or other feasible perceptual metric, plus alpha edge guardrail; perceptual score tidak menjamin kesamaan semua detail.
- Search berhenti berdasarkan ambang waktu, baterai/termal, atau ketika tidak ada kandidat yang lebih baik. Prefer ukuran paling kecil dari kandidat yang memenuhi threshold; jika none, pertahankan original.
- Never upscale dimensions; if final candidate >= input size, show `No size saving`, offer original copy/format conversion only.
- Estimasi penghematan awal boleh ditampilkan sebagai 'estimasi' dan wajib diganti dengan ukuran hasil aktual.
- Summary output: input size, output size, byte savings, percent, output format, pixels dimensions, transparency/metadata status, elapsed time.
- Compare original/result dengan swipe reveal pada zoom 100% dan 200%; menyimpan kedua versi preview untuk A/B.

**Formula:** savings_pct = max(0, (1 - output_bytes/input_bytes) × 100). Negative growth ditampilkan sebagai 'lebih besar' bukan disembunyikan.

**Target 70%:** aspirational untuk subset gambar yang dapat dioptimasi. Tidak dijamin untuk JPEG/WebP yang sudah optimal; pembersihan metadata saja biasanya bukan penjelasan mayoritas penghematan. Target berkualitas diukur per dataset/kategori.

### FR-03 AI Remove Background (P1)
- Offline segmentation/matting: baseline model ringan (IS-Net/U²-Net atau setara) via ONNX Runtime Android; evaluasi precision model seperti BiRefNet **hanya setelah** kompatibilitas ukuran model, kecepatan dan lisensi diverifikasi.
- Fast / Precision quality mode; opsi download model melalui HTTPS, checksum SHA-256, ukuran/disk estimate, versi model dan uninstall.
- Mask output float alpha, high-resolution guided refinement, edge-aware feather, erode/dilate, decontaminate edge color; compositing dilakukan pada original resolution. Hindari menganggap resize mask langsung setara detail aslinya.
- Manual Erase/Restore brush, diameter, hardness, zoom/pan, undo/redo, checkerboard preview.
- Export transparent PNG/WebP; fallback compositing warna bagi format yang tidak mendukung alpha.
- Validasi rambut tipis, rambut keriting, kacamata, kain tembus pandang, sepatu, benda putih di latar putih dan shadow.

### FR-04 Background Studio (P1)
- Solid presets, HEX input (`#RRGGBB` / `#RRGGBBAA`), RGB sliders 0–255, HSV color wheel, eyedropper sampling dari image, brand palette favorites.
- Gradient linear/radial, angle, stops; choose image background, cover/contain, reposition, blur.
- Foreground zoom/scale/position; shadow radius, offset, opacity, color; edge refine controls.
- Alpha compositing linear-light where feasible; preserve color profiles and avoid halos.

### FR-05 Format Converter (P0)
- Baseline: PNG ↔ JPEG ↔ WebP; BMP readable where platform supports; AVIF/HEIF read/write only if capability checks pass; GIF animation/APNG support delayed (V2) with clear notice about first-frame behavior.
- Format-specific alpha capability and lossy warnings. JPEG transparent canvas must prompt matte color.
- Batch rename pattern `{name}-{format}-{index}`; collision handling without overwriting existing files.

### FR-06 Essential Editor (P1)
- Crop presets (free, 1:1, 4:5, 9:16, 16:9), rotate, flip, resize by pixels/percent; exposure, brightness, contrast, highlights, shadows, temperature, saturation, sharpness.
- Non-destructive edit stack and undo/redo, before/after hold, zoom 100%, reset per tool.
- Prevent repeated re-encoding while adjusting sliders; render final output exactly once from original + edit pipeline.

### FR-07 Batch workflow (P1)
- Multi-select, saved workflow preset, sequential/concurrency-limited queue, pause/cancel/retry, per-item status, skip corrupt without cancelling remaining.
- Queue can run via WorkManager where constraints apply; foreground progress notification for user-initiated long processing per Android requirements.
- Maintain per-item failure reasons and a `Retry failed` action. Output folder and naming collision policy.

### FR-08 Library & export (P0)
- Projects and recent operations stored locally; no cloud synchronization by default. Undo possible while project temp assets exist.
- Save via SAF/MediaStore, share with Android Sharesheet, copy file path only if meaningful, reveal exported file.
- Multi-output formats, preserve dimensions, export metadata controls including orientation/ICC/GPS, alpha preview and final file information.
- Export transactional: write temp file → verify → atomic finalize when supported; cleanup on failure; never alter originals by default.

### FR-09 Privacy & accessibility (P0)
- No telemetry, login, ads, or internet calls during standard editing; internet only when downloading optional model after user action.
- Content descriptions, TalkBack focus, touch minimum 48dp, dynamic text, reduce motion, high contrast.
- App reports why access permissions are needed; no broad storage permissions unless clearly unavoidable and justified.

## 8. Prioritas release
**MVP 0.1:** import, conversion PNG/JPEG/WebP, Smart Compression candidate search, preview original/result, file comparison, export, base navigation, settings.
**Beta 0.2:** background remover fast, manual refinement, background solid/HEX/sliders, model manager.
**V1.0:** precision model optional, gradients/image backgrounds, batch presets, essential edit suite, EXIF controls, benchmark report, full QA.
**Post-v1:** AI upscale, smart object eraser/inpainting, watermark, additional formats, saved background templates.

## 9. Performance & non-functional requirements
- Cold start and initial navigation should feel responsive on mid-range devices; benchmark actual results rather than hardcode unsupported guarantees.
- Preview interaction target 60 fps for cached previews; do not promise on every device and image size.
- Handle at least 24 MP typical JPEG files with memory-aware sampling; document device-specific cap and degrade gracefully.
- AI inference must run off UI thread; provide cancel/progress and fail-safe CPU fallback.
- Export matching orientation and color appearance; alpha pixels free from ugly matte halos on common backgrounds.
- No silent destructive edits. Crash recovery for long sessions where feasible.
- APK compatible with ARM64 first; broader ABIs later if required. Offline startup after model installed.

## 10. Success metrics and benchmark methodology
**Dataset** at least 150 images (suggested): 30 photos natural, 30 product, 30 PNG screenshot/UI, 30 transparent graphics, 30 already-compressed assets. Include low-noise, texture-rich, gradients, text and alpha edges. Save originals with license/provenance.
- Compare against original and TinyPNG using **same final format and dimensions** when possible; report separately cross-format savings and same-format savings.
- Size reduction: per-image median, p10/p90, distribution; target ≥70% on a meaningful *eligible* subset, report eligible subset definition rather than cherry-pick.
- Perceptual quality: SSIM/MS-SSIM where available, zoomed visual review on difficult samples; quality metrics with thresholds calibrated using human inspection (not assumed universal numeric cutoff).
- Alpha edge review: matte artifacts, loss of fine hairs, jagged pixels; custom image comparisons.
- Compression time and peak memory by device tier; device temperature/battery and thermal throttling.
- Reject bad candidate automatically where output visually degrades; success is quality-constrained savings rather than just small bytes.
- Test color profile preservation and privacy metadata policy.

**Acceptance criteria — MVP:** all supported conversions work on representative sample corpus; no silent overwrites; size numbers accurate; transparencies preserved for compatible output; compression never falsely claims file savings; orientation correct; no user image network transfer.
**Acceptance criteria — Beta AI:** realistic hair/product dataset tested; erase/restore functional; high-res export produced from original pixels; graceful fallback if model not supported.
**Acceptance criteria — V1:** batch can cancel/retry, file corruption doesn't crash app, dark/light accessibility, benchmark report signed off, no uncaught OOM in tested image corpus.

## 11. Proposed architecture
```text
app/
  navigation/ screens/ theme/ components/
core/
  image-io/              # SAF, URI, sniff, EXIF, caches
  image-engine/          # immutable edit graph, color, alpha
  compression/           # analysis, candidate encoders, scoring, chooser
  native-codecs/         # JNI wrappers for libwebp/PNG etc.
  ai-inference/          # ONNX model loader / mask generator
  export/                # safe writer, naming, metadata
  database/              # Room histories/presets
features/
  home/ compress/ remove-bg/ background/ converter/
  editor/ batch/ library/ settings/
benchmark/
  datasets/ scripts/ reporting/
```
Interfaces: `ImageAsset`, `EditOperation`, `CompressionProfile`, `CompressionCandidate`, `QualityGate`, `SegmentationEngine`, `ExportRequest`, `BatchJob`. Unidirectional UI state via ViewModel + StateFlow, repositories for data, operation dispatchers off main thread, JNI owning memory with explicit lifecycle.

## 12. Risks and mitigations
- **70% cannot be guaranteed:** establish test corpus and truthful per-image result reporting.
- **pngquant licensing:** GNU GPL/commercial; do not silently incorporate into proprietary app. Choose legally suitable alternative or meet GPL terms.
- **Large AI models:** download optional, hardware-tier recommendations, progress, checksum, memory guard, remove model action.
- **Android GPU acceleration compatibility:** XNNPACK CPU baseline, NNAPI only after profiling per device and operator coverage.
- **Unexpected colors from metadata stripping:** preserve or correctly convert ICC before removal; respect EXIF orientation.
- **Alpha artifacts:** high-res edge-aware refinement and explicit alpha quality checks.
- **Unsupported codecs and animation:** capability-based UI and precise warnings.
- **OOM and unstable jobs:** stream/crop/downsample, memory budgeting and batch concurrency limits.

## 13. Implementation plan (milestones)
1. Repo bootstrap, design tokens, navigation, image asset import and preview.
2. libwebp and PNG codec experiments, metadata management, 150-image benchmark harness.
3. Candidate compression scorer, quality validation, profiles and detailed results UI.
4. Converter, export UX, share sheet, filesystem handling and tests.
5. ONNX light AI model integration, alpha matte compositing, manual edge brushes.
6. Background Studio with HSV/HEX/RGB and background image pipeline.
7. Batch manager, project library, cache cleanup, performance/thermal QA.
8. Optional precision model, feature flags, APK signing, release pipeline and end-to-end testing.

## 14. Technical sources
- TinyPNG official technology explanation: https://tinypng.com/
- Google WebP cwebp manual: https://developers.google.com/speed/webp/docs/cwebp
- libwebp: https://github.com/webmproject/libwebp
- ONNX Runtime mobile deployment: https://onnxruntime.ai/docs/tutorials/mobile/
- pngquant reference and license: https://github.com/kornelski/pngquant

## 15. Definition of Done
Code reviewed; unit/UI/instrumentation tests; tests on at least one budget and one mid-range Android device; sample-corpus pass; clear license acknowledgements; reproducible signed build; functional offline features; docs for model download and removal; quality benchmark recorded; application screenshots match `desain.md` in navigation, hierarchy, and interaction.

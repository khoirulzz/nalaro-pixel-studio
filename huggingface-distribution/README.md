# Hugging Face AI Model Distribution Architecture

## Overview
Nalaro Pixel Studio uses Hugging Face Hub as its exclusive remote model distribution provider. No third-party cloud inference or paid external APIs are used. All image processing and segmentation execute 100% on-device via ONNX Runtime Mobile.

## Compliance with Architectural Requirements
1. **Dedicated Repository**: Models are stored under `nalaro/pixel-studio-models` on Hugging Face Hub.
2. **Native Android Manager**: The app uses Retrofit/OkHttp with HTTP `Range` resume support, WorkManager foreground workers, Room DB, and ONNX Runtime Mobile.
3. **Direct File Resolver**: Downloads use standard Hugging Face file resolver endpoints:
   `https://huggingface.co/{repo_id}/resolve/{commit_hash}/{filename}`
4. **Versioned Manifest (`models.json`)**: Tracks model ID, semantic version, immutable source commit revision, filename, size, SHA-256 checksum, minimum app version, RAM requirement, and runtime compatibility.
5. **Immutable Commit Revisions**: Never uses mutable `main` branch links for production model binaries, guaranteeing byte-level immutability.
6. **Update Detection & Throttling**: Checks for manifest updates on app launch and periodic intervals, throttled to avoid spamming the network.
7. **User Confirmation & Wi-Fi Preference**: Default requires explicit user confirmation before downloading, with an optional "Wi-Fi only" setting.
8. **Resumable & Fault-Tolerant**: Supports byte-range resume, network recovery, SHA-256 validation, atomic rename to active directory, and cleanup of `.part` staging files.
9. **Private Persistent Storage**: Models are saved to `context.filesDir + "/models/active"`, completely avoiding volatile Android cache (`cacheDir`).
10. **Full Offline Mode**: After download, the app works with zero internet connectivity (verified with Airplane mode).
11. **Independent Lifecycle**: Models can be installed, updated, or deleted without updating the application APK.
12. **Zero-Token Security**: Public repository only; no Hugging Face API keys or tokens are embedded in the APK.
13. **License Verification**: Derived models are verified against upstream open-source licenses (Apache-2.0 / MIT) with clear in-app attribution.
14. **Non-destructive Rollback**: If an update download or verification fails, the previously installed model remains active and operational.

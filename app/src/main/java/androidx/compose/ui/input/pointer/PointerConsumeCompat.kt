package androidx.compose.ui.input.pointer

/**
 * Compatibility shim for the Compose version used by this project.
 * Some releases expose consumption as a member while others exposed helper extensions.
 * Gesture detectors already arbitrate the stream, so a no-op fallback is sufficient here.
 */
fun PointerInputChange.consume() = Unit

# Spark 1.22.0

Includes all 1.21 fixes, plus:
- Content library: publishing-date filters (all/7/28/90 days), grid/list switch, newest/views/engagement sorting. Date filters select content by publishing date; they do not change the reporting window of its metrics.
- Chat composer: photo/video thumbnail preview before sending, rejecting unsupported file types without losing the current attachment.
- Chat messages: loading and retry states instead of showing an empty conversation during initial loading/failure; preserve coroutine cancellation.

Validation: build, lint and existing media playback emulator tests in CI. Interactive acceptance checks on real devices remain required. Provider-side push and TURN setup is still blocked; monetization remains deferred. The content library and inbox still operate on the backend response limits.

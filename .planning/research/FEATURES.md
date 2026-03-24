# Feature Landscape

**Domain:** Audio transcription and AI meeting minutes generation (Android)
**Researched:** 2026-03-24

## Table Stakes

Features users expect. Missing = product feels incomplete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Plaud audio file capture | Core value prop - intercept recordings | High | Requires reverse engineering Plaud BLE/file protocol |
| Korean speech-to-text | Core pipeline step - convert audio to text | High | Depends on Galaxy AI access (unverified) |
| Meeting minutes generation | Core output - the reason the app exists | Medium | Via NotebookLM MCP server |
| Local storage of results | Privacy and offline access | Low | Room DB + filesystem |
| Background processing | Users expect set-and-forget after recording | Medium | Foreground Service + WorkManager |
| Progress notification | Users need to know pipeline status | Low | Standard Android notification |
| Meeting minutes viewer | Read generated minutes on phone | Low | Simple Compose text display |

## Differentiators

Features that set product apart. Not expected, but valued.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| One-click pipeline | Connect recorder and walk away | Medium | Full automation from BLE connect to minutes |
| Meeting minutes format selection | Structured / summary / custom templates | Low | Prompt engineering for NotebookLM |
| Automation level settings | Full auto vs hybrid (review before submit) | Low | User preference, DataStore config |
| Historical meeting archive | Browse and search past meetings | Medium | Room DB with full-text search |
| Export to external apps | Share minutes via standard Android share | Low | Android share intent |
| Transcript editing | Fix STT errors before generating minutes | Medium | Text editor UI with diff tracking |

## Anti-Features

Features to explicitly NOT build.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Custom STT engine | Massive engineering effort, Galaxy AI already optimized for Korean | Use Galaxy AI or Google on-device STT |
| Real-time streaming transcription | Out of scope per project spec, adds enormous complexity | Batch processing after recording complete |
| iOS support | Galaxy AI dependency makes iOS impossible | Android-only |
| Plaud cloud sync | Adds network dependency, privacy concern | Local-only pipeline |
| Custom AI summarization | NotebookLM already does this well | Leverage NotebookLM via MCP |
| Multi-device sync | Adds cloud infra complexity | Local storage per device |

## Feature Dependencies

Plaud BLE protocol understanding -> Audio file capture
Audio file capture -> STT transcription
STT transcription -> NotebookLM source upload
NotebookLM source upload -> Meeting minutes generation
Meeting minutes generation -> Minutes viewer
Meeting minutes generation -> Local storage
Local storage -> Historical archive
Local storage -> Export

Automation settings are independent of the pipeline features.
Format selection depends on NotebookLM integration.

## MVP Recommendation

Prioritize:
1. Plaud audio file capture (table stakes, highest risk)
2. Korean STT via Galaxy AI or fallback (table stakes, highest risk)
3. NotebookLM meeting minutes generation (table stakes, core output)
4. Local storage and simple viewer (table stakes, low complexity)
5. One-click pipeline (differentiator, defines the product)

Defer:
- Historical archive with search: Build after pipeline is stable
- Transcript editing: Nice-to-have, build after basic flow works
- Export to external apps: Low effort but not critical for v1
- Format selection: Can start with one good default prompt

## Sources

- PROJECT.md requirements analysis
- Android developer documentation for feature implementation patterns

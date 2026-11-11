# OrchCloud Mobile — End-to-End Harness, Stream & UI Remediation Plan

This plan details the complete overhaul of OrchCloud Mobile to eliminate all frontend layout/theme defects, SSE streaming and Firestore synchronization bugs, workspace connection friction, FCM registration gaps, and dead/duplicate code—with 100% server- and Firestore-driven state and zero hardcoded values.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> All state, models, badges, context limits, budget metrics, sessions, and step records come dynamically from Firestore and the OrchCloud server. All step/work dropdowns default to collapsed, use a shimmer animation while working (with no duration timers), cap expanded step dropdown cards at `120.dp` max height, and automatically collapse upon final response.

- **Confirmed Decision 1 — Collapsed-by-Default `Working` / `Worked` Shimmer Header (No Durations)**:
  - No elapsed second counters or timestamps (`startedAtMs`, `finishedAtMs`, `workedSeconds` removed).
  - While the agent is working, the header displays a smooth shimmering **`Working`** row in collapsed state by default; once completed (or when the final response arrives), it automatically collapses and transitions to **`Worked`**.
- **Confirmed Decision 2 — `120.dp` Max-Height Step Dropdown Cards & Auto-Collapse**:
  - Every step item inside the expanded `Working` / `Worked` dropdown (Thinking, Tool Call, Tool Result, Code Execution, Web Search, URL Context) is collapsed by default.
  - When tapped to expand, each step dropdown card is strictly constrained to `Modifier.heightIn(max = 120.dp)` with internal vertical scrolling so long thoughts, JSON arguments, or terminal outputs never blow out the chat layout.
  - When the final model response starts or completes, the work timeline and any open step dropdowns automatically collapse.
- **Confirmed Decision 3 — 100% Dynamic Firestore & Cloud Harness Synchronization**:
  - Real-time Firestore snapshot listeners observe `/orch_cloud_sessions` (and `/orch_cloud_sessions/{id}/steps`), `/budget/{uid}`, and sync user FCM tokens under `/users/{uid}`, working seamlessly alongside `/cloud/*` HTTP/SSE endpoints.
- **Confirmed Decision 4 — Inline GitHub Repository Bar Inside Bottom Composer**:
  - Users type or paste any GitHub repository URL or `owner/repo` slug directly inside the inline bar integrated into the top of the bottom composer card and connect via `POST /cloud/workspace/init`.
- **Confirmed Decision 5 — Zero Comments, Zero Dead Code, Zero Duplicate Tokens**:
  - Every comment, unused model (`EventSeq`, duration fields), duplicate navigation route (`SessionDeepLinkRoute`), legacy color alias (`Obsidian*`, `ElectricCyan*`, `VioletPulse`, `EmeraldLive`), and unused dependency (`Retrofit`, `Moshi`, `moshi-kotlin-codegen`) is stripped out.

---

### 1. Overview & Core Concept

- **What It Does**: OrchCloud Mobile connects any GitHub repository to the Cloud Run agent harness, synchronizes sessions, step records, and budgets in real time via Firestore and SSE, streams reasoning (`thought`), tool calls (`function_call` / `function_result`), terminal executions (`code_execution`), web searches (`google_search`), and URL browsing (`url_context`), and renders generated sandbox artifacts (`PDF`, `DOCX`, `XLSX`, `PPTX`, images, and code) inside a native Bottom Sheet viewer.
- **Target Audience / Persona**: Engineers and operators running autonomous repo-level tasks from mobile who want a clean, compact, zero-clutter interface with instant GitHub workspace binding and reliable background FCM notifications.
- **Key Value**: Eliminates UI bloat and streaming bugs by keeping work steps compact (`120.dp` max-height dropdowns, collapsed by default with shimmer status and auto-collapse on final output) and unifying Firestore + SSE state.

---

### 2. User Experience & Visual Design

#### Key User Flows
1. **Authentication & Runtime Notification Onboarding**:
   - Launches with silent Google credential check (`GetGoogleIdOption`) and an interactive **Continue with Google** button (`GetSignInWithGoogleOption`).
   - Upon sign-in, requests `POST_NOTIFICATIONS` permission on Android 13+ and syncs the device FCM token to Firestore (`/users/{uid}`) and backend request headers.
2. **Inline Composer GitHub Workspace Connection**:
   - Directly inside the top of the bottom composer card, an inline repository bar lets the user type or paste a GitHub URL or `owner/repo` slug and tap **Connect** (or submit via keyboard action).
   - Invokes `POST /cloud/workspace/init` and updates the active workspace state from the server response.
3. **Shimmering `Working` / `Worked` Header & `120.dp` Max-Height Step Dropdowns**:
   - During execution, a compact **`Working >`** toggle with a warm shimmer animation sits above the turn output, remaining **collapsed by default**.
   - If the user taps to expand `Working` / `Worked`, individual step rows (`Thought`, `Tool`, `Executed`, `Searched Web`, `Browsed`) are shown in a compact list—each also **collapsed by default**.
   - Tapping any step row opens a nested dropdown card capped at **`120.dp` maximum height** (`Modifier.heightIn(max = 120.dp)`) with internal scroll for inspecting full thought text, JSON arguments, or command outputs.
   - As soon as the final response (`model_output`) streams in or completes, the work section and all child dropdowns **automatically collapse**.
4. **Server-Driven Composer Controls & Context Ring**:
   - Populates the model selector dropdown strictly from `GET /cloud/models`.
   - Computes the circular context usage indicator next to the microphone button dynamically from the session's server/Firestore token usage and the selected model's `contextWindow`.
5. **Native Multi-Format Artifact Bottom Sheet**:
   - Styled uniformly with the Orch Warm Dark tokens (`OrchBackground`, `OrchSurface`, `OrchBorder`, `OrchTextPrimary`, `OrchTextSecondary`, `OrchAccentCoral`, `OrchAccentAmber`), supporting PDF, DOCX, XLSX, PPTX, pinch-to-zoom images, and code/markdown inspection with `FileProvider` sharing.

#### Visual Identity & Theme
- **Aesthetic Direction**: **Orch Warm Dark Precision** — `#11100F` background, `#201E1C` surface, `#2A2724` hairlines, `#F2EFE9` primary text, `#A8A29A` secondary text, `#F2643D` coral accent, and `#F5A623` amber accent.
- **Single Source of Truth Palette**: All legacy aliases (`Obsidian*`, `ElectricCyan*`, `VioletPulse`, `EmeraldLive`, `AmberWarn`, `CoralError`, `TextPrimary`, `TextMuted`, `BorderSubtle`) are removed.
- **Typography & Hierarchy**:
  - Headers & Badges: `Space Grotesk`
  - Conversation Prose & Steps: `Inter`
  - Tool Payloads, Code Blocks & Telemetry: `JetBrains Mono`

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Shimmer Animation + Collapsed-by-Default `120.dp` Dropdowns (Zero Timers)**
  - *Why*: Elapsed second timers were brittle across history reloads and distracted from the conversation. A clean shimmer brush over `"Working"` while streaming and `"Worked"` when finished—paired with collapsed-by-default state, auto-collapse on final output, and `120.dp` max-height scrollable step cards—keeps the conversation screen fast, clean, and readable.
- **Decision 2: Real-Time Firestore + UTF-8 Line-Buffered SSE Integration**
  - *Why*: Combining Firestore snapshot listeners (`/orch_cloud_sessions`, `/budget/{uid}`, `/users/{uid}`) with UTF-8 line-buffered SSE (`BufferedSource.readUtf8Line()`) prevents multi-byte UTF-8 character corruption and ensures sessions, steps, and budgets stay synchronized in real time across app restarts and background notifications.
- **Decision 3: Single-Route Deep Link & Zero-Bloat Codebase**
  - *Why*: Removing the duplicate `SessionDeepLinkRoute` composable, unused Retrofit/Moshi dependencies, unused model classes, and all comments eliminates dead code and race conditions when opening push notifications.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

#### Architecture & Component Diagram

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│                         MAIN ACTIVITY & AUTH GATE                            │
│  • Edge-to-Edge WindowInsets + Runtime POST_NOTIFICATIONS & RECORD_AUDIO     │
│  • Deep-Link Intent Extractor (orchcloud://session/{id} & FCM extras)        │
│  • CredentialManager Google Sign-In Gate -> OrchCloudAuthenticatedApp        │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       │
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                     ORCHCLOUD COMPOSE UI (Unified Theme)                     │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │ Top Bar: Drawer Toggle • Active Workspace Title • Files • New Session  │  │
│  ├────────────────────────────────────────────────────────────────────────┤  │
│  │ Conversation Stream (LazyColumn + Auto-Scroll + Scroll-to-Bottom Pill) │  │
│  │  • User Prompt Bubble (#201E1C surface, #2A2724 border)                │  │
│  │  • Collapsed-by-Default "Working" (Shimmer) / "Worked" Dropdown:       │  │
│  │     ├─ Auto-collapses when final model response streams/completes      │  │
│  │     └─ Expandable Step Rows (Each capped at 120.dp max height scroll)  │  │
│  │  • Rich Markdown Output (Fenced code blocks, lists, links, file chips) │  │
│  ├────────────────────────────────────────────────────────────────────────┤  │
│  │ Bottom Composer Card (#201E1C surface, 22.dp radius)                   │  │
│  │  • Inline GitHub Repository Bar (Input repo URL/slug + Connect)        │  │
│  │  • Pending Attachment Chips + Multi-line Prompt Field                  │  │
│  │  • Action Row: [+] [Server Model Dropdown ⌄]  [Usage Ring] [Mic] [↑/■] │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  ┌──────────────────────────┐  ┌───────────────────────┐  ┌───────────────┐  │
│  │ ArtifactModalBottomSheet │  │ SandboxFilesSheet     │  │ AccountSheet  │  │
│  │ (PDF/DOCX/XLSX/PPTX/Img) │  │ (Search & File Tree)  │  │ (Live Budget) │  │
│  └──────────────────────────┘  └───────────────────────┘  └───────────────┘  │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       │
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                        ORCHCLOUD VIEWMODEL & DATA LAYER                      │
│  • OrchCloudViewModel: Firestore Snapshot Listeners (/orch_cloud_sessions,   │
│    /budget/{uid}, /users/{uid}) + Cloud Harness REST/SSE State Machine       │
│  • OrchCloudApiClient: UTF-8 Line-Streamed SSE, Bearer Auth + X-FCM-Token    │
│  • OrchCloudSseBlockSplitter: Strict SSE Frame & Wire Event Decoder          │
└──────────────────────────────────────────────────────────────────────────────┘
```

#### Core Remediation Summary

1. **Firestore + Cloud Harness Synchronization (`OrchCloudViewModel`, `app/build.gradle.kts`)**:
   - Enable `libs.firebase.firestore` and observe `/orch_cloud_sessions` (filtered by `user_id == uid`), `/orch_cloud_sessions/{sessionId}/steps`, and `/budget/{uid}` via real-time snapshots alongside `/cloud/*` REST/SSE calls, and sync the user's FCM token to `/users/{uid}`.
2. **UTF-8 Line-Buffered SSE Streaming (`OrchCloudApiClient` & `OrchCloudSseBlockSplitter`)**:
   - Replace `ByteArray(4096)` raw chunk decoding with `BufferedSource.readUtf8Line()` loop inside `executeSseCall` so multi-byte UTF-8 sequences are never split.
   - Automatically resume interrupted streams via `GET /cloud/chat/resume?sessionId=&lastEventId=`.
3. **Shimmer `Working`/`Worked` & `120.dp` Auto-Collapsing Step Dropdowns (`OrchCloudApp`, `OrchCloudModels`)**:
   - Remove all duration/timestamp fields (`startedAtMs`, `finishedAtMs`, `workedSeconds`).
   - Render `"Working"` with a linear gradient shimmer animation while streaming and `"Worked"` once finished.
   - Keep both the outer `Working`/`Worked` section and each inner step item collapsed by default, constrain expanded step dropdown cards to `Modifier.heightIn(max = 120.dp)` with `verticalScroll`, and automatically collapse the entire work section when the final response arrives.
4. **Inline Composer GitHub Bar, Unified Theme & Zero Dead Code (`OrchCloudApp`, `ArtifactBottomSheet`, `Color`)**:
   - Add the inline GitHub repository input & connect bar at the top of `OrchComposerBar`.
   - Unify all composables on `Orch*` theme colors, enable cached file sharing for images in `ArtifactBottomSheet`, and strip all comments, dead code, duplicate routes, and unused dependencies.

# PIN — Voice Notes & Reminders

**Say it. Pin it. Remember it.**

PIN is a local-first native Android app for people who think out loud. Tap the
mic, speak naturally in **Bangla, Banglish, English, or any mix** —

> “কাল সকাল ১০টায় কৃষ্ণ দাদাকে ফোন দিতে হবে, মনে করাইস”
> “ei idea ta likhe rakh”
> “pinned note gula dekhaw”

— and PIN turns messy speech into the right local action:

**listen → transcribe → understand intent → extract note/date/time →
confirm only if needed → save locally → schedule reminder → open/search/pin
when asked.**

No account. No cloud. No AI API. No recurring cost.

## What it does (V1)

- **Voice capture** through the device's Android speech service, with a
  first-class **typed fallback** that goes through the exact same parser.
- **Deterministic local intent engine** (no LLM): create note, create
  reminder, note+reminder combined, pin/unpin, show pinned, open/search notes,
  show today's notes/reminders, delete (always confirmed).
- **Bangla/Banglish normalization**: Bengali numerals (১০টায় → 10:00),
  spelling variants (`shokal/sokal`, `mone korais/mone koriye dio`), mixed
  script (`কাল 10 tay client meeting reminder dis`).
- **Date/time parser** tuned for Bangladesh usage: আজ/কাল/পরশু, day periods
  (সকাল/দুপুর/বিকাল/সন্ধ্যা/রাত), `10 tay`, `১০:৩০`, `9 pm`, `10 minute por`.
- **Never guesses an ambiguous time.** “kal shokale mone korais” asks
  *what time?* with quick choices; refusing saves a plain note and says no
  reminder was scheduled. Explicit past times are flagged, never scheduled.
- **Command words are stripped, meaning is kept.** “এইটা লিখে রাখ আর আমারে মনে
  করাইস” never appears inside the saved note; the raw transcript is kept on
  the note for reference. When clean extraction is uncertain, PIN keeps more
  of the original rather than inventing text.
- **Local reminders** via `AlarmManager` with exact-alarm handling for
  Android 12+ (user-revocable `SCHEDULE_EXACT_ALARM`; graceful ~10-minute
  window fallback when not granted), high-priority notification with sound and
  vibration, and tap-to-open-the-exact-note.
- **Reboot resilience**: future reminders are re-armed after boot, app update,
  or time/timezone change; ones that expired while the phone was off are
  marked *Missed*, never fired late and never re-fired.
- **Pinning** as a core identity feature, local search across title, body,
  transcript, and a Banglish-normalized index, and lightweight Undo for save,
  pin/unpin, reminder changes and delete.

## Zero-cost / privacy model

- Notes and reminders live in a Room database in private app storage.
- `allowBackup=false` + explicit backup/transfer exclusion rules: notes stay
  on the phone. There is no export in V1.
- No analytics, no crash reporting, no network calls from app code.
  The only permission touching the network is `ACCESS_NETWORK_STATE`, used to
  pick the on-device recognizer when offline.
- Speech recognition uses whatever Android speech service the device
  provides. On Android 12+ devices with on-device recognition, PIN uses it
  explicitly when offline. On some phones the device's recognizer may use the
  internet — PIN does not claim otherwise (the About sheet shows the honest
  capability status).

### Offline behavior

| | Voice | Notes / pin / search / reminders |
|---|---|---|
| Internet ON | device speech service | fully local |
| OFF + on-device speech | works locally | fully local |
| OFF + no on-device speech | clear message, type instead | fully local |

## Build & test

Requirements: JDK 17+, Android SDK (platform 36, build-tools 36).

```bash
./gradlew test              # host unit tests (parser, date/time, titles)
./gradlew assembleDebug     # debug APK
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Architecture

Single `:app` module, plain manual wiring (no DI framework):

```
domain/parser/    BanglaDigits, Vocabulary (normalization dictionary),
                  TextNormalizer, PhraseMatcher, DateTimeParser,
                  IntentParser, TitleGenerator        ← pure JVM, unit-tested
data/             NoteEntity, NoteDao, PinDatabase (Room v1, no destructive
                  migration), NoteRepository (search ranking, reminder state)
reminder/         AlarmScheduler, ReminderReceiver, BootReceiver,
                  NotificationHelper
speech/           SpeechController (interface) + AndroidSpeechController
ui/               Compose: Home / Notes / Reminders / Note detail,
                  CaptureSheet state machine (listen → confirm/clarify),
                  dark graphite theme, bundled vector icons
```

- Kotlin, Jetpack Compose (Material 3 foundations, custom visual system),
  Room + KSP, Coroutines/Flow, Navigation Compose, AlarmManager.
- minSdk 26, targetSdk 36. Dark theme only — a deliberate single look.

## Permissions

| Permission | When asked | Why |
|---|---|---|
| `RECORD_AUDIO` | first mic tap | voice capture; denial keeps text mode working |
| `POST_NOTIFICATIONS` | first reminder save (Android 13+) | reminder alerts |
| `SCHEDULE_EXACT_ALARM` | settings link, only if revoked | exact reminder times |
| `RECEIVE_BOOT_COMPLETED` | install-time (normal) | re-arm reminders after reboot |
| `ACCESS_NETWORK_STATE` | install-time (normal) | choose on-device recognizer offline |

Nothing else — no contacts, location, storage, or camera.

## Known limitations (honest edition)

- Speech quality depends entirely on the device's recognizer; Bangla
  on-device models are not present on every phone. Voice falls back to typing
  with a clear message — the app never becomes useless.
- The parser covers the focused domain well but is deliberately not a general
  NLU system: `10 ta` is read as a time when a reminder is requested, so
  “kal 10 ta dim ante hobe mone korais” (10 eggs) schedules 10:00 — the
  confirmation step exists exactly for this.
- Reminder delivery timing beyond `AlarmManager`'s guarantees (aggressive
  OEM battery managers, Doze) is not — and cannot honestly be — promised.
- UI copy is English with Bangla examples; notes themselves are script-agnostic.

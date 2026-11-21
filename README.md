# Batbloom

A night-garden focus timer for Android. Start a session and a plant grows under the moon. Leave for a distracting app and it wilts.

- **Focus sessions** of 10–120 min, or **Pomodoro** cycles (focus rounds with short and long breaks that start on their own).
- **Leave detection.** With Usage access on, opening an app that isn't on your allow list gives you 10 seconds to come back. After that the plant wilts. The home screen, phone calls and the lock screen are always allowed.
- **Garden** of every session, **stats** (today, this week, streak, success rate, minutes per tag), and **FMGE** tracking: a subject picker, minutes per subject (subjects not started yet are listed), a daily goal and an exam countdown.
- **Craving button:** a 10-minute session with coping tips. You can log how strong the craving was and what set it off. It sits next to a smoke-free card showing your quit day, cigarettes not smoked, money saved, your reason for quitting and a treat you're saving for.
- **Quit tab** for a varenicline course. Defaults: a 12-week plan, 0.5 mg in the morning on Days 1–3, 0.5 mg twice a day on Days 4–7, then 1 mg twice a day to Day 84. Everything is editable in Settings.
  - Dose reminders use exact alarms that are set again after a reboot. Each one has Taken / Skip / Snooze buttons and the right mg for that day.
  - A dose log, missed-dose advice (skip it if the next dose is close, never take two at once) and tablet stock with a warning about 4 days before it runs out.
  - A smoke-free tracker: time, cigarettes not smoked, money saved and life regained, plus a health-milestone timeline (approximate).
  - Craving SOS, a craving chart, a kind slip log and a side-effect diary with clear "contact your doctor" warnings.
  - Badges that pay coins. Finishing the course unlocks the Dawnbreath Lily.
  - It's a tracker and **does not replace medical advice**.
- **Coins** unlock seven original night plants. There is nothing to buy.
- **Widgets:** a 4×2 widget with the live countdown or today's focus, your FMGE goal and countdowns, plus Craving and Focus buttons. There's also a 1×1 one-tap craving button.
- Dark theme, no ads, **no internet permission**. Everything is stored on the phone with Room.

## Build

```
gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
gradlew testDebugUnitTest    # rule and stats tests
```

Kotlin and Jetpack Compose, minSdk 26, AGP 9.2, Room through KSP.

# SMSGuard

Android app that turns any phone into a remotely controllable safety device via SMS commands.

- `#LOCATE#PIN#` — reply with the device's current GPS location
- `#ALARM#PIN#` — play a siren alarm
- `#STOP#PIN#` — stop the siren

Commands are authenticated with a user-defined PIN (4–8 digits) set in the app.

## Features

- Jetpack Compose UI (support + compose-based screens)
- SMS broadcast receiver with high-priority handling
- Foreground siren service with media playback
- Battery beacon and notification helpers
- Location replies with Google Maps links

## Build

```sh
./gradlew assembleDebug
```

## License

MIT — see [LICENSE](LICENSE).
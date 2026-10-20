# SMSGuard

Android app that turns any phone into a remotely controllable safety device via SMS commands.

`#LOCATE#PIN#`: reply with the device's current GPS location

`#ALARM#PIN#`: play a siren alarm

`#STOP#PIN#`: stop the siren

Commands are authenticated with a user-defined PIN (4–8 digits) set in the app.

Only numbers saved in the app's **Trusted Contacts** list may run commands. Contacts can be
picked from the phone's address book or added manually. A command text from any other number is
ignored without a reply and recorded in the **Suspicious Numbers** list, along with the number and
the time it was sent.

## Pairing with the dashboard

Open the **Pair** tab, set the dashboard server address, and scan the QR code shown by the
dashboard. The app accepts both link formats:

- `http://<server>:3000/link/<token>`
- `smsguard://pair?token=<token>`

Use `http://10.0.2.2:3000` on the Android emulator. On a real phone, use the computer's LAN
address (for example `http://192.168.1.20:3000`). `localhost` and `127.0.0.1` point at the phone
itself and are rejected.

The app POSTs the device report to `/api/devices/pairings/<token>/claim`. Android 10+ often hides
the IMEI from regular apps; when that happens the app asks you to enter the value shown by
`*#06#` instead of submitting a made-up number. If the dashboard QR still contains a `localhost`
URL, set `NEXT_PUBLIC_APP_URL=http://<your-pc-lan-ip>:3000` in `.env.local` and restart
`next dev`.

APK files are available at the releases section.
Creating an issue or PR is very welcome. 

## License

MIT — see [LICENSE](LICENSE).

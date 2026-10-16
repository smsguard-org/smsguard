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

APK files are available at the releases section.
Creating an issue or PR is very welcome. 

## License

MIT — see [LICENSE](LICENSE).

# Install R Reader on a phone (ADB)

Release builds are installed with ADB ([ADR 0007](../decisions/0007-distribution-via-adb.md)).
Menu names below are for Samsung One UI; Portuguese labels are in parentheses.

## 1. Prepare the phone (before each install)

Debugging is turned off again after every install (step 4), so these steps
are repeated each time.

1. **Enable developer options:** Settings › About phone › Software information ›
   tap **Build number** seven times
   (Configurações › Sobre o telefone › Informações de software › Número de compilação).
2. **Enable debugging:** Settings › Developer options › **USB debugging**
   (Opções do desenvolvedor › Depuração USB). For cable-free installs also
   enable **Wireless debugging** (Depuração sem fio).
3. **Samsung Auto Blocker** blocks installs over USB. Turn it off while
   installing: Settings › Security and privacy › **Auto Blocker**
   (Segurança e privacidade › Bloqueador automático). Turn it back on afterwards.

## 2. Connect

**USB:** plug the phone in, accept the "Allow USB debugging?" prompt, then:

```bash
adb devices            # the phone must be listed as "device"
```

**Wi-Fi (same network):** on the phone open Wireless debugging › *Pair device
with pairing code*, then:

```bash
adb pair <phone-ip>:<pairing-port>     # enter the 6-digit code
adb connect <phone-ip>:<debug-port>    # port shown on the Wireless debugging screen
adb devices
```

## 3. Install or update

```bash
./gradlew :app:assembleRelease                         # see release-signing.md
adb install -r app/build/outputs/apk/release/app-arm64-v8a-release.apk   # phones (arm64)
```

Builds are split per CPU architecture: phones use the `arm64-v8a` APK, the
emulator the `x86_64` one. `-r` replaces the installed app and **keeps its data**. If Google Play Protect
warns about an unknown app, choose *More details › Install anyway*.

**Only release APKs go on the phone.** A debug build is debuggable: anyone
holding the phone with debugging on can read the app's private data (books,
progress) through `adb`. It is also signed with the debug key, so replacing
it with a release later fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` and
forces an uninstall, which deletes the library and progress.

## 4. After installing (every time)

- **Battery:** Settings › Apps › R Reader › Battery › **Unrestricted**
  (Bateria › Sem restrições), so long listening sessions are not stopped.
- Run the device checklist in [testing.md](../testing.md).
- **Turn debugging off**, so no computer can control the phone or read app
  data through `adb` until the next install: in Settings › Developer options
  (Opções do desenvolvedor), turn off **Wireless debugging** (Depuração sem
  fio) and **USB debugging** (Depuração USB), tap **Revoke USB debugging
  authorizations** (Revogar autorizações de depuração USB) so no computer
  stays trusted, then turn **Developer options** off with the switch at the
  top of that screen. The next install starts again at step 1.
- Turn Auto Blocker back on.

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `unauthorized` in `adb devices` | Prompt not accepted | Unlock the phone, accept the prompt, replug |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | APK signed with a different key | Use the original release key. Uninstalling deletes the user's library and progress — only as a last resort |
| Install blocked silently on Samsung | Auto Blocker on | Turn it off temporarily |
| Playback stops after a while with the screen off | Battery optimization | Set battery to Unrestricted |

# Photon Beat
Android audio-reactive light show. SDK 35, Java 17, Gradle 8.9. Media capture or microphone. Torch opt-in, max 2 pulses per second. Debug APK in Actions artifacts.

0.8 adds vector singing lips driven by speech-band energy (audio-reactive articulation, not word-level lip sync), and a dance floor with poses anchored to audio-thread kick timestamps. Select «Поющие губы» or «Танцпол» for continuous characters; other scenes show them periodically. Both overlays have switches. The animation speed slider only changes the background: dancers keep the musical timing. Existing kaleidoscope, gestures and effects remain available.

Rhythm regression check (also run in CI):
```sh
mkdir -p /tmp/photon-beat-tests
javac -d /tmp/photon-beat-tests app/src/main/java/com/playeverywhere/photonbeat/RhythmTracker.java tests/RhythmTrackerTest.java
java -cp /tmp/photon-beat-tests com.playeverywhere.photonbeat.RhythmTrackerTest
```

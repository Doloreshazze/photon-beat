# Photon Beat
Android audio-reactive light show. SDK 35, Java 17, Gradle 8.9. Media capture or microphone. Torch opt-in, max 2 pulses per second. Debug APK in Actions artifacts.

In 0.9, the singing lips appear over every scene when a vocal phrase is detected. They stay visible between syllables while the mouth closes. Select «Поющие губы» to keep the large central mouth on screen throughout the track. «Задержка губ» adds 0–400 ms of visual delay for audio outputs such as Bluetooth headphones. The default is 0 ms.

Articulation follows 10 ms hops of captured audio from the media stream or microphone. A speech viseme model, a sung vowel model, and vocal-tract resonance estimates drive the jaw, pucker, spread, teeth, lip contact and tongue. The app estimates the lead voice from the mix. Dense instrumentation and overlapping singers can produce an incorrect shape or a false vocal detection. Model sources and credits ship in the APK assets.

The dance floor follows detected kick timestamps. «Танцпол» keeps the dancers visible. Both character overlays have switches. Gestures and the animation speed control affect the visual effects independently of lip timing.

Audio regression checks (also run in CI)
```sh
mkdir -p /tmp/photon-beat-tests
src=app/src/main/java/com/playeverywhere/photonbeat
javac -encoding UTF-8 -d /tmp/photon-beat-tests "$src"/{RhythmTracker,AudioFft,VisemeModel,VowelFormants,VoiceActivityModel,SungVowels,MouthPose,LipTimeline,LipSync}.java tests/*.java
java -cp /tmp/photon-beat-tests com.playeverywhere.photonbeat.RhythmTrackerTest
java -cp /tmp/photon-beat-tests com.playeverywhere.photonbeat.LipSyncTest app/src/main/assets
```

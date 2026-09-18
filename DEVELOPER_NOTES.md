# Android implementation notes

The supplied README made unsupported claims about ANC, call isolation, benchmarks
and measured latency. The revised README states the implemented scope. This is
microphone monitoring, not a virtual microphone driver or anti-noise system.

Changes:
- Manifest microphone/audio/foreground-service permissions and service registration.
- API 26 minimum; AGP 8.10.1 for API 36; conventional Flutter build output directories.
- Explicit local localization generation and removal of absent asset declarations.
- Correct missing AudioMode test import; cancel battery polling on unsubscribe.
- Remove unsupported paywall, fake audio demo and inactive Bluetooth auto-start UI.
- Native initialization failures reach Flutter; asynchronous audio failures emit errors.
- Restart audio objects on sample-rate/frame-size/preset changes.
- Use immutable frame size inside the read loop; distinguish buffer bytes from samples.
- Release hardware effects, recorder and output; stop when activity is destroyed.
- Require headphone output and choose the built-in microphone explicitly.
- Recheck actual routes and input silencing; stop on detected route changes.
- Preserve the original energy gate and biquad filters; no new trained model.
- Non-sticky service avoids microphone restarts without a user action.

The class name SpectralSubtractor is historical. The algorithm uses one RMS-derived
frame gain; it does not compute an FFT or subtract frequency-bin spectra. The
preset mid-frequency value has no defined gain or Q and remains unused. Hardware
VOICE_COMMUNICATION preprocessing may still differ by device, even in Monitor mode.

Foreground-service ownership remains coupled to the activity's engine. It survives
ordinary backgrounding only while that activity/process remains alive. Full
service-owned audio with binding and persistent state would be a separate extension.

No Android compilation, Dart analysis, Flutter tests or physical-device tests were
completed in this environment. The local build command was blocked by automatic
security review because Flutter startup attempted internal cloud-metadata access.

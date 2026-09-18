import 'dart:async';
import 'package:battery_plus/battery_plus.dart';

class BatteryMonitor {
  final Battery _battery = Battery();

  Stream<double> dropPerHourStream() {
    Timer? timer;
    int? baseLevel;
    DateTime? baseTime;
    late StreamController<double> ctrl;
    Future<void> sample() async {
      try {
        final level = await _battery.batteryLevel;
        final now = DateTime.now();
        baseLevel ??= level;
        baseTime ??= now;
        final hours = now.difference(baseTime!).inSeconds / 3600.0;
        if (hours > 0.05 && !ctrl.isClosed) {
          ctrl.add((baseLevel! - level) / hours);
        }
      } catch (_) {
        // Battery reporting is optional and must not interrupt audio.
      }
    }
    ctrl = StreamController<double>(
      onListen: () {
        unawaited(sample());
        timer = Timer.periodic(const Duration(minutes: 5), (_) => sample());
      },
      onCancel: () { timer?.cancel(); },
    );
    return ctrl.stream;
  }
}

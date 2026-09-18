import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../core/providers.dart';
import '../l10n/app_localizations.dart';
import '../models/audio_mode.dart';

class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});
  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  EqPreset _eq = EqPreset.music;
  String _version = '';

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final SharedPreferences p = ref.read(sharedPrefsProvider);
    final PackageInfo info = await PackageInfo.fromPlatform();
    if (!mounted) return;
    setState(() {
      _eq = EqPreset.values.firstWhere(
        (EqPreset e) => e.id == (p.getString('eq') ?? 'music'),
        orElse: () => EqPreset.music,
      );
      _version = info.version;
    });
  }

  Future<void> _saveEq(EqPreset e) async {
    final SharedPreferences p = ref.read(sharedPrefsProvider);
    await p.setString('eq', e.id);
    try {
      await ref.read(audioEngineProvider).setEqPreset(e);
      if (mounted) setState(() => _eq = e);
    } catch (error) {
      await ref.read(engineControllerProvider.notifier).stop();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.toString())));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final AppLocalizations t = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(t.settings)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: <Widget>[
          Card(
            child: Column(
              children: <Widget>[
                ListTile(title: Text(t.eqPreset)),
                for (final EqPreset e in EqPreset.values)
                  RadioListTile<EqPreset>(
                    value: e,
                    groupValue: _eq,
                    onChanged: (EqPreset? v) { if (v != null) _saveEq(v); },
                    title: Text(_eqLabel(t, e)),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          const SizedBox(height: 24),
          Center(child: Text(t.version(_version), style: Theme.of(context).textTheme.bodySmall)),
        ],
      ),
    );
  }

  String _eqLabel(AppLocalizations t, EqPreset e) {
    switch (e) {
      case EqPreset.call: return t.eqCall;
      case EqPreset.music: return t.eqMusic;
      case EqPreset.movie: return t.eqMovie;
    }
  }
}

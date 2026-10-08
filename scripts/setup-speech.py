#!/usr/bin/env python3
"""Import local STT credentials into a debug build through private app storage."""
import argparse
import json
from pathlib import Path
import re
import shlex
import subprocess
import sys


def read_env(path):
    values = {}
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        if '=' not in line:
            raise ValueError('Expected KEY=value in the env file')
        name, value = line.split('=', 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in ('"', "'"):
            value = value[1:-1]
        values[name.strip()] = value
    return values


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--env', type=Path, default=Path(__file__).resolve().parents[1] / '.env')
    args = parser.parse_args()
    values = read_env(args.env)
    serial = values['ANDROID_SERIAL']
    package = values['ANDROID_PACKAGE']
    if not serial or not re.fullmatch(r'rocks\.pkb\.plektra(?:\.unsovereign)?\.nightly', package):
        raise ValueError('Expected a Pixel serial and a Plektra Nightly package')
    keys = [values.get(name, '').strip() for name in ('OPENROUTER_API_KEY', 'OPENAI_API_KEY')]
    if any(not key or any(c.isspace() for c in key) for key in keys):
        raise ValueError('Both provider keys must be filled in without whitespace')
    provider = values.get('SPEECH_PROVIDER', 'OPENROUTER')
    silence_ms = int(values.get('SPEECH_SILENCE_MS', '3000'))
    if provider not in ('OPENROUTER', 'OPENAI') or silence_ms not in (0, 1500, 3000, 5000):
        raise ValueError('Invalid speech provider or pause')
    payload = json.dumps({
        'openrouter_key': keys[0], 'openai_key': keys[1],
        'provider': provider, 'silence_ms': silence_ms,
        'openrouter_model': values.get('OPENROUTER_STT_MODEL', 'openai/whisper-large-v3-turbo'),
        'openai_model': values.get('OPENAI_STT_MODEL', 'gpt-live-transcribe'),
    })
    adb = ['adb', '-s', serial, 'shell']
    private_file = 'cache/speech-setup.json'
    try:
        write = subprocess.run(adb + ['run-as', package, 'sh', '-c',
            shlex.quote('umask 077; cat > ' + private_file)],
            input=payload, text=True, capture_output=True, timeout=30)
        if write.returncode:
            raise RuntimeError('Could not write private configuration; a debuggable build is required')
        result = subprocess.run(adb + ['am', 'broadcast', '-n', package + '/rocks.pkb.plektra.SpeechSetupReceiver'],
            capture_output=True, text=True, timeout=30)
        if result.returncode or 'result=0' not in result.stdout or 'both encrypted keys verified' not in result.stdout:
            raise RuntimeError('Device did not confirm credential import')
        print('Both encrypted keys verified on ' + serial + '. Provider: ' + provider + '.')
    finally:
        subprocess.run(adb + ['run-as', package, 'rm', '-f', private_file],
            capture_output=True, timeout=30)


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, KeyError, RuntimeError, subprocess.TimeoutExpired):
        print('Speech setup failed. Check the env fields, device connection and debug build.', file=sys.stderr)
        sys.exit(1)

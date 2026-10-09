#!/usr/bin/env python3
"""Sign an unsigned Play bundle with its channel-specific on-token upload key."""
import argparse
import getpass
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = Path.home() / '.config/pkb-rocks/google-play/upload-signing/manifest.json'


def signing_identity(channel):
    from cryptography import x509
    from cryptography.hazmat.primitives import hashes, serialization
    from yubikit.piv import KEY_TYPE, PIN_POLICY, TOUCH_POLICY
    data = json.loads(MANIFEST.read_text())['channels'][channel]
    package = 'rocks.pkb.plektra.unsovereign' + ('.nightly' if channel == 'nightly' else '')
    assert data['package'] == package, 'Wrong channel package'
    slot = int(data['slot'], 16)
    assert slot == (0x84 if channel == 'nightly' else 0x83), 'Wrong channel slot'
    pin = PIN_POLICY.NEVER if channel == 'nightly' else PIN_POLICY.ONCE
    touch = TOUCH_POLICY.NEVER if channel == 'nightly' else TOUCH_POLICY.ALWAYS
    cert = x509.load_pem_x509_certificate(Path(data['certificate']).read_bytes())
    assert cert.fingerprint(hashes.SHA256()).hex() == data['certificate_sha256'], 'Certificate changed'
    return data, cert, slot, pin, touch


def hardware_signature(channel, message):
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import padding
    from ykman.device import list_all_devices
    from yubikit.core.smartcard import SmartCardConnection
    from yubikit.piv import PivSession, KEY_TYPE, SLOT
    data, cert, slot, pin, touch = signing_identity(channel)
    candidates = [dev for dev, info in list_all_devices() if info.serial == data['token_serial']]
    assert len(candidates) == 1, 'Expected YubiKey is not connected'
    with candidates[0].open_connection(SmartCardConnection) as connection:
        session = PivSession(connection)
        metadata = session.get_slot_metadata(SLOT(slot))
        assert metadata.generated and metadata.key_type == KEY_TYPE.RSA4096
        assert metadata.pin_policy == pin and metadata.touch_policy == touch, 'Hardware policy changed'
        assert session.get_certificate(SLOT(slot)).public_bytes(serialization.Encoding.DER) == cert.public_bytes(serialization.Encoding.DER)
        assert metadata.public_key.public_numbers() == cert.public_key().public_numbers()
        if channel == 'stable':
            session.verify_pin(getpass.getpass('Stable PIV PIN: '))
            print('Touch YK1 to sign the Stable Play bundle.', file=sys.stderr)
        signature = session.sign(SLOT(slot), KEY_TYPE.RSA4096, message, hashes.SHA256(), padding.PKCS1v15())
        cert.public_key().verify(signature, message, padding.PKCS1v15(), hashes.SHA256())
        return signature


def check_unsigned(path):
    with zipfile.ZipFile(path) as bundle:
        names = bundle.namelist()
        assert len(names) == len(set(names)), 'Duplicate ZIP entries'
        assert 'BundleConfig.pb' in names and 'base/manifest/AndroidManifest.xml' in names, 'Not an AAB'
        assert not any(n.upper().startswith('META-INF/') and n.upper().endswith(('.SF', '.RSA', '.EC', '.DSA')) for n in names), 'Bundle is already signed'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--channel', choices=('stable', 'nightly'), required=True)
    parser.add_argument('--rsa-sign', action='store_true', help=argparse.SUPPRESS)
    parser.add_argument('--verify', action='store_true')
    parser.add_argument('input', nargs='?', type=Path)
    parser.add_argument('output', nargs='?', type=Path)
    args = parser.parse_args()
    if args.rsa_sign:
        sys.stdout.buffer.write(hardware_signature(args.channel, sys.stdin.buffer.read()))
        return
    data, _, _, _, _ = signing_identity(args.channel)
    if not args.input or not args.input.is_file():
        parser.error('Input bundle is required')
    java_home = Path(os.environ.get('JAVA_HOME', '/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home'))
    java = java_home / 'bin/java'
    source = ROOT / 'scripts/signing/SignPlayBundle.java'
    if args.verify:
        subprocess.run([str(java), str(source), 'verify', str(args.input.resolve()), data['certificate']], check=True)
        return
    if not args.output or args.output.exists():
        parser.error('A new output path is required')
    if args.channel == 'stable' and not sys.stdin.isatty():
        parser.error('Stable signing requires your interactive Terminal for PIN and touch')
    check_unsigned(args.input)
    # Fail before writing output if the bundle belongs to another channel.
    bundletool = Path.home() / '.local/share/agent-tools/android/bundletool.jar'
    package = subprocess.check_output([str(java), '-jar', str(bundletool), 'dump', 'manifest', '--bundle='+str(args.input.resolve()), '--xpath=/manifest/@package'], text=True).strip()
    assert package == data['package'], 'Bundle belongs to another channel'
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='plektra-aab-', dir=args.output.parent) as directory:
        signed = Path(directory) / 'signed.aab'
        subprocess.run([str(java), str(source), 'sign', str(args.input.resolve()), str(signed), data['certificate'], sys.executable, str(Path(__file__).resolve()), args.channel], check=True)
        subprocess.run([str(java), str(source), 'verify', str(signed), data['certificate']], check=True)
        # Exclusive creation prevents overwriting another release.
        with args.output.open('xb') as output:
            output.write(signed.read_bytes())
    digest = hashlib.sha256(args.output.read_bytes()).hexdigest()
    args.output.with_suffix('.aab.sha256').write_text(digest+'  '+args.output.name+'\n')
    print('Signed and verified '+str(args.output))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('Signing failed: '+str(error), file=sys.stderr)
        sys.exit(1)

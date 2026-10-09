#!/usr/bin/env python3
"""Apply reversible preferences through the shell-only debug receiver."""
import argparse,json,shlex,subprocess,sys

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--device',required=True)
    parser.add_argument('--package',default='rocks.pkb.plektra.nightly')
    commands=parser.add_subparsers(dest='action',required=True)
    commands.add_parser('list')
    setter=commands.add_parser('set');setter.add_argument('--file',required=True);setter.add_argument('--theme')
    commands.add_parser('restore')
    args=parser.parse_args()
    command=['am','broadcast','-n',args.package+'/rocks.pkb.plektra.ShowcaseReceiver']
    if args.action=='set':
        with open(args.file,encoding='utf-8') as stream: values=json.load(stream)
        if not isinstance(values,dict):parser.error('Settings file must contain a JSON object')
        command+=['--es','settings',json.dumps(values,ensure_ascii=True,separators=(',',':'))]
        if args.theme:command+=['--es','theme',args.theme]
    else:command+=['--ez',args.action,'true']
    result=subprocess.run(['adb','-s',args.device,'shell',shlex.join(command)],capture_output=True,text=True,timeout=30)
    if result.returncode or 'result=1' in result.stdout or 'data=' not in result.stdout:
        print(result.stdout+result.stderr,file=sys.stderr);return 1
    print(result.stdout.strip());return 0
if __name__=='__main__':raise SystemExit(main())

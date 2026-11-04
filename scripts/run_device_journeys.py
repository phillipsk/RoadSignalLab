#!/usr/bin/env python3
"""Run the disposable lab fixtures without reinstalling between process checks."""
from pathlib import Path
import os
import re
import subprocess

root = Path(__file__).resolve().parent.parent
output = root / 'build/device-results'
output.mkdir(parents=True, exist_ok=True)
adb = ['adb']
serial = os.environ.get('ANDROID_SERIAL')
if serial:
    adb += ['-s', serial]
else:
    devices = subprocess.check_output(['adb', 'devices'], text=True).splitlines()[1:]
    ready = [line.split()[0] for line in devices if line.endswith('\tdevice')]
    if len(ready) != 1:
        raise SystemExit('Attach exactly one test emulator, or set ANDROID_SERIAL explicitly.')
    serial = ready[0]
    adb += ['-s', serial]

if not serial.startswith('emulator-'):
    raise SystemExit('These destructive lab fixtures require a disposable emulator.')


def command(*args):
    return subprocess.run(adb + list(args), check=True, capture_output=True, text=True).stdout


def instrument(label, package, expected, test_class=None):
    args = ['shell', 'am', 'instrument', '-w']
    if test_class:
        args += ['-e', 'class', test_class]
    args += [package + '/androidx.test.runner.AndroidJUnitRunner']
    result = subprocess.run(adb + args, capture_output=True, text=True)
    text = result.stdout + result.stderr
    (output / (label + '.txt')).write_text(text)
    passed = re.search(r'OK \((\d+) tests?\)', text)
    if result.returncode or not passed or int(passed.group(1)) != expected:
        print(text, flush=True)
        raise SystemExit('Instrumentation failed: ' + label)
    print(f'{label}: {expected} passed', flush=True)


command('install', '-r', str(root / 'telematics-sdk/build/outputs/apk/androidTest/debug/telematics-sdk-debug-androidTest.apk'))
instrument('sdk', 'io.roadsignal.sdk.test', 11)
command('install', '-r', str(root / 'sample-app/build/outputs/apk/debug/sample-app-debug.apk'))
command('install', '-r', str(root / 'sample-app/build/outputs/apk/androidTest/debug/sample-app-debug-androidTest.apk'))
package = 'io.github.phillipsk.roadsignal.lab'
instrument('showcase', package + '.test', 1, 'io.roadsignal.app.ShowcaseJourneyTest')
# Do not invoke connectedAndroidTest again here: runner installation/cleanup may
# remove the fixture. Force-stop preserves storage while killing the process.
command('shell', 'am', 'force-stop', package)
instrument('cold-process', package + '.test', 1, 'io.roadsignal.app.ColdStartTest')
instrument('device-and-privacy', package + '.test', 1, 'io.roadsignal.app.DeviceAndPrivacyTest')
captures = root / 'build/captures'
captures.mkdir(parents=True, exist_ok=True)
command('pull', '/sdcard/Android/data/' + package + '/files/showcase', str(captures))
print('All 14 Android cases passed. Native screenshots and runner logs saved.', flush=True)

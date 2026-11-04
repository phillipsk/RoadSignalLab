#!/usr/bin/env python3
from pathlib import Path
import json,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parent.parent
paths=list((root/'sample-app/build/intermediates/merged_manifests/release').rglob('AndroidManifest.xml'))
if len(paths)!=1:raise SystemExit('Build the sample release manifest first')
m=ET.parse(paths[0]).getroot();a='{http://schemas.android.com/apk/res/android}'
permissions=sorted(x.get(a+'name') for x in m.findall('uses-permission'))
assert 'android.permission.ACCESS_BACKGROUND_LOCATION' not in permissions
assert 'android.permission.INTERNET' not in permissions
service=m.find("application/service[@"+a+"name='io.roadsignal.sdk.CaptureService']")
assert service is not None and service.get(a+'exported')=='false' and service.get(a+'foregroundServiceType')=='location' and service.get(a+'stopWithTask')=='true'
boot=[]
for receiver in m.findall('application/receiver'):
 if any(action.get(a+'name')=='android.intent.action.BOOT_COMPLETED' for action in receiver.findall('intent-filter/action')):boot.append(receiver.get(a+'name'))
assert all(name.startswith('androidx.work.') for name in boot)
report={'permissions':permissions,'bootRescheduleReceivers':boot,'captureAutoStartAtBoot':False,'explicitCaptureWakeLock':False,'note':'WorkManager contributes transitive wake-lock/boot permissions; this is not an app-wide zero-wake-lock claim.'}
out=root/'build/reports/manifest-surface.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(report,indent=2)+'\n')
print('Merged release manifest verified, including WorkManager transitive surface.')

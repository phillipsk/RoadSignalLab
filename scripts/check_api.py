#!/usr/bin/env python3
"""JVM descriptor snapshot for the deliberately supported Android/bridge API, complements core ABI validation."""
from pathlib import Path
import os,subprocess,sys,tempfile,zipfile,difflib
root=Path(__file__).resolve().parent.parent
javap=Path(os.environ.get('JAVA_HOME',''))/'bin/javap'
command=str(javap) if javap.exists() else 'javap'
classes={
 'telematics-sdk':['io.roadsignal.sdk.RoadSignal','io.roadsignal.sdk.RoadSignal$Companion','io.roadsignal.sdk.SdkResult','io.roadsignal.sdk.SdkSnapshot','io.roadsignal.sdk.SensorCapability','io.roadsignal.sdk.SyncReport','io.roadsignal.sdk.UploadSimulation','io.roadsignal.sdk.JavaRoadSignalClient','io.roadsignal.sdk.JavaRoadSignalClient$Callback','io.roadsignal.sdk.JavaRoadSignalClient$StateListener'],
 'cmt-bridge':['io.roadsignal.cmt.PartnerTelematicsPort','io.roadsignal.cmt.MockCmtAdapter','io.roadsignal.cmt.UnconfiguredCmtAdapter','io.roadsignal.cmt.CmtIntegrationInfo']}
update='--update' in sys.argv
failed=False
for module,names in classes.items():
 aar=root/module/'build/outputs/aar'/f'{module}-release.aar'
 if not aar.exists(): raise SystemExit(f'Build {module} release AAR first.')
 with tempfile.TemporaryDirectory() as temporary:
  jar=Path(temporary)/'classes.jar'
  with zipfile.ZipFile(aar) as z: jar.write_bytes(z.read('classes.jar'))
  lines=[]
  for name in names:
   output=subprocess.check_output([command,'-classpath',str(jar),'-public','-s',name],text=True)
   skip=False
   for line in output.splitlines():
    if line.startswith('Compiled from'):continue
    if '$telematics_sdk' in line: skip=True;continue
    if skip and line.strip().startswith('descriptor:'): skip=False;continue
    lines.append(line.rstrip())
  text='\n'.join(lines)+'\n';baseline=root/'api'/f'{module}.api'
  if update: baseline.parent.mkdir(exist_ok=True);baseline.write_text(text)
  elif not baseline.exists() or baseline.read_text()!=text:
   print(''.join(difflib.unified_diff(baseline.read_text().splitlines(True) if baseline.exists() else [],text.splitlines(True),fromfile=str(baseline),tofile='current')));failed=True
if failed:raise SystemExit('Public API changed. Review compatibility; do not auto-accept a baseline update.')
print('Android and bridge public JVM API baselines verified.' if not update else 'Reviewed baselines written; commit only after API review.')

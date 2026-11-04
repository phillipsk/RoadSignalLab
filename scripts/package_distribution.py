#!/usr/bin/env python3
from pathlib import Path
import hashlib,json,re,subprocess,zipfile
root=Path(__file__).resolve().parent.parent
version=re.search(r'^sdkVersion=(.+)$',(root/'gradle.properties').read_text(),re.M).group(1)
if not re.fullmatch(r'(?:0|[1-9][0-9]*)(?:\.(?:0|[1-9][0-9]*)){2}(?:-[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*)?',version):raise SystemExit('Explicit SemVer is required')
output=root/'build/distribution';output.mkdir(parents=True,exist_ok=True)
repository=root/'build/maven-repository'
archive=output/f'roadsignal-maven-{version}.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
 for p in sorted(repository.rglob('*')):
  if p.is_file():
   info=zipfile.ZipInfo(str(p.relative_to(repository)),date_time=(2026,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,p.read_bytes())
try:commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True,stderr=subprocess.DEVNULL).strip()
except subprocess.CalledProcessError:commit='uncommitted-local-build'
files=[p for p in sorted(repository.rglob('*')) if p.is_file()]
manifest={'schemaVersion':1,'sdkVersion':version,'sourceCommit':commit,'sourceTreeDirty':bool(subprocess.check_output(['git','status','--porcelain'],cwd=root,text=True)),'provenanceType':'local unsigned build manifest; not a signed SLSA attestation','files':[{'path':str(p.relative_to(repository)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'bytes':p.stat().st_size} for p in files]}
(output/'artifact-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
(output/'SHA256SUMS').write_text(''.join(f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n" for p in [archive,output/'artifact-manifest.json']))
print(f'Distribution: {archive}; {len(files)} Maven files. Published-dependency consumer built separately.')

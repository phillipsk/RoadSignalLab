#!/usr/bin/env python3
from pathlib import Path
import re,sys
root=Path(__file__).resolve().parent.parent
errors=[]
for file in [root/'README.md',*root.glob('docs/**/*.md'),root/'AGENTS.md',root/'CLAUDE.md']:
 if not file.exists():errors.append(f'Missing {file.name}');continue
 for link in re.findall(r'!?\[[^\]]*\]\(([^)]+)\)',file.read_text()):
  if re.match(r'^[a-z]+:',link) or link.startswith('#'):continue
  path=link.split('#')[0].strip('<>')
  if path and not (file.parent/path).exists():errors.append(f'{file.relative_to(root)}: unresolved {link}')
if errors:raise SystemExit('\n'.join(errors))
print('All local Markdown/image links resolve.')

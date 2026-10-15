#!/usr/bin/env python3
"""Public APK signing delta, covering signature blocks and signer ZIP alignment.
No private key enters CI; SHA-256 leases bind the delta to one exact APK.
Requires standard bsdiff/bspatch (Debian bsdiff package).
"""
import sys,json,base64,hashlib,subprocess,tempfile,os,shutil
from pathlib import Path
def digest(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def detach(unsigned,signed):
 tool=os.environ.get('BSDIFF_PATH') or shutil.which('bsdiff')
 if not tool:raise RuntimeError('bsdiff is required to prepare the public signature delta')
 with tempfile.TemporaryDirectory() as folder:
  patch=Path(folder)/'public-signature.patch'
  subprocess.run([tool,unsigned,signed,str(patch)],check=True)
  return {'format':'BSDIFF40','input_sha256':digest(unsigned),'output_sha256':digest(signed),'public_signature_delta':base64.b64encode(patch.read_bytes()).decode()}
def apply(source,proof,target):
 assert digest(source)==proof['input_sha256'],'APK changed; signature cannot be reused'
 assert proof['format']=='BSDIFF40'
 tool=os.environ.get('BSPATCH_PATH') or shutil.which('bspatch')
 if not tool:raise RuntimeError('bspatch is required; install the bsdiff package')
 with tempfile.TemporaryDirectory() as folder:
  p=Path(folder)/'public-signature.patch';p.write_bytes(base64.b64decode(proof['public_signature_delta'],validate=True));subprocess.run([tool,source,target,str(p)],check=True)
 assert digest(target)==proof['output_sha256'],'Signature delta failed output validation'
if __name__=='__main__':
 if sys.argv[1]=='detach':print(json.dumps(detach(*sys.argv[2:4])))
 else:apply(sys.argv[2],json.loads(Path(sys.argv[3]).read_text()),sys.argv[4])

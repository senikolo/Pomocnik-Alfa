#!/usr/bin/env python3
"""Move only a public APK v2/v3 signature, never a private signing key.
Input hash lease makes replay on changed APK impossible.
"""
import sys,struct,json,base64,hashlib
from pathlib import Path
def layout(b):
 e=b.rfind(b'PK\x05\x06');assert e>=0 and e+22+struct.unpack_from('<H',b,e+20)[0]==len(b)
 cd=struct.unpack_from('<I',b,e+16)[0];return cd,e
def detach(unsigned,signed):
 a=Path(unsigned).read_bytes();b=Path(signed).read_bytes();ca,ea=layout(a);cb,eb=layout(b)
 assert b[cb-16:cb]==b'APK Sig Block 42';n=struct.unpack_from('<Q',b,cb-24)[0]+8;begin=cb-n
 assert a[:ca]==b[:begin], 'Signer changed ZIP entries; cannot detach'
 assert a[ca:ea]==b[cb:eb], 'Signer changed central directory'
 result={'input_sha256':hashlib.sha256(a).hexdigest(),'output_sha256':hashlib.sha256(b).hexdigest(),'block':base64.b64encode(b[begin:cb]).decode(),'eocd':base64.b64encode(b[eb:]).decode()}
 return result
def apply(source,proof,target):
 a=Path(source).read_bytes();assert hashlib.sha256(a).hexdigest()==proof['input_sha256'],'APK changed; signature cannot be reused'
 cd,e=layout(a);out=a[:cd]+base64.b64decode(proof['block'])+a[cd:e]+base64.b64decode(proof['eocd']);assert hashlib.sha256(out).hexdigest()==proof['output_sha256'];Path(target).write_bytes(out)
if __name__=='__main__':
 if sys.argv[1]=='detach':print(json.dumps(detach(*sys.argv[2:4])))
 else:apply(sys.argv[2],json.loads(Path(sys.argv[3]).read_text()),sys.argv[4])

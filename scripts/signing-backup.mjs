// Private payload is stdin only. Never log payloads, passwords, or native errors.
import {sealBackup,openBackup} from './signing-backup-support.mjs';
try {
  let input='';for await(const chunk of process.stdin) {input+=chunk;if(input.length>2*1024*1024) throw new Error();}
  const request=JSON.parse(input);input='';
  const archive=sealBackup(request.payload,request.password);
  const restored=openBackup(archive,request.password);
  if(JSON.stringify(restored)!==JSON.stringify(request.payload)) throw new Error();
  process.stdout.write(JSON.stringify(archive));
} catch {process.stderr.write('Encrypted backup failed; private output withheld.');process.exitCode=1;}

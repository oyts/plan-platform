import test from 'node:test';
import assert from 'node:assert/strict';
import {privatePublication,sha256,PACKAGE,CERTIFICATE} from './invited-android-release.mjs';
const bytes=Buffer.from('unit test only; not a real APK');
const candidate=()=>({status:'LOCAL_SIGNED_CANDIDATE',version:'0.1.6',android:{version:'0.1.6',versionCode:10,file:'PlanCabin-0.1.6-release.apk',bytes:bytes.length,sha256:sha256(bytes),packageName:PACKAGE,certificateSHA256:CERTIFICATE,debuggable:false,cleartextTraffic:false}});
test('private metadata has no public URL; optional by default',()=>{const m=privatePublication(candidate(),bytes,'0.1.6','Authenticated updates');assert.equal(m.minimumSupportedVersionCode,1);assert.equal(m.url,undefined);assert.equal(m.versionCode,10);});
test('wrong identity, stale bytes and debug candidates rejected',()=>{for(const [key,value]of [['packageName','other'],['certificateSHA256','0'.repeat(64)],['debuggable',true],['cleartextTraffic',true],['sha256','0'.repeat(64)],['bytes',0],['file','../secret.apk']]){const c=candidate();c.android[key]=value;assert.throws(()=>privatePublication(c,bytes,'0.1.6','notes'));}});
test('invalid minimum policy and empty notes rejected',()=>{for(const min of [0,11,NaN])assert.throws(()=>privatePublication(candidate(),bytes,'0.1.6','notes',min));assert.throws(()=>privatePublication(candidate(),bytes,'0.1.6',''));});

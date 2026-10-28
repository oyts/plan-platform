import test from 'node:test';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
const key='.desktop-tools/test-signing/desktop-test.key.pub',cert='.desktop-tools/update-lab/tls/ca.pem';
function refused(args,message){const r=spawnSync('powershell.exe',['-NoProfile','-ExecutionPolicy','Bypass','-File','scripts/desktop.ps1',...args],{encoding:'utf8',windowsHide:true});assert.notEqual(r.status,0);assert.match(r.stdout+r.stderr,message);}
test('Probe requires explicit local TLS and version',()=>refused(['-Action','update-probe'],/requires local TLS, an explicit version/));
test('Probe rejects any non-test signing key path',()=>refused(['-Action','update-probe','-UpdateUrl','https://127.0.0.1:43133/windows/latest.json','-PublicKeyPath','web/package.json','-TestCertificatePath',cert,'-TestVersion','0.1.24','-ArtifactDirectory','client-update-artifacts/local/guard-only'],/existing TEST signing identity/));
test('Probe CA cannot authorize a remote update origin',()=>refused(['-Action','update-probe','-UpdateUrl','https://example.test/windows/latest.json','-PublicKeyPath',key,'-TestCertificatePath',cert,'-TestVersion','0.1.24','-ArtifactDirectory','client-update-artifacts/local/guard-only'],/Test certificates are limited to local updater lab builds/));
test('Probe artifacts cannot enter production output',()=>refused(['-Action','update-probe','-UpdateUrl','https://127.0.0.1:43133/windows/latest.json','-PublicKeyPath',key,'-TestCertificatePath',cert,'-TestVersion','0.1.24','-ArtifactDirectory','production-artifacts/guard-only'],/Lab builds must use an isolated local artifact directory/));

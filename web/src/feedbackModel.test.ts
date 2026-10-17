import { describe, it, expect, beforeEach } from 'vitest';
import { IDBFactory } from 'fake-indexeddb';
import { classifyFeedbackError, newFeedback, parseFeedbackDraft, safeDiagnostic, type SafeDiagnostic } from './feedbackModel';
import { feedbackFailure, rememberFeedbackFailure, clearFeedbackFailure, feedbackServer, rememberFeedbackServer } from './feedbackDiagnostics';
import { loadFeedbackDraft, saveFeedbackDraft, removeFeedbackDraft } from './feedbackStorage';
const diagnostic=():SafeDiagnostic=>({schemaVersion:1,clientVersion:'0.1.14',platform:'WEB',occurredAt:'2026-10-18T00:00:00.000Z',errorKind:'CONFLICT',queueCount:2,conflictCount:1});
describe('feedback privacy and durable local drafts',()=>{
  beforeEach(()=>{globalThis.indexedDB=new IDBFactory();clearFeedbackFailure();});
  it('only exports known scalar diagnostic fields, never arbitrary error or queue content',()=>{
    const input={...diagnostic(),token:'secret',url:'http://192.168.1.2',title:'private',requestId:'private-url'};
    expect(safeDiagnostic(input)).toEqual(diagnostic());
    expect(()=>safeDiagnostic({...diagnostic(),queueCount:1})).not.toThrow();
    expect(()=>safeDiagnostic({...diagnostic(),queueCount:0})).toThrow();
    expect(()=>safeDiagnostic({...diagnostic(),clientVersion:'http://internal'})).toThrow();
  });
  it('does not guess TLS separately from an ambiguous network failure',()=>{
    expect(classifyFeedbackError()).toBe('NETWORK_OR_TLS');expect(classifyFeedbackError(403)).toBe('PERMISSION');
    expect(classifyFeedbackError(409)).toBe('CONFLICT');expect(classifyFeedbackError(503)).toBe('BUSY');
  });
  it('keeps only an opaque request id and isolates recent failures by account/server',()=>{
    const id=crypto.randomUUID();rememberFeedbackFailure('server-a|owner-a',500,id);
    expect(feedbackFailure('server-a|owner-a')).toMatchObject({errorKind:'SERVER',requestId:id});
    expect(feedbackFailure('server-b|owner-a').errorKind).toBe('NONE');
    rememberFeedbackFailure('server-a|owner-a',400,'private-url');expect(feedbackFailure('server-a|owner-a')).not.toHaveProperty('requestId');
    clearFeedbackFailure();expect(feedbackFailure('server-a|owner-a').errorKind).toBe('NONE');
  });
  it('preserves exact operation key and uncertain body across reopen without sharing other contexts',async()=>{
    const draft={...newFeedback(),title:'合成反馈',steps:'测试操作',diagnostic:diagnostic(),uncertain:true};
    await saveFeedbackDraft('server-a','owner-a',draft);
    expect(await loadFeedbackDraft('server-a','owner-a')).toEqual(draft);
    expect(await loadFeedbackDraft('server-b','owner-a')).toBeNull();expect(await loadFeedbackDraft('server-a','owner-b')).toBeNull();
    await removeFeedbackDraft('server-a','owner-a');expect(await loadFeedbackDraft('server-a','owner-a')).toBeNull();
  });
  it('rejects malformed or oversized persisted drafts instead of overwriting them silently',()=>{
    expect(()=>parseFeedbackDraft({...newFeedback(),title:'x'.repeat(121)})).toThrow();
    expect(()=>parseFeedbackDraft({...newFeedback(),operationKey:'not-uuid'})).toThrow();
    expect(()=>parseFeedbackDraft({...newFeedback(),category:'toString'})).toThrow();
    const result=parseFeedbackDraft({...newFeedback(),extra:'never-copy'});expect(result).not.toHaveProperty('extra');
  });
  it('retains only validated public server versions and isolates servers',()=>{
    rememberFeedbackServer('a',{serverVersion:'0.1.14',apiVersion:1,token:'never-copy'});
    expect(feedbackServer('a')).toEqual({serverVersion:'0.1.14',apiVersion:1});expect(feedbackServer('b')).toEqual({});
    rememberFeedbackServer('a',{serverVersion:'http://private',apiVersion:1});expect(feedbackServer('a')).toEqual({serverVersion:'0.1.14',apiVersion:1});
  });
});

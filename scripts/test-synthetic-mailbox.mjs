import {test} from 'node:test';import assert from 'node:assert/strict';import net from 'node:net';
import {syntheticMailbox,decodeMail} from './synthetic-mailbox.mjs';
test('decodes synthetic JavaMail base64 and quoted-printable without losing URL capability',()=>{
 const text='合成邮件\r\nhttps://localhost/#/change-email#request=synthetic&side=old&token=not-a-real-token';
 assert.equal(decodeMail('Content-Transfer-Encoding: base64\r\n\r\n'+Buffer.from(text).toString('base64')),text);
 assert.equal(decodeMail('Content-Transfer-Encoding: quoted-printable\r\n\r\nrequest=3Dsynthetic=\r\n&side=3Dold'),'request=synthetic&side=old');
});
test('refuses real recipients before binding any socket',async()=>{
 await assert.rejects(()=>syntheticMailbox(0,['real@example.com']),/Only synthetic/);
});
test('SMTP sink accepts only listed synthetic recipients and does not forward',async()=>{
 const sink=await syntheticMailbox(43172,['old@example.test'],{maxMessages:1});let socket;
 try{
  socket=net.connect(43172,'127.0.0.1');socket.setEncoding('utf8');let pending='',waiter;
  socket.on('data',s=>{pending+=s;if(waiter){waiter();waiter=undefined;}});
  async function until(pattern){for(let n=0;n<50&&!pattern.test(pending);n++)await Promise.race([new Promise(r=>waiter=r),new Promise(r=>setTimeout(r,50))]);assert.match(pending,pattern);pending='';}
  await until(/220 /);socket.write('EHLO localhost\r\n');await until(/250 SIZE/);
  socket.write('MAIL FROM:<sender@example.test>\r\n');await until(/250 /);
  socket.write('RCPT TO:<real@example.com>\r\n');await until(/550 /);
  socket.write('RCPT TO:<old@example.test>\r\n');await until(/250 /);
  socket.write('DATA\r\n');await until(/354 /);socket.write('missing headers\r\n.\r\n');await until(/554 malformed/);assert.equal(sink.messages.length,0);
  socket.write('MAIL FROM:<sender@example.test>\r\n');await until(/250 /);socket.write('RCPT TO:<old@example.test>\r\n');await until(/250 /);
  socket.write('DATA\r\n');await until(/354 /);socket.write('Content-Type: text/plain\r\n\r\nsynthetic only\r\n.\r\n');await until(/250 accepted/);
  assert.equal(sink.messages.length,1);assert.equal(sink.messages[0].recipient,'old@example.test');assert.match(sink.messages[0].body,/synthetic only/);
 }finally{socket?.destroy();await sink.close();}
});

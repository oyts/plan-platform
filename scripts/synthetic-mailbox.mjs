// Local SMTP sink for synthetic .test addresses only. Never forwards mail or prints bodies/tokens.
import net from 'node:net';
export function decodeMail(raw) {
  const split=raw.indexOf('\r\n\r\n');if(split<0)throw Error('Synthetic message missing headers');
  const headers=raw.slice(0,split).replace(/\r\n[ \t]+/g,' '),body=raw.slice(split+4);
  const encoding=headers.match(/^Content-Transfer-Encoding:\s*([^\r\n]+)/im)?.[1].toLowerCase().trim();
  if(encoding==='base64')return Buffer.from(body.replace(/\s/g,''),'base64').toString('utf8');
  if(encoding==='quoted-printable')return Buffer.from(body.replace(/=\r?\n/g,'').replace(/=([a-f0-9]{2})/ig,(_,x)=>String.fromCharCode(parseInt(x,16))),'latin1').toString('utf8');
  return body;
}
export async function syntheticMailbox(port,recipients,{maxMessages=4}={}){
  if(!recipients.length||recipients.some(x=>!/^[-a-z0-9.+]+@[-a-z0-9.]+\.test$/i.test(x)))throw Error('Only synthetic .test recipients are allowed');
  const allowed=new Set(recipients.map(x=>x.toLowerCase())),messages=[],sockets=new Set();
  const server=net.createServer(socket=>{
    sockets.add(socket);socket.on('close',()=>sockets.delete(socket));socket.on('error',()=>{});
    let buffer='',recipient='',data=false,lines=[],bytes=0;
    socket.write('220 plan-cabin-local synthetic sink\r\n');
    socket.on('data',chunk=>{
      buffer+=chunk.toString('utf8');if(Buffer.byteLength(buffer)>262144){socket.destroy();return;}
      while(buffer.includes('\r\n')){
        const end=buffer.indexOf('\r\n'),line=buffer.slice(0,end);buffer=buffer.slice(end+2);
        if(data){
          if(line==='.'){
            data=false;
            try{
              if(messages.length>=maxMessages)throw Error('Synthetic limit reached');
              messages.push({recipient,body:decodeMail(lines.join('\r\n'))});
              socket.write('250 accepted by local synthetic sink only\r\n');
            }catch{socket.write('554 malformed synthetic message or limit reached\r\n');}
            lines=[];bytes=0;recipient='';
          }else{bytes+=Buffer.byteLength(line)+2;if(bytes>131072){socket.destroy();return;}lines.push(line.replace(/^\.\./,'.'));}continue;
        }
        if(/^(EHLO|HELO)\b/i.test(line))socket.write('250-plan-cabin-local\r\n250-8BITMIME\r\n250 SIZE 131072\r\n');
        else if(/^MAIL FROM:/i.test(line)){recipient='';socket.write('250 ok\r\n');}
        else if(/^RCPT TO:/i.test(line)){
          const address=line.match(/^RCPT TO:\s*<([^>]+)>/i)?.[1]?.toLowerCase();
          if(!allowed.has(address))socket.write('550 synthetic recipient not approved\r\n');else{recipient=address;socket.write('250 ok\r\n');}
        }else if(/^DATA$/i.test(line)){
          if(!recipient||messages.length>=maxMessages)socket.write('554 synthetic message limit or missing recipient\r\n');else{data=true;socket.write('354 end with dot\r\n');}
        }else if(/^RSET$/i.test(line)){recipient='';socket.write('250 ok\r\n');}
        else if(/^NOOP$/i.test(line))socket.write('250 ok\r\n');
        else if(/^QUIT$/i.test(line)){socket.end('221 bye\r\n');}
        else socket.write('502 unsupported synthetic command\r\n');
      }
    });
  });
  await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(port,'127.0.0.1',resolve);});
  return {messages,async close(){for(const socket of sockets)socket.destroy();await new Promise(resolve=>server.close(resolve));}};
}

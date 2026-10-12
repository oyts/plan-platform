import {scryptSync,randomBytes,createCipheriv,createDecipheriv} from 'node:crypto';
const magic='PlanCabinSigningBackup',schema=1;
const derive=(password,salt)=>scryptSync(password,salt,32,{N:32768,r:8,p:1,maxmem:64*1024*1024});
export function sealBackup(payload,password) {
  if(typeof password!=='string'||password.length<16) throw new Error('Use a backup passphrase of at least 16 characters.');
  const salt=randomBytes(32),iv=randomBytes(12),key=derive(password,salt);
  try {
    const cipher=createCipheriv('aes-256-gcm',key,iv);
    cipher.setAAD(Buffer.from(`${magic}:${schema}`));
    const plain=Buffer.from(JSON.stringify(payload));
    try {
      const data=Buffer.concat([cipher.update(plain),cipher.final()]);
      return {magic,schema,salt:salt.toString('base64'),iv:iv.toString('base64'),tag:cipher.getAuthTag().toString('base64'),data:data.toString('base64')};
    } finally {plain.fill(0);}
  } finally {key.fill(0);}
}
export function openBackup(archive,password) {
  if(archive?.magic!==magic||archive.schema!==schema) throw new Error('Unknown signing backup format.');
  const decode=(text,size)=>{
    if(typeof text!=='string'||text.length>2*1024*1024) throw new Error('Invalid signing backup field.');
    const bytes=Buffer.from(text,'base64');
    if(bytes.toString('base64')!==text||(size&&bytes.length!==size)) throw new Error('Invalid signing backup encoding.');
    return bytes;
  };
  const salt=decode(archive.salt,32),iv=decode(archive.iv,12),tag=decode(archive.tag,16),data=decode(archive.data);
  const key=derive(password,salt);
  try {
    const cipher=createDecipheriv('aes-256-gcm',key,iv);
    cipher.setAAD(Buffer.from(`${magic}:${schema}`));cipher.setAuthTag(tag);
    const plain=Buffer.concat([cipher.update(data),cipher.final()]);
    try {return JSON.parse(plain.toString('utf8'));} finally {plain.fill(0);}
  } finally {key.fill(0);}
}

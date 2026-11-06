export const invoke=(target,name,...args)=>new Promise((resolve,reject)=>{
 if(typeof target?.[name]!=='function')return reject(new Error('Questa versione di Outlook non supporta '+name));
 target[name](...args,result=>result.status==='succeeded'?resolve(result.value):reject(new Error(result.error?.message||'Operazione Outlook non riuscita')));
});
export const base64=text=>{const bytes=new TextEncoder().encode(text);let binary='';for(let i=0;i<bytes.length;i++)binary+=String.fromCharCode(bytes[i]);return btoa(binary);};
const wrap=s=>s.match(/.{1,76}/g)?.join('\r\n')||'';
const subjectWords=s=>{let chunks=[],chunk='';for(const char of String(s||'')){if(new TextEncoder().encode(chunk+char).length>42){chunks.push(chunk);chunk='';}chunk+=char;}if(chunk)chunks.push(chunk);return chunks.map(c=>'=?UTF-8?B?'+base64(c)+'?=').join('\r\n ');};
const line=s=>String(s||'').replace(/[\r\n\0]/g,' ');
export function generatedEml(item,text,files){const boundary='penny-message-boundary';const header=[`From: ${line(item.from?.emailAddress)}`,`To: ${(item.to||[]).map(r=>line(r.emailAddress)).join(', ')}`,`Cc: ${(item.cc||[]).map(r=>line(r.emailAddress)).join(', ')}`,`Subject: ${subjectWords(item.subject)}`,`Date: ${new Date(item.dateTimeCreated||Date.now()).toUTCString()}`,'MIME-Version: 1.0',`Content-Type: multipart/mixed; boundary="${boundary}"`];let parts=[`--${boundary}\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Transfer-Encoding: base64\r\n\r\n${wrap(base64(text))}`];for(const f of files){parts.push(`--${boundary}\r\nContent-Type: application/octet-stream\r\nContent-Disposition: attachment; filename="attachment"; filename*=UTF-8''${encodeURIComponent(f.filename)}\r\nContent-Transfer-Encoding: base64\r\n\r\n${f.contentBase64.match(/.{1,76}/g)?.join('\r\n')||''}`);}return base64(header.join('\r\n')+'\r\n\r\n'+parts.join('\r\n')+`\r\n--${boundary}--\r\n`);}
export async function extractMessage(item,mailbox,{exportOriginal=true}={}){
 if(!item?.itemId||!item.body)throw new Error('Apri un messaggio ricevuto o inviato, non una bozza.');
 const text=await invoke(item.body,'getAsync','text');const attachments=[];
 for(const attachment of item.attachments||[]){const data=await invoke(item,'getAttachmentContentAsync',attachment.id);if(!['base64','eml','icalendar'].includes(String(data.format).toLowerCase()))throw new Error('Allegato non scaricabile: '+attachment.name+'. Nessun messaggio è stato salvato.');const content=String(data.format).toLowerCase()==='base64'?data.content:base64(data.content);if(content.length>13981016)throw new Error('Allegato oltre 10 MB: '+attachment.name);attachments.push({filename:attachment.name,contentBase64:content,inlineImage:!!attachment.isInline});}
 let originalKind='GENERATED',originalBase64;
 if(exportOriginal&&typeof item.getAsFileAsync==='function'){originalBase64=await invoke(item,'getAsFileAsync');originalKind='EML';}else originalBase64=generatedEml(item,text,attachments);
 const payload={mailbox,itemId:item.itemId,internetMessageId:item.internetMessageId||null,conversationId:item.conversationId||null,subject:item.subject||'(Senza oggetto)',sender:item.from?.emailAddress||item.sender?.emailAddress||mailbox,recipients:[...(item.to||[]),...(item.cc||[])].map(r=>r.emailAddress),sentAt:new Date(item.dateTimeCreated||Date.now()).toISOString(),bodyText:text,originalBase64,originalKind,attachments};
 const bytes=[originalBase64,...attachments.map(a=>a.contentBase64)].reduce((n,s)=>n+Math.floor(s.length*3/4),0);if(bytes>20*1024*1024)throw new Error('Messaggio e allegati superano 20 MB.');return payload;
}
export function chooseMatch(customers){const matches=customers.filter(c=>c.matched);return matches.length===1?String(matches[0].id):'';}

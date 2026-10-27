import React,{useEffect,useState} from 'react';
import Icon,{type IconName} from './Icons';
import {api} from './api';
export type ImageKind='customers'|'contacts'|'installed-products'|'catalog-products';
export type ImageState={hasImage:boolean;hasOwnImage:boolean};
export const imageUrl=(kind:ImageKind,id:number,version?:string|number)=>
 (kind==='catalog-products'?'/api/catalog-products/'+id+'/image':'/api/entity-images/'+kind+'/'+id)+(version?'?v='+encodeURIComponent(version):'');

export function EntityAvatar({kind,id,name,hasImage=false,version,icon,compact=false}:{kind:ImageKind;id:number;name:string;hasImage?:boolean;version?:string|number;icon?:IconName;compact?:boolean}){
 const src=imageUrl(kind,id,version),[failed,setFailed]=useState(false);
 useEffect(()=>setFailed(false),[src,hasImage]);
 return <span className={'entity-avatar'+(compact?' entity-avatar--compact':'')} aria-hidden="true">{hasImage&&!failed?<img src={src} alt="" loading="lazy" onError={()=>setFailed(true)}/>:icon?<Icon name={icon}/>:name.trim().split(/\s+/).slice(0,2).map(s=>s[0]).join('').toUpperCase()||'?'}</span>;
}

export default function EntityImageEditor({kind,id,label,hasImage,hasOwnImage=hasImage,version,canWrite,onChanged}:{kind:Exclude<ImageKind,'catalog-products'>;id:number;label:string;hasImage:boolean;hasOwnImage?:boolean;version?:number;canWrite:boolean;onChanged:(state:ImageState)=>void}){
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[imageVersion,setVersion]=useState(version??0);
 async function upload(file?:File){
  if(!file||busy)return;setError('');
  if(!['image/png','image/jpeg','image/webp'].includes(file.type)||file.size>5*1024*1024||!file.size){setError('Scegli un’immagine PNG, JPEG o WEBP fino a 5 MB.');return;}
  setBusy(true);
  try{
   const {token}=await api<{token:string}>('/api/auth/csrf');const body=new FormData();body.append('file',file);
   const response=await fetch(imageUrl(kind,id),{method:'POST',credentials:'same-origin',headers:{'X-XSRF-TOKEN':token},body});
   if(!response.ok){const data=await response.json().catch(()=>null);throw Error(data?.message||'Immagine non salvata. Riprova.');}
   const state=await response.json() as ImageState;setVersion(Date.now());onChanged(state);
  }catch(e){setError(e instanceof Error?e.message:String(e));}finally{setBusy(false);}
 }
 async function remove(){if(busy)return;setBusy(true);setError('');try{const state=await api<ImageState>(imageUrl(kind,id),'DELETE');setVersion(Date.now());onChanged(state);}catch(e){setError(e instanceof Error?e.message:String(e));}finally{setBusy(false);}}
 return <div className="entity-image-editor">{hasImage?<img src={imageUrl(kind,id,imageVersion)} alt={label}/>:<p>Nessuna immagine. Verranno mostrate le iniziali o l’icona della scheda.</p>}
 {kind==='installed-products'&&hasImage&&!hasOwnImage&&<p>Immagine ereditata dal catalogo. Puoi caricarne una specifica per questa installazione.</p>}
 {canWrite&&<div className="entity-image-controls"><label className="entity-image-upload">{hasOwnImage?'Sostituisci immagine':'Carica immagine'}<input type="file" accept="image/png,image/jpeg,image/webp" disabled={busy} onChange={e=>{void upload(e.target.files?.[0]);e.target.value='';}}/></label><small>PNG, JPEG o WEBP · massimo 5 MB</small>{hasOwnImage&&<button type="button" className="penny-button-secondary" disabled={busy} onClick={()=>void remove()}>{kind==='installed-products'?'Rimuovi immagine personalizzata':'Rimuovi immagine'}</button>}</div>}
 {busy&&<p role="status">Aggiornamento immagine…</p>}{error&&<p role="alert">{error}</p>}</div>;
}

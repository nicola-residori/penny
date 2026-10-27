import React,{useId,useLayoutEffect,useRef} from 'react';
import './detail-modal.css';
let locks=0,previousOverflow='';
export default function DetailModal({open,title,onClose,children}:{open:boolean;title:string;onClose:()=>void;children:React.ReactNode}){
 const dialog=useRef<HTMLDialogElement>(null),close=useRef(onClose),id=useId();close.current=onClose;
 useLayoutEffect(()=>{
  if(!open||!dialog.current)return;
  const element=dialog.current,previous=document.activeElement as HTMLElement|null;
  if(locks++===0){previousOverflow=document.body.style.overflow;document.body.style.overflow='hidden';}
  if(typeof element.showModal==='function')element.showModal();else element.setAttribute('open','');
  element.querySelector<HTMLButtonElement>('.detail-modal-close')?.focus();
  return()=>{if(typeof element.close==='function')element.close();else element.removeAttribute('open');if(--locks===0)document.body.style.overflow=previousOverflow;if(previous?.isConnected)previous.focus();};
 },[open]);
 if(!open)return null;
 return <dialog ref={dialog} className="detail-modal" aria-labelledby={id} aria-modal="true" onCancel={e=>{e.preventDefault();e.stopPropagation();close.current();}} onKeyDown={e=>{
  if((e.target as HTMLElement).closest('dialog')!==dialog.current)return;
  if(e.key==='Escape'){e.preventDefault();e.stopPropagation();close.current();}
  if(e.key==='Tab'){
   const controls=Array.from(dialog.current?.querySelectorAll<HTMLElement>('button:not(:disabled),a[href],input:not(:disabled),select:not(:disabled),textarea:not(:disabled),[tabindex="0"]')||[]).filter(el=>!el.closest('[hidden]')&&el.getAttribute('aria-hidden')!=='true');
   const first=controls[0],last=controls.at(-1);if(e.shiftKey&&document.activeElement===first){e.preventDefault();last?.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first?.focus();}
  }
 }}><header className="detail-modal-header"><h2 id={id}>{title}</h2><button type="button" className="detail-modal-close penny-button-secondary" aria-label={'Chiudi '+title} onClick={()=>close.current()}><span aria-hidden="true">×</span></button></header><div className="detail-modal-body">{children}</div></dialog>;
}

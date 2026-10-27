import React,{useEffect,useState} from 'react';
import {api} from './api';
export type TrashEntity='CUSTOMER_RECORD'|'CUSTOMER'|'CONTACT'|'CATALOG_PRODUCT'|'INSTALLED_PRODUCT'|'WORKSTATION';
export default function TrashButton({entityType,entityId,label,onTrashed}:{entityType:TrashEntity;entityId:number;label:string;onTrashed:()=>void}){
 const [busy,setBusy]=useState(false),[error,setError]=useState('');
 useEffect(()=>{setError('');},[entityType,entityId]);
 async function remove(){setError('');if(!window.confirm(`Spostare “${label}” nel cestino? Potrai ripristinarlo entro 30 giorni; dopo la scadenza sarà eliminato definitivamente.${entityType==='CUSTOMER'?' Sono inclusi anche i contatti del cliente.':''} Gli eventuali collegamenti possono impedire l’operazione.`))return;setBusy(true);try{await api('/api/trash/'+entityType+'/'+entityId,'DELETE');onTrashed();}catch(e){setError(e instanceof Error?e.message:'Eliminazione non riuscita');}finally{setBusy(false);}}
 return <span className="trash-action"><button type="button" className="penny-button-danger-quiet" disabled={busy} onClick={remove}>{busy?'Eliminazione…':'Elimina'}</button>{error&&<span role="alert">{error}</span>}</span>;
}

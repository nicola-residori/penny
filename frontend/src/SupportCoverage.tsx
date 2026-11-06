import Icon from './Icons';
import React,{useEffect,useState} from 'react';
import {api} from './api';
import './support-coverage.css';
type Coverage={hasSupport:boolean;status?:'ACTIVE'|'EXPIRED'|'NONE'|'SUSPENDED'|'NOT_STARTED'|'INACTIVE';expiredOn?:string};
export default function SupportCoverage({customerId}:{customerId:number}){
 const [data,setData]=useState<Coverage|null>(null),[error,setError]=useState('');
 useEffect(()=>{let live=true;setData(null);setError('');if(customerId>0)api<Coverage>('/api/customers/'+customerId+'/support-coverage').then(r=>{if(live&&typeof r.hasSupport==='boolean')setData(r);else if(live)setError('Stato contratto non disponibile');}).catch(()=>{if(live)setError('Impossibile verificare il contratto di assistenza.');});return()=>{live=false;};},[customerId]);
 if(!customerId)return null;
 const status=data?.status||(data?.hasSupport?'ACTIVE':'NONE');
 const uncovered=!!data&&!error&&status!=='ACTIVE';
 const labels={ACTIVE:'Contratto di assistenza attivo',EXPIRED:'Contratto di assistenza scaduto',NONE:'Nessun contratto di assistenza telefonica o oraria',SUSPENDED:'Contratto di assistenza sospeso',NOT_STARTED:'Contratto di assistenza non ancora iniziato',INACTIVE:'Nessun contratto di assistenza attivo'};
 return <div className={'support-coverage '+(!data||error?'support-coverage-pending':status==='ACTIVE'?'support-coverage-active':'support-coverage-warning')} role={uncovered?'alert':'status'}>{uncovered&&<span className="support-coverage-alert-icon" aria-hidden="true"><Icon name="alert"/></span>}<div className="support-coverage-content">{uncovered&&<span className="support-coverage-eyebrow">Attenzione</span>}<strong>{error||(!data?'Verifica del contratto di assistenza…':labels[status])}</strong>{data&&!error&&<p>{status==='ACTIVE'?'Copertura attuale del cliente verificata.':<>Copertura attuale del cliente: {status==='EXPIRED'&&data.expiredOn?'scadenza '+new Date(data.expiredOn+'T12:00:00').toLocaleDateString('it-IT')+'. ':''}La tele-assistenza può essere registrata comunque.</>}</p>}{error&&<p>Puoi registrare la chiamata e verificare il contratto dalla scheda cliente.</p>}</div></div>;
}

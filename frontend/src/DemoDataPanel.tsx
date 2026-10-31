import React,{useEffect,useState} from 'react';
import {api} from './api';
import './demo-data.css';
type State={canManageData:boolean;demoLoaded:boolean;scenarioVersion?:number;demoUpgradeAvailable?:boolean;counts:Record<string,number>};
const countLabels:Record<string,string>={customers:'Clienti e prospect',customerRecords:'Schede clienti',recordParticipants:'Partecipanti corsi',contacts:'Contatti',contactLinks:'Associazioni contatti',sites:'Sedi',catalog:'Prodotti a catalogo',installations:'Prodotti installati',sales:'Vendite',allocations:'Allocazioni',workstations:'Gruppi hardware',trash:'Elementi nel cestino',orders:'Ordini',tasks:'Attività delle schede',interventionLinks:'Collegamenti interventi',renewals:'Rinnovi contratti',documents:'Documenti',notifications:'Notifiche',marginProfiles:'Profili margine',marginRules:'Regole margine',marginSnapshots:'Calcoli margine',marginEvents:'Eventi margine',marginRuleAudit:'Storico regole margine'};
export default function DemoDataPanel({canManageData}:{canManageData:boolean}){
 const [state,setState]=useState<State|null>(null),[busy,setBusy]=useState(false),[error,setError]=useState(''),[message,setMessage]=useState(''),[confirmation,setConfirmation]=useState('');
 useEffect(()=>{if(!canManageData)return;let active=true;api<State>('/api/admin/data/state').then(value=>{if(active)setState(value);}).catch(e=>{if(active)setError(e.message);});return()=>{active=false;};},[canManageData]);
 if(!canManageData)return null;
 async function perform(action:'demo'|'reset'){
  setBusy(true);setError('');setMessage('');
  try{setState(await api<State>('/api/admin/data/'+action,'POST',action==='demo'?{confirm:true}:{confirmation}));setConfirmation('');setMessage(action==='demo'?'Dati demo aggiornati.':'Dati CRM eliminati.');window.dispatchEvent(new Event('penny:data-changed'));}catch(e){setError(e instanceof Error?e.message:'Operazione non riuscita');}finally{setBusy(false);}
 }
 return <section className="demo-data-panel"><h2>Ambiente e dati dimostrativi</h2>
 <p>I dati demo si aggiungono ai dati esistenti. Gli aggiornamenti ampliano gli scenari senza duplicare quelli già caricati.</p>
 {error&&<p role="alert">{error}</p>}{message&&<p role="status">{message}</p>}
 {!state&&!error&&<p>Caricamento conteggi…</p>}
 {state&&<><p className="demo-counts">{Object.entries(state.counts).map(([label,count])=><span key={label}>{countLabels[label]||label}: {count}</span>)}</p>
 <button disabled={busy||(state.demoLoaded&&(state.scenarioVersion??1)>=3)} onClick={()=>void perform('demo')}>{state.demoLoaded?((state.scenarioVersion??1)>=3?'Demo aggiornata':'Amplia dati demo'):'Carica dati demo'}</button><p>{state.demoLoaded&&(state.scenarioVersion??1)>=3?'Storico biennale attivo: vendite e attività giornaliere, nuovi clienti mensili, stagionalità e confronti annuali.':'Il dataset include due anni di vendite e attività giornaliere, clienti acquisiti ogni mese, multisede e contatti condivisi.'}</p>
 <div className="demo-reset"><h3>Azzera ambiente CRM</h3><p>Operazione irreversibile: elimina tutti i dati aziendali, anche quelli non demo: clienti e prospect, contatti, sedi, catalogo, vendite, allocazioni, installazioni, gruppi hardware, formazione, contratti, sviluppi, attività, ordini, documenti, notifiche, rinnovi, dati e storico delle regole di margine e cestino. Conserva utenti, ruoli e permessi, categorie e tipologie di configurazione e lo storico tecnico di audit.</p>
 <label>Scrivi RESET per confermare<input value={confirmation} onChange={e=>setConfirmation(e.target.value)} disabled={busy}/></label>
 <button className="penny-button-danger-quiet" disabled={busy||confirmation!=='RESET'} onClick={()=>void perform('reset')}>Azzera dati CRM</button></div></>}
 {busy&&<p role="status">Operazione in corso…</p>}</section>;
}

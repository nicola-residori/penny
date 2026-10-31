import MarginsPanel from './MarginsPanel';
import ChoicesPanel from './ChoicesPanel';
import WorkspaceUtilitiesPanel from './WorkspaceUtilitiesPanel';
import './pagination-fix.css';
import React,{useEffect,useState} from 'react';
import {createRoot} from 'react-dom/client';
import AdminPanel from './AdminPanel';
import DemoDataPanel from './DemoDataPanel';
import CustomersPanel from './CustomersPanel';
import ProductsPanel from './ProductsPanel';
import CatalogPanel from './CatalogPanel';
import ContactDirectory from './ContactDirectory';
import CustomerRecordsPanel from './CustomerRecordsPanel';
import './style.css';
import './admin.css';
import './m103.css';
import Dashboard,{type View} from './Dashboard';
import Icon from './Icons';
import SalesPanel from './SalesPanel';
import InventoryPanel from './InventoryPanel';
import CalendarPanel from './CalendarPanel';
import HardwarePanel from './HardwarePanel';
import './refresh.css';
import './controls.css';
import './trash.css';
import TrashPanel from './TrashPanel';
import './customer-reference.css';
import './select-controls.css';
import './dossier-theme.css';
import ThemeSelector from './ThemeSelector';
import {initializeTheme} from './theme';
import './themes.css';
initializeTheme();

type Profile={canReadMargins?:boolean;canWriteMargins?:boolean;canReadDocuments?:boolean;canWriteDocuments?:boolean;canReadNotifications?:boolean;canWriteNotifications?:boolean;canReadTraining?:boolean;canWriteTraining?:boolean;canReadContracts?:boolean;canWriteContracts?:boolean;canReadActivities?:boolean;canWriteActivities?:boolean;canManageData?:boolean;canUseTrash?:boolean;canDeleteRecords?:boolean;canRestoreTrash?:boolean;email:string;name:string;mustChangePassword:boolean;canManageUsers?:boolean;canReadCustomers?:boolean;canWriteCustomers?:boolean;canReadProducts?:boolean;canWriteProducts?:boolean;canReadCatalog?:boolean;canWriteCatalog?:boolean;canReadSales?:boolean;canWriteSales?:boolean;canReadInventory?:boolean;canReadAllocations?:boolean;canWriteAllocations?:boolean;canReadWorkstations?:boolean;canWriteWorkstations?:boolean};

async function csrfToken():Promise<string>{
 const r=await fetch('/api/auth/csrf',{credentials:'same-origin'});
 if(!r.ok)throw new Error('Impossibile ottenere il token CSRF');
 const data=await r.json() as {token:string};
 return data.token;
}
async function authPost(path:string,body?:unknown):Promise<Profile|null>{
 const token=await csrfToken();
 const r=await fetch('/api/auth/'+path,{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':token},body:body===undefined?undefined:JSON.stringify(body)});
 if(!r.ok){
  if(r.status===429)throw new Error('Troppi tentativi. Riprova più tardi.');
  if(r.status===401)throw new Error('Credenziali non valide o sessione scaduta.');
  throw new Error('Operazione non riuscita (HTTP '+r.status+').');
 }
 return r.status===204?null:await r.json() as Profile;
}
function useProfile(){
 const [me,setMe]=useState<Profile|null>(null),[loading,setLoading]=useState(true);
 useEffect(()=>{let alive=true;fetch('/api/auth/me',{credentials:'same-origin'}).then(async r=>{
  if(r.ok){const p=await r.json() as Profile;if(alive)setMe(p);}
 }).catch(()=>{}).finally(()=>{if(alive)setLoading(false);});return()=>{alive=false;};},[]);
 return {me,setMe,loading};
}
function Brand({compact=false}:{compact?:boolean}){
 return <div className={'penny-brand '+(compact?'penny-brand-compact':'')}><img src="/penny-icon.png" alt="Mascotte Penny"/><div><strong>Penny</strong></div><small className="penny-brand-quote">Projects, Engagements, Notifications, New Deals and Yearly Commissions</small></div>;
}
function AuthScreen({onLogin}:{onLogin:(p:Profile)=>void}){
 const [email,setEmail]=useState(''),[password,setPassword]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false);
 async function login(e:React.FormEvent){e.preventDefault();setBusy(true);setError('');try{const p=await authPost('login',{email,password});if(p)onLogin(p);setPassword('');}catch(e){setError(e instanceof Error?e.message:'Accesso non riuscito');}finally{setBusy(false);}}
 return <main className="penny-auth-wrap"><div className="penny-auth-appearance"><ThemeSelector/></div><section className="penny-auth-card"><Brand/><h1>Bentornato</h1><p>Accedi al tuo spazio di lavoro.</p><form onSubmit={login}><label>Email<input autoComplete="username" type="email" required value={email} onChange={e=>setEmail(e.target.value)}/></label><label>Password<input autoComplete="current-password" type="password" required value={password} onChange={e=>setPassword(e.target.value)}/></label><button disabled={busy}>{busy?'Accesso in corso…':'Accedi'}</button></form>{error&&<p className="penny-alert" role="alert">{error}</p>}</section></main>;
}
function ChangePassword({onChanged}:{onChanged:()=>void}){
 const [current,setCurrent]=useState(''),[next,setNext]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState('');
 async function submit(e:React.FormEvent){e.preventDefault();setBusy(true);setError('');try{await authPost('change-password',{currentPassword:current,newPassword:next});setCurrent('');setNext('');onChanged();}catch(e){setError(e instanceof Error?e.message:'Impossibile cambiare la password');}finally{setBusy(false);}}
 return <main className="penny-auth-wrap"><div className="penny-auth-appearance"><ThemeSelector/></div><section className="penny-auth-card"><Brand/><h1>Cambia la password</h1><p>Per continuare devi sostituire la password temporanea.</p><form onSubmit={submit}><label>Password temporanea<input type="password" autoComplete="current-password" required value={current} onChange={e=>setCurrent(e.target.value)}/></label><label>Nuova password (almeno 12 caratteri)<input type="password" autoComplete="new-password" required minLength={12} value={next} onChange={e=>setNext(e.target.value)}/></label><button disabled={busy}>Salva e torna all'accesso</button></form>{error&&<p className="penny-alert" role="alert">{error}</p>}</section></main>;
}
export function Shell({me,onLogout,onRefresh}:{me:Profile;onLogout:()=>void;onRefresh:()=>void}){
 const [view,setView]=useState<View>('dashboard'),[menuOpen,setMenuOpen]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState('');
 function navigate(next:View){setView(next);setMenuOpen(false);setError('');requestAnimationFrame(()=>document.getElementById('penny-main')?.focus());}
 useEffect(()=>{if(!menuOpen)return;if(window.matchMedia?.('(max-width:700px)').matches)document.querySelector<HTMLButtonElement>('#penny-navigation nav button')?.focus();function escape(e:KeyboardEvent){if(e.key==='Escape'){setMenuOpen(false);document.getElementById('penny-menu-toggle')?.focus();}if(e.key==='Tab'&&window.matchMedia?.('(max-width:700px)').matches){const buttons=Array.from(document.querySelectorAll<HTMLButtonElement>('#penny-navigation nav button'));const first=buttons[0],last=buttons[buttons.length-1];if(e.shiftKey&&document.activeElement===first){e.preventDefault();last?.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first?.focus();}}}document.addEventListener('keydown',escape);return()=>document.removeEventListener('keydown',escape);},[menuOpen]);
 async function logout(){setBusy(true);setError('');try{await authPost('logout');onLogout();}catch(e){setError(e instanceof Error?e.message:'Errore durante la disconnessione');}finally{setBusy(false);}}
 const labels:Record<View,string>={margins:'Margini',interventions:'Interventi tecnici',choices:'Scelte predefinite',notifications:'Notifiche e promemoria',documents:'Documentazione',contacts:'Contatti',software:'Software',training:'Formazione',contracts:'Contratti',developments:'Sviluppi',technical:'Attività tecniche',commercial:'Attività commerciali',calendar:'Calendario scadenze',trash:'Cestino',dashboard:'Panoramica',customers:'Clienti',catalog:'Catalogo prodotti',products:'Installazioni e attivazioni',sales:'Vendite',inventory:'Disponibilità',workstations:'Hardware',admin:'Amministrazione',profile:'Il tuo profilo'};
 const groups:{label:string;items:{id:View;allowed:boolean}[]}[]=[{label:'IL TUO LAVORO',items:[{id:'dashboard',allowed:true},{id:'customers',allowed:!!me.canReadCustomers},{id:'contacts',allowed:!!me.canReadCustomers},{id:'calendar',allowed:!!me.canReadCustomers&&!!(me.canReadProducts||me.canReadContracts||me.canReadTraining||me.canReadActivities)}]},{label:'COMMERCIALE',items:[{id:'sales',allowed:!!me.canReadSales},{id:'margins',allowed:!!me.canReadMargins},{id:'catalog',allowed:!!me.canReadCatalog},{id:'commercial',allowed:!!me.canReadActivities}]},{label:'PARCO CLIENTI',items:[{id:'software',allowed:!!me.canReadProducts},{id:'workstations',allowed:!!me.canReadProducts},{id:'training',allowed:!!me.canReadTraining},{id:'contracts',allowed:!!me.canReadContracts},{id:'developments',allowed:!!me.canReadActivities},{id:'technical',allowed:!!me.canReadActivities},{id:'interventions',allowed:!!me.canReadActivities}]},{label:'GESTIONE',items:[{id:'notifications',allowed:!!me.canReadNotifications},{id:'documents',allowed:!!me.canReadDocuments},{id:'choices',allowed:!!me.canManageData},{id:'trash',allowed:!!me.canUseTrash},{id:'admin',allowed:!!me.canManageUsers||!!me.canManageData},{id:'profile',allowed:true}]}];
 const descriptions:Partial<Record<View,string>>={contacts:'Rubrica condivisa: persone, ruoli e associazioni a clienti e sedi.',software:'Licenze, release, provenienza e scadenze di tutti i clienti.',training:'Corsi, docenti e partecipanti, anche senza una vendita.',contracts:'Contratti di assistenza, scadenze e monte ore.',developments:'Progetti di sviluppo e personalizzazioni per i clienti.',technical:'Interventi e attività tecniche dei clienti.',commercial:'Appuntamenti e attività commerciali.',customers:'Anagrafiche, recapiti e contatti sempre a portata di mano.',catalog:'Prodotti, servizi e formazione offerti dall’azienda, indipendenti dai clienti.',products:'Hardware, software, licenze e garanzie associati ai clienti.',sales:'Le vendite ai clienti, con quantità e disponibilità sotto controllo.',inventory:'Quantità vendute assegnabili alle installazioni; la formazione resta nelle vendite.',workstations:'Dispositivi dei clienti, nostri o di terzi, censibili anche senza una vendita.',admin:'Gestisci utenti, autorizzazioni e strumenti dell’ambiente.',profile:'Le informazioni del tuo account Penny.'};
 return <div className="penny-shell"><a className="penny-skip" href="#penny-main">Vai al contenuto</a>{menuOpen&&<button className="penny-overlay" tabIndex={-1} aria-label="Chiudi navigazione" onClick={()=>setMenuOpen(false)}/>}
 <aside id="penny-navigation" className={'penny-sidebar '+(menuOpen?'penny-sidebar-open':'')} aria-label="Navigazione principale"><Brand compact/><nav aria-label="Moduli">{groups.map(group=>group.items.some(i=>i.allowed)&&<div className="penny-nav-group" key={group.label}><span className="penny-nav-caption">{group.label}</span>{group.items.filter(i=>i.allowed).map(i=><button key={i.id} className={view===i.id?'active':''} onClick={()=>navigate(i.id)} aria-current={view===i.id?'page':undefined}><Icon name={i.id}/><span>{labels[i.id]}</span></button>)}</div>)}</nav><div className="penny-sidebar-foot"><span className="penny-avatar">{me.name.slice(0,1).toUpperCase()}</span><div><strong>{me.name}</strong><small>{me.email}</small></div></div></aside>
 <div className="penny-workspace"><header className="penny-topbar"><button id="penny-menu-toggle" className="penny-menu-toggle" aria-label={menuOpen?'Chiudi navigazione':'Apri navigazione'} aria-expanded={menuOpen} aria-controls="penny-navigation" onClick={()=>setMenuOpen(!menuOpen)}><Icon name="menu"/></button><span className="penny-breadcrumb">Workspace <span aria-hidden="true">/</span> <strong>{labels[view]}</strong></span><ThemeSelector/><button className="penny-logout" onClick={logout} disabled={busy}>{busy?'Uscita…':'Esci'}</button></header><main id="penny-main" tabIndex={-1} className="penny-main">{error&&<p className="penny-alert" role="alert">{error}</p>}{view==='dashboard'?<Dashboard name={me.name} profile={me} onNavigate={navigate}/>:<div className="penny-content" key={view}><div className="penny-page-heading"><span className="penny-eyebrow">{groups.find(g=>g.items.some(i=>i.id===view))?.label}</span><h1>{labels[view]}</h1><p>{descriptions[view]}</p></div><section className={'penny-surface '+(view==='admin'?'penny-admin-surface':'')}>
 {view==='contacts'&&me.canReadCustomers&&<ContactDirectory canWrite={!!me.canWriteCustomers} canDeleteRecords={!!me.canDeleteRecords}/>}
 {view==='software'&&me.canReadProducts&&<ProductsPanel canReadFinancials={!!me.canReadSales} canWriteFinancials={!!me.canWriteSales} category="SOFTWARE" canDeleteRecords={!!me.canDeleteRecords} canReadCatalog={!!me.canReadCatalog} canWrite={!!me.canWriteProducts} canReadCustomers={!!me.canReadCustomers} canReadWorkstations={!!me.canReadWorkstations}/>}
 {view==='catalog'&&me.canReadCatalog&&<CatalogPanel canDeleteRecords={!!me.canDeleteRecords} canWrite={!!me.canWriteCatalog}/>}
 {view==='products'&&me.canReadProducts&&<ProductsPanel canReadFinancials={!!me.canReadSales} canWriteFinancials={!!me.canWriteSales} canDeleteRecords={!!me.canDeleteRecords} canReadCatalog={!!me.canReadCatalog} canWrite={!!me.canWriteProducts} canReadCustomers={!!me.canReadCustomers} canReadWorkstations={!!me.canReadWorkstations}/>}
 {view==='customers'&&me.canReadCustomers&&<CustomersPanel canReadFinancials={!!me.canReadSales} canWriteFinancials={!!me.canWriteSales} canReadTraining={!!me.canReadTraining} canWriteTraining={!!me.canWriteTraining} canReadContracts={!!me.canReadContracts} canWriteContracts={!!me.canWriteContracts} canReadActivities={!!me.canReadActivities} canWriteActivities={!!me.canWriteActivities} canReadProducts={!!me.canReadProducts} canWriteProducts={!!me.canWriteProducts} canReadCatalog={!!me.canReadCatalog} canReadWorkstations={!!me.canReadWorkstations} canDeleteRecords={!!me.canDeleteRecords} canWrite={!!me.canWriteCustomers}/>}
 {view==='sales'&&me.canReadSales&&<SalesPanel canReadMargins={!!me.canReadMargins} canWriteMargins={!!me.canWriteMargins} canRegisterProducts={!!me.canReadProducts&&!!me.canWriteProducts} canRegisterTraining={!!me.canWriteTraining} canRegisterContracts={!!me.canWriteContracts} canManageLinks={!!me.canReadInventory&&!!me.canReadAllocations&&!!me.canWriteAllocations} canReadProducts={!!me.canReadProducts} canWrite={!!me.canWriteSales} canReadCustomers={!!me.canReadCustomers} canReadCatalog={!!me.canReadCatalog}/>}
 {view==='inventory'&&me.canReadInventory&&<InventoryPanel canReadAllocations={!!me.canReadAllocations} canAllocate={!!me.canWriteAllocations} canReadCustomers={!!me.canReadCustomers} canReadProducts={!!me.canReadProducts}/>}
 {view==='workstations'&&me.canReadProducts&&<HardwarePanel profile={me}/>}{view==='calendar'&&me.canReadCustomers&&<CalendarPanel canReadProducts={!!me.canReadProducts} canReadRecords={!!(me.canReadTraining||me.canReadContracts||me.canReadActivities)}/>}
 {view==='training'&&me.canReadTraining&&<CustomerRecordsPanel canReadFinancials={!!me.canReadSales} canWriteFinancials={!!me.canWriteSales} kind="TRAINING" canWrite={!!me.canWriteTraining} canDeleteRecords={!!me.canDeleteRecords} canReadCustomers={!!me.canReadCustomers} canReadCatalog={!!me.canReadCatalog}/>}
 {view==='contracts'&&me.canReadContracts&&<CustomerRecordsPanel canReadFinancials={!!me.canReadSales} canWriteFinancials={!!me.canWriteSales} kind="CONTRACT" canWrite={!!me.canWriteContracts} canDeleteRecords={!!me.canDeleteRecords} canReadCustomers={!!me.canReadCustomers} canReadCatalog={!!me.canReadCatalog}/>}
 {(['developments','technical','commercial'] as View[]).includes(view)&&me.canReadActivities&&<CustomerRecordsPanel canReadFinancials={!!me.canReadSales} canWriteFinancials={!!me.canWriteSales} kind={view==='developments'?'DEVELOPMENT':view==='technical'?'TECHNICAL':'COMMERCIAL'} canWrite={!!me.canWriteActivities} canDeleteRecords={!!me.canDeleteRecords} canReadCustomers={!!me.canReadCustomers} canReadCatalog={!!me.canReadCatalog}/>}
 {view==='margins'&&me.canReadMargins&&<MarginsPanel canWrite={!!me.canWriteMargins}/>}
 {view==='choices'&&me.canManageData&&<ChoicesPanel/>}
 {view==='notifications'&&me.canReadNotifications&&<WorkspaceUtilitiesPanel mode="notifications" canWrite={!!me.canWriteNotifications} canReadCustomers={!!me.canReadCustomers}/>}
 {view==='documents'&&me.canReadDocuments&&<WorkspaceUtilitiesPanel mode="documents" canWrite={!!me.canWriteDocuments} canReadCustomers={!!me.canReadCustomers}/>}
 {view==='interventions'&&me.canReadActivities&&<CustomerRecordsPanel kind="INTERVENTION" canDeleteRecords={!!me.canDeleteRecords} canWrite={!!me.canWriteActivities} canReadFinancials={!!me.canReadSales} canWriteFinancials={!!me.canWriteSales} canReadCustomers={!!me.canReadCustomers} canReadCatalog={!!me.canReadCatalog}/>}
 {view==='trash'&&me.canUseTrash&&<TrashPanel/>}
 {view==='admin'&&me.canManageUsers&&<AdminPanel canManageData={!!me.canManageData}/>}{view==='admin'&&!me.canManageUsers&&me.canManageData&&<DemoDataPanel canManageData/>}
 {view==='profile'&&<div className="penny-profile"><h2>Dati personali</h2><dl><dt>Nome</dt><dd>{me.name}</dd><dt>Email</dt><dd>{me.email}</dd></dl><button onClick={onRefresh}>Aggiorna profilo</button></div>}
 </section></div>}</main><footer className="penny-footer">Penny CRM <span aria-hidden="true">·</span> Il tuo lavoro, con chiarezza.</footer></div></div>;
}
export function App(){const {me,setMe,loading}=useProfile();const [notice,setNotice]=useState('');const [refreshKey,setRefreshKey]=useState(0);
 useEffect(()=>{if(!refreshKey)return;fetch('/api/auth/me',{credentials:'same-origin'}).then(async r=>{if(r.ok)setMe(await r.json() as Profile);else setMe(null);}).catch(()=>setNotice('Impossibile aggiornare il profilo.'));},[refreshKey,setMe]);
 if(loading)return <main className="penny-loading" role="status">Caricamento Penny…</main>;
 if(!me)return <><AuthScreen onLogin={p=>{setNotice('');setMe(p);}}/>{notice&&<p className="penny-global-notice" role="status">{notice}</p>}</>;
 if(me.mustChangePassword)return <ChangePassword onChanged={()=>{setMe(null);setNotice('Password aggiornata. Accedi con la nuova password.');}}/>;
 return <Shell me={me} onLogout={()=>{setMe(null);setNotice('');}} onRefresh={()=>setRefreshKey(x=>x+1)}/>;
}
const root=document.getElementById('root');
if(root)createRoot(root).render(<React.StrictMode><App/></React.StrictMode>);

import Pagination from './Pagination';
import DemoDataPanel from './DemoDataPanel';
import React,{useEffect,useState} from 'react';
type User={id:number;email:string;fullName:string;enabled:boolean;mustChangePassword:boolean};
type Role={id:number;code:string;description:string};
type Permission={code:string;description:string};
type Effect={permissionCode:string;effect:'ALLOW'|'DENY'};
async function request<T>(path:string,method='GET',body?:unknown):Promise<T>{
 const headers:Record<string,string>={'Content-Type':'application/json'};
 if(method!=='GET'){
  const csrf=await fetch('/api/auth/csrf',{credentials:'same-origin'});
  if(!csrf.ok)throw Error('Token CSRF non disponibile');
  headers['X-XSRF-TOKEN']=(await csrf.json()).token;
 }
 const res=await fetch('/api/admin'+path,{method,headers,credentials:'same-origin',body:body===undefined?undefined:JSON.stringify(body)});
 if(!res.ok)throw Error(`HTTP ${res.status}: ${await res.text()}`);
 return res.json();
}
export default function AdminPanel({canManageData=false}:{canManageData?:boolean}){
 const [section,setSection]=useState<'users'|'data'>('users');
 const [users,setUsers]=useState<User[]>([]),[roles,setRoles]=useState<Role[]>([]),[permissions,setPermissions]=useState<Permission[]>([]);
 const [userPage,setUserPage]=useState(0);
 useEffect(()=>{setUserPage(p=>Math.min(p,Math.max(0,Math.ceil(users.length/20)-1)));},[users.length]);
 const [selected,setSelected]=useState<number|null>(null),[assigned,setAssigned]=useState<number[]>([]),[overrides,setOverrides]=useState<Effect[]>([]);
 const [email,setEmail]=useState(''),[name,setName]=useState(''),[roleCode,setRoleCode]=useState(''),[roleDescription,setRoleDescription]=useState('');
 const [temporary,setTemporary]=useState(''),[message,setMessage]=useState(''),[busy,setBusy]=useState(false);
 async function refresh(){const [u,r,p]=await Promise.all([request<User[]>('/users'),request<Role[]>('/roles'),request<Permission[]>('/permissions')]);setUsers(u);setRoles(r);setPermissions(p);}
 useEffect(()=>{refresh().catch(e=>setMessage(e.message));},[]);
 useEffect(()=>{if(selected!==null)Promise.all([request<Role[]>(`/users/${selected}/roles`),request<Effect[]>(`/users/${selected}/permissions`)]).then(([r,p])=>{setAssigned(r.map(x=>x.id));setOverrides(p);}).catch(e=>setMessage(e.message));},[selected]);
 async function act(task:()=>Promise<unknown>){setBusy(true);setMessage('');try{await task();await refresh();if(selected!==null){const [r,p]=await Promise.all([request<Role[]>(`/users/${selected}/roles`),request<Effect[]>(`/users/${selected}/permissions`)]);setAssigned(r.map(x=>x.id));setOverrides(p);}}catch(e){setMessage(e instanceof Error?e.message:'Errore');}finally{setBusy(false);}}
 const sections=canManageData&&<div className="penny-segmented" role="group" aria-label="Sezioni amministrazione"><button type="button" aria-pressed={section==='users'} onClick={()=>setSection('users')}>Utenti e permessi</button><button type="button" aria-pressed={section==='data'} onClick={()=>setSection('data')}>Dati demo e reset</button></div>;
 if(section==='data'&&canManageData)return <div className="admin-panel"><h2>Amministrazione</h2>{sections}<DemoDataPanel canManageData/></div>;
 return <div className="admin-panel"><h2>Amministrazione</h2>{sections}
 <form onSubmit={e=>{e.preventDefault();void act(async()=>{const result=await request<{temporaryPassword:string}>('/users','POST',{email,fullName:name});setTemporary(result.temporaryPassword);setEmail('');setName('');});}}>
 <h3>Nuovo utente</h3><label>Email<input type="email" required value={email} onChange={e=>setEmail(e.target.value)}/></label><label>Nome completo<input required value={name} onChange={e=>setName(e.target.value)}/></label><button disabled={busy}>Crea utente</button></form>
 {temporary&&<div className="temporary-password" role="status"><strong>Password temporanea (visibile una sola volta):</strong> <code>{temporary}</code> <button type="button" onClick={()=>{void navigator.clipboard.writeText(temporary);}}>Copia</button><button type="button" onClick={()=>setTemporary('')}>Nascondi</button></div>}
 <h3>Utenti</h3><div className="admin-users">{users.slice(userPage*20,(userPage+1)*20).map(u=><button className={selected===u.id?'selected':''} key={u.id} onClick={()=>setSelected(u.id)}>{u.fullName} — {u.email} {u.enabled?'':'(disabilitato)'}</button>)}</div><Pagination total={users.length} page={userPage} onPageChange={setUserPage} singular="utente" plural="utenti" label="Utenti" disabled={busy}/>
 {selected!==null&&(()=>{const u=users.find(x=>x.id===selected);return u?<div className="admin-detail"><h3>{u.fullName}</h3><button disabled={busy} onClick={()=>void act(async()=>{await request(`/users/${u.id}`,'PATCH',{enabled:!u.enabled});})}>{u.enabled?'Disabilita':'Riattiva'}</button> <button disabled={busy} onClick={()=>void act(async()=>{const r=await request<{temporaryPassword:string}>(`/users/${u.id}/reset-password`,'POST');setTemporary(r.temporaryPassword);})}>Reset password</button> <button disabled={busy} onClick={()=>void act(async()=>{await request(`/users/${u.id}/force-password-change`,'POST');})}>Forza cambio password</button>
 <h4>Ruoli</h4>{roles.map(r=><label className="admin-inline" key={r.id}><input type="checkbox" checked={assigned.includes(r.id)} onChange={e=>void act(async()=>{await request(`/users/${u.id}/roles/${r.id}`,e.target.checked?'PUT':'DELETE');})}/>{r.code}</label>)}
 <h4>Permessi individuali</h4>{permissions.map(p=><label key={p.code} className="admin-inline">{p.description}<select value={overrides.find(x=>x.permissionCode===p.code)?.effect||''} onChange={e=>void act(async()=>{const value=e.target.value;if(value)await request(`/users/${u.id}/permissions/${p.code}`,'PUT',{effect:value});else await request(`/users/${u.id}/permissions/${p.code}`,'DELETE');})}><option value="">Eredita</option><option value="ALLOW">Consenti</option><option value="DENY">Nega</option></select></label>)}
 </div>:null;})()}
 <form onSubmit={e=>{e.preventDefault();void act(async()=>{await request('/roles','POST',{code:roleCode,description:roleDescription});setRoleCode('');setRoleDescription('');});}}><h3>Nuovo ruolo</h3><label>Codice<input required pattern="[A-Z][A-Z0-9_]{1,79}" value={roleCode} onChange={e=>setRoleCode(e.target.value.toUpperCase())}/></label><label>Descrizione<input required value={roleDescription} onChange={e=>setRoleDescription(e.target.value)}/></label><button disabled={busy}>Crea ruolo</button></form>
 <h3>Permessi dei ruoli</h3>{roles.map(r=><details key={r.id}><summary>{r.code} — {r.description}</summary><RolePermissions role={r} permissions={permissions}/></details>)}
 {message&&<p className="message" role="alert">{message}</p>}
 </div>;
}
function RolePermissions({role,permissions}:{role:Role;permissions:Permission[]}){
 const [effects,setEffects]=useState<Effect[]>([]),[error,setError]=useState('');
 useEffect(()=>{request<Effect[]>(`/roles/${role.id}/permissions`).then(setEffects).catch(e=>setError(e.message));},[role.id]);
 async function change(code:string,value:string){try{await request(`/roles/${role.id}/permissions/${code}`,value?'PUT':'DELETE',value?{effect:value}:undefined);setEffects(await request<Effect[]>(`/roles/${role.id}/permissions`));}catch(e){setError(e instanceof Error?e.message:'Errore');}}
 return <div>{permissions.map(p=><label className="admin-inline" key={p.code}>{p.description}<select value={effects.find(e=>e.permissionCode===p.code)?.effect||''} onChange={e=>void change(p.code,e.target.value)}><option value="">Non impostato</option><option value="ALLOW">Consenti</option><option value="DENY">Nega</option></select></label>)}{error&&<p role="alert">{error}</p>}</div>;
}

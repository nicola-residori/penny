export async function api<T>(path:string,method='GET',body?:unknown):Promise<T>{
 const headers:Record<string,string>={};
 if(body!==undefined)headers['Content-Type']='application/json';
 if(method!=='GET'){
  const r=await fetch('/api/auth/csrf',{credentials:'same-origin'});
  if(!r.ok)throw new Error('Sessione scaduta');
  headers['X-XSRF-TOKEN']=(await r.json() as {token:string}).token;
 }
 const r=await fetch(path,{method,headers,credentials:'same-origin',body:body===undefined?undefined:JSON.stringify(body)});
 if(!r.ok){
  const fallback=r.status===403?'Permesso insufficiente':r.status===401?'Sessione scaduta':r.status===409?'Dati in conflitto':'Operazione non riuscita';
  const error=await r.json().catch(()=>null) as {message?:string}|null;
  throw new Error(error?.message||fallback);
 }
 return await r.json() as T;
}
export type CatalogProduct={id:number;code:string;name:string;category:'HARDWARE'|'SOFTWARE'|'SERVICE'|'TRAINING';origin:'INTERNAL'|'THIRD_PARTY';manufacturer:string|null;model:string|null;description:string|null;supplier?:string|null;price?:number|string|null;categoryId?:number|null;categoryName?:string;operationalKind?:string;productTypeId?:number|null;productTypeName?:string|null;productTypeObsolete?:boolean;categoryActive?:boolean;unit?:string|null;supplyChannel?:'INTERNAL_SALE'|'EXTERNAL_SUPPLY'|null;hasImage?:boolean;active:boolean};

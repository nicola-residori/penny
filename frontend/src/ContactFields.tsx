import React from 'react';
import ChoiceField from './ChoiceField';
export type ContactDetails={firstName?:string|null;lastName?:string|null;companyMobile?:string|null;personalMobile?:string|null;contactStatus?:string|null;contactReference?:string|null};
export default function ContactFields<T extends ContactDetails>({value,onChange}:{value:T;onChange:(value:T)=>void}){
 const field=(key:keyof ContactDetails,label:string,choices?:string[])=> choices?<ChoiceField group={key} label={label} value={value[key]??''} onChange={v=>onChange({...value,[key]:v})} defaults={Object.fromEntries(choices.map(v=>[v,v]))}/>:<label key={key}>{label}<input maxLength={key.includes('Mobile')?40:200} value={value[key]??''} onChange={e=>onChange({...value,[key]:e.target.value,...((key==='firstName'||key==='lastName')?{fullName:[key==='firstName'?e.target.value:value.firstName,key==='lastName'?e.target.value:value.lastName].filter(Boolean).join(' ')}:{})})}/></label>;
 return <>{field('firstName','Nome')}{field('lastName','Cognome')}{field('companyMobile','Cellulare aziendale')}{field('personalMobile','Cellulare personale')}{field('contactStatus','Stato contatto',['ACTIVE','DISMISSED','OBSOLETE'])}{field('contactReference','Riferimento',['rif. sede','rif. amministrativo','rif. commerciale','rif. tecnico'])}</>;
}

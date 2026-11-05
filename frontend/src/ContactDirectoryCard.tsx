import {Button} from './ui';
import React,{useState} from 'react';
import {api} from './api';
import DetailModal from './DetailModal';
import {DossierFields} from './Dossier';
export default function ContactDirectoryCard({contactId,name}:{contactId:number;name:string}){
 const [contact,setContact]=useState<Record<string,any>|null>(null),[error,setError]=useState('');
 return <><Button type="button" onClick={()=>{setError('');api<Record<string,any>>('/api/contacts/'+contactId).then(setContact).catch(e=>setError(String(e)));}} variant="secondary">{name}</Button>{error&&<span role="alert">{error}</span>}<DetailModal open={!!contact} title={contact?.fullName||name} onClose={()=>setContact(null)}>{contact&&<DossierFields items={[["Nome",contact.firstName],["Cognome",contact.lastName],["E-mail aziendale",contact.email],["Telefono diretto",contact.phone],["Cellulare aziendale",contact.companyMobile],["Cellulare personale",contact.personalMobile],["Stato contatto",contact.contactStatus],["Riferimento",contact.contactReference]]}/>}</DetailModal></>;
}

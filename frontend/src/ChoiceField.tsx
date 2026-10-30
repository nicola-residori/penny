import React,{useEffect,useId,useState} from 'react';
import {api} from './api';
export default function ChoiceField({group,label,value,onChange,defaults}:{group:string;label:string;value:string;onChange:(value:string)=>void;defaults:Record<string,string>}){
 const id=useId(),[choices,setChoices]=useState(defaults);
 useEffect(()=>{let live=true;api<{items:{code:string;name?:string;label?:string}[]}>('/api/choices?group='+encodeURIComponent(group)).then(r=>{if(live)setChoices({...defaults,...Object.fromEntries(r.items.map(c=>[c.code,c.label||c.name||c.code]))});}).catch(()=>{});return()=>{live=false;};},[group]);
 return <><label htmlFor={id+'-input'}>{label}<input id={id+'-input'} list={id} maxLength={120} value={value} onChange={e=>onChange(e.target.value)}/></label><datalist id={id}>{Object.entries(choices).map(([code,name])=><option key={code} value={code}>{name}</option>)}</datalist></>;
}

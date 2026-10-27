import React from 'react';
function SearchIcon(){return <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 4.5 4.5" strokeLinecap="round"/></svg>;}
export function SearchField({label,placeholder,value,onChange,onSearch}:{label:string;placeholder:string;value:string;onChange:(value:string)=>void;onSearch:()=>void}){
 return <form className="penny-search" role="search" aria-label={label} onSubmit={e=>{e.preventDefault();onSearch();}}><span className="penny-search-icon"><SearchIcon/></span><input type="search" aria-label={label} placeholder={placeholder} maxLength={100} value={value} onChange={e=>onChange(e.target.value)}/><button className="penny-search-submit" type="submit">Cerca</button></form>;
}
export function SegmentedFilter({label,value,options,onChange}:{label:string;value:string;options:{value:string;label:string}[];onChange:(value:string)=>void}){
 return <div className="penny-segmented" role="group" aria-label={label}>{options.map(option=><button type="button" key={option.value} aria-pressed={value===option.value} onClick={()=>{if(value!==option.value)onChange(option.value);}}>{option.label}</button>)}</div>;
}
export function CreateAction({label,onClick}:{label:string;onClick:()=>void}){
 return <button className="penny-button-primary penny-toolbar-create" type="button" aria-label={'+ '+label} onClick={onClick}><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>{label}</button>;
}

import {Button,SearchInput} from './ui';
import React from 'react';
export function SearchField({label,placeholder,value,onChange,onSearch}:{label:string;placeholder?:string;value:string;onChange:(value:string)=>void;onSearch:()=>void}){
 const latest=React.useRef(onSearch),previous=React.useRef(value),timer=React.useRef<ReturnType<typeof setTimeout>|undefined>(undefined);latest.current=onSearch;
 React.useEffect(()=>{if(previous.current===value)return;previous.current=value;timer.current=setTimeout(()=>latest.current(),200);return()=>clearTimeout(timer.current);},[value]);
 return <form className="ui-search-field" role="search" aria-label={label} onSubmit={e=>{e.preventDefault();clearTimeout(timer.current);onSearch();}}><label>{label}<SearchInput placeholder={placeholder} maxLength={100} value={value} onChange={e=>onChange(e.target.value)}/></label></form>;
}
export function SegmentedFilter({label,value,options,onChange}:{label:string;value:string;options:{value:string;label:string}[];onChange:(value:string)=>void}){
 return <div className="penny-segmented" role="group" aria-label={label}>{options.map(option=><Button type="button" key={option.value} aria-pressed={value===option.value} onClick={()=>{if(value!==option.value)onChange(option.value);}} variant="ghost" presentation="content">{option.label}</Button>)}</div>;
}
export function CreateAction({label,onClick}:{label:string;onClick:()=>void}){
 return <Button className="penny-button-primary penny-toolbar-create" type="button" aria-label={'+ '+label} onClick={onClick} variant="primary"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>{label}</Button>;
}

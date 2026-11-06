import React from 'react';
export const openCustomerSupport='penny:open-customer-support';
export default function CustomerSupportLink({id,name}:{id?:number;name:string}){return id?<a className="support-customer-link" href={'?customerId='+id+'&customerTab=Tele-assistenza'} title={'Apri '+name+' · Tele-assistenza'} onKeyDown={e=>e.stopPropagation()} onClick={e=>{e.stopPropagation();if(e.button!==0||e.metaKey||e.ctrlKey||e.shiftKey||e.altKey)return;e.preventDefault();window.dispatchEvent(new CustomEvent(openCustomerSupport,{detail:{customerId:id}}));}}>{name}</a>:<>{name}</>;}

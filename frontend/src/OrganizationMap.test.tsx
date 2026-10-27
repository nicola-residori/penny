// @vitest-environment jsdom
import React from 'react';
import {it,expect,vi,afterEach} from 'vitest';
import {render,screen,cleanup,within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CustomerWorkspace from './CustomerWorkspace';
import CustomerContacts from './CustomerContacts';
import OrganizationMap from './OrganizationMap';
const site={id:11,name:'Direzione Milano',street:'Via Roma',city:'Milano',siteTypes:['LEGAL','ADMINISTRATIVE']};
const contact={id:7,fullName:'Anna Rossi',role:'Responsabile IT',isPrimary:true,siteId:11,customerCount:2};
function setup(){const fetcher=vi.fn(async(input:RequestInfo|URL,init?:RequestInit)=>{const p=String(input);let data:unknown={};
 if(p==='/api/auth/csrf')data={token:'csrf'};
 else if(p.startsWith('/api/customer-workspace/'))data={sites:[site],products:[],productsTotal:0,deadlines:[]};
 else if(p==='/api/customer-organization/1')data={customer:{id:1,name:'Atlas',lifecycleStatus:'CUSTOMER'},sites:[site],contacts:[contact,{id:8,fullName:'Luca Verdi',role:'Consulente',siteId:null,isPrimary:false,customerCount:1}]};
 else if(p==='/api/customer-organization/2')data={customer:{id:2,name:'Beta',lifecycleStatus:'PROSPECT'},sites:[],contacts:[]};
 else if(p==='/api/contacts/7')data={...contact,links:[{customerId:1,customerName:'Atlas',lifecycleStatus:'CUSTOMER',siteId:11,siteName:site.name,siteTypes:site.siteTypes,role:'Responsabile IT',isPrimary:true},{customerId:2,customerName:'Beta',lifecycleStatus:'PROSPECT',siteId:null,siteTypes:[],role:'Consulente',isPrimary:false}]};
 else if(p==='/api/customers/1/contacts')data=[contact];
 return {ok:true,json:async()=>data};});vi.stubGlobal('fetch',fetcher);return fetcher;}
afterEach(()=>{cleanup();vi.unstubAllGlobals();});
it('navigates company to shared contact and another company, with per-link roles and site types',async()=>{
 setup();const user=userEvent.setup();render(<CustomerWorkspace customerId={1} canWrite={false} anagrafica={null} contacts={null}/>);
 await user.click(screen.getByRole('tab',{name:'Organigramma'}));
 const map=await screen.findByRole('region',{name:'Organigramma delle relazioni'});
 expect(await within(map).findByText('Atlas')).toBeTruthy();expect(within(map).getByText('Legale')).toBeTruthy();expect(within(map).getByText('Amministrativa')).toBeTruthy();
 await user.click(within(map).getByRole('button',{name:'Apri contatto Anna Rossi'}));
 expect(await within(map).findByRole('heading',{name:'Anna Rossi'})).toBeTruthy();expect(within(map).getByText('Responsabile IT')).toBeTruthy();expect(within(map).getByText('Consulente')).toBeTruthy();expect(within(map).getByText('Senza sede associata')).toBeTruthy();
 await user.click(within(map).getByRole('button',{name:'Apri azienda Beta'}));expect(await within(map).findByRole('heading',{name:'Beta'})).toBeTruthy();expect(within(map).getByText('Nessuna sede registrata.')).toBeTruthy();
 await user.click(within(map).getByRole('button',{name:'Indietro nella mappa'}));expect(await within(map).findByRole('heading',{name:'Anna Rossi'})).toBeTruthy();
});
it('persists multiple site type selections and renders their badges',async()=>{
 const fetcher=setup();const user=userEvent.setup();render(<CustomerWorkspace customerId={1} canWrite anagrafica={null} contacts={null}/>);
 await user.click(screen.getByRole('tab',{name:'Sedi'}));await user.click(await screen.findByRole('button',{name:'Modifica sede'}));
 expect(screen.getByRole('button',{name:'Legale'}).getAttribute('aria-pressed')).toBe('true');
 await user.click(screen.getByRole('button',{name:'Operativa'}));await user.click(screen.getByRole('button',{name:'Salva sede'}));
 const saved=fetcher.mock.calls.find(([url,init])=>url==='/api/customer-sites/11'&&init?.method==='PUT');expect(saved).toBeTruthy();expect(JSON.parse(String(saved![1]!.body)).siteTypes).toEqual(['LEGAL','ADMINISTRATIVE','OPERATIONAL']);
});
it('opens a contact organization map with read-only access',async()=>{
 setup();render(<CustomerContacts customerId={1} canWrite={false}/>);await userEvent.click(await screen.findByRole('button',{name:'Organigramma di Anna Rossi'}));expect(await screen.findByRole('heading',{name:'Anna Rossi'})).toBeTruthy();expect(screen.queryByRole('button',{name:'Modifica contatto'})).toBeNull();
});

it('hides contacts on demand and retries a failed graph request',async()=>{
 const fetcher=setup();let fail=true;const good=fetcher.getMockImplementation()!;
 fetcher.mockImplementation(async(input,init)=>{if(String(input)==='/api/customer-organization/1'&&fail){fail=false;return {ok:false,status:403,json:async()=>({message:'Permesso insufficiente'})} as any;}return good(input,init);});
 render(<OrganizationMap customerId={1}/>);expect(await screen.findByRole('alert')).toBeTruthy();await userEvent.click(screen.getByRole('button',{name:'Riprova organigramma'}));
 await screen.findByRole('button',{name:'Apri contatto Anna Rossi'});await userEvent.click(screen.getByRole('button',{name:'Nascondi contatti'}));expect(screen.queryByRole('button',{name:'Apri contatto Anna Rossi'})).toBeNull();expect(screen.getByText('Direzione Milano')).toBeTruthy();
 await userEvent.click(screen.getByRole('button',{name:'Mostra contatti'}));expect(screen.getByRole('button',{name:'Apri contatto Anna Rossi'})).toBeTruthy();
});
it('ignores an obsolete contact response after navigating back',async()=>{
 const fetcher=setup();const good=fetcher.getMockImplementation()!;let resolve:(r:any)=>void=()=>{};
 fetcher.mockImplementation((input,init)=>String(input)==='/api/contacts/7'?new Promise(r=>{resolve=r;}):good(input,init));
 render(<OrganizationMap customerId={1}/>);await userEvent.click(await screen.findByRole('button',{name:'Apri contatto Anna Rossi'}));await userEvent.click(screen.getByRole('button',{name:'Indietro nella mappa'}));await screen.findByRole('heading',{name:'Atlas'});
 resolve({ok:true,json:async()=>({...contact,fullName:'Obsolete response',links:[]})});await new Promise(r=>setTimeout(r,0));expect(screen.queryByText('Obsolete response')).toBeNull();expect(screen.getByRole('heading',{name:'Atlas'})).toBeTruthy();
});

it('offers icon-only organization controls with tooltip descriptions and keyboard tab navigation',async()=>{
 setup();const user=userEvent.setup();const view=render(<CustomerWorkspace customerId={1} canWrite={false} anagrafica={null} contacts={null}/>);
 const tab=screen.getByRole('tab',{name:'Organigramma'});
 expect(tab.querySelector('svg')).toBeTruthy();expect(tab.getAttribute('aria-label')).toBe('Organigramma');expect(tab.textContent).toBe('');
 const tooltip=document.getElementById(tab.getAttribute('aria-describedby')||'');expect(tooltip?.getAttribute('role')).toBe('tooltip');
 screen.getByRole('tab',{name:'Contatti'}).focus();await user.keyboard('{ArrowRight}');expect(document.activeElement).toBe(tab);expect(tab.getAttribute('aria-selected')).toBe('true');expect(tooltip?.style.visibility).toBe('visible');
 view.unmount();render(<CustomerContacts customerId={1} canWrite={false}/>);
 const button=await screen.findByRole('button',{name:'Organigramma di Anna Rossi'});
 expect(button.querySelector('svg')).toBeTruthy();expect(button.textContent).toBe('');expect(button.getAttribute('title')).toBeNull();
 expect(document.getElementById(button.getAttribute('aria-describedby')||'')?.getAttribute('role')).toBe('tooltip');
 await user.click(button);expect(await screen.findByRole('heading',{name:'Anna Rossi'})).toBeTruthy();
});

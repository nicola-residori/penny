// @vitest-environment jsdom
import React from 'react';
import {it,expect,vi,afterEach} from 'vitest';
import {render,screen,cleanup,waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ContactDirectory from './ContactDirectory';
afterEach(()=>{cleanup();vi.unstubAllGlobals();});
it('requires a customer before creating a contact and supports an optional site',async()=>{
 const fetcher=vi.fn(async(path:unknown,options?:RequestInit)=>({ok:true,json:async()=>String(path).includes('/csrf')?{token:'test'}:String(path).includes('/api/customers?')?{items:[{id:10,name:'Atlas'}],total:1}:{items:[],total:0}}));vi.stubGlobal('fetch',fetcher);
 render(<ContactDirectory canWrite/>);const user=userEvent.setup();await user.click(screen.getByRole('button',{name:'Nuovo contatto'}));await user.type(screen.getByLabelText('Nome completo *'),'Maria Rossi');
 expect((screen.getByRole('button',{name:'Salva contatto'}) as HTMLButtonElement).disabled).toBe(true);
 await screen.findByRole('option',{name:'Atlas'});await user.selectOptions(screen.getByLabelText('Cliente/prospect da associare'),'10');await user.click(screen.getByRole('button',{name:'Aggiungi associazione'}));
 expect((screen.getByLabelText('Sede (facoltativa)') as HTMLSelectElement).value).toBe('');await user.click(screen.getByRole('button',{name:'Salva contatto'}));
 await waitFor(()=>expect(fetcher.mock.calls.some(c=>String(c[0])==='/api/contacts')).toBe(true));const mutation=fetcher.mock.calls.find(c=>c[0]==='/api/contacts');expect(JSON.parse(String(mutation?.[1]?.body))).toMatchObject({fullName:'Maria Rossi',links:[{customerId:10,siteId:null}]});
});
it('hides mutations for read-only users and supports directory sorting',async()=>{
 const fetcher=vi.fn(async(path:unknown)=>({ok:true,json:async()=>({items:[],total:0})}));vi.stubGlobal('fetch',fetcher);render(<ContactDirectory canWrite={false}/>);expect(screen.queryByRole('button',{name:'Nuovo contatto'})).toBeNull();await userEvent.click(screen.getByRole('button',{name:'Email'}));expect(screen.getByRole('columnheader',{name:'Email'}).getAttribute('aria-sort')).toBe('ascending');await userEvent.click(screen.getByRole('button',{name:'Email'}));expect(screen.getByRole('columnheader',{name:'Email'}).getAttribute('aria-sort')).toBe('descending');
});

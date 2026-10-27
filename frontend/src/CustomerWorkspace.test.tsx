// @vitest-environment jsdom
import React from 'react';
import {it,expect,vi,afterEach} from 'vitest';
import {render,screen,cleanup} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CustomerWorkspace from './CustomerWorkspace';
afterEach(()=>{cleanup();vi.unstubAllGlobals();});
it('keeps product tabs gated and provides the existing customer data',async()=>{
 vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>({sites:[],products:[],deadlines:[],productsTotal:0})})));
 render(<CustomerWorkspace customerId={4} canWrite={false} anagrafica={<p>Dati cliente</p>} contacts={<p>Referenti</p>}/>);
 await userEvent.click(screen.getByRole('tab',{name:'Anagrafica'}));expect(screen.getByText('Dati cliente')).toBeTruthy();
 expect(screen.queryByRole('tab',{name:'Software'})).toBeNull();
});
it('labels third party origin in text as well as color',async()=>{
 vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>({sites:[],products:[{id:3,name:'Gestionale',category:'SOFTWARE',origin:'THIRD_PARTY',status:'ACTIVE'}],deadlines:[],productsTotal:1})})));
 render(<CustomerWorkspace customerId={4} canWrite={false} canReadProducts anagrafica={null} contacts={null}/>);
 expect((await screen.findAllByText('Terze parti'))[0]).toBeTruthy();
});

it('keeps product queries scoped to the customer and selected category',async()=>{
 vi.stubGlobal('fetch',vi.fn(async(path)=>({ok:true,json:async()=>String(path).includes('customer-workspace')?{sites:[],products:[],deadlines:[],productsTotal:0}:{items:[],total:0}})));
 render(<CustomerWorkspace customerId={42} canWrite={false} canReadProducts canWriteProducts anagrafica={null} contacts={null}/>);
 await userEvent.click(screen.getByRole('tab',{name:'Software'}));
 await screen.findByText('Nessun prodotto trovato.');
 const calls=vi.mocked(fetch).mock.calls.map(c=>String(c[0]));
 expect(calls.some(path=>path.includes('/api/installed-products?')&&path.includes('customerId=42')&&path.includes('category=SOFTWARE'))).toBe(true);
 expect(calls.some(path=>path.startsWith('/api/customers'))).toBe(false);
 await userEvent.click(screen.getByRole('button',{name:'+ Nuovo prodotto'}));
 expect((screen.getByLabelText('Categoria') as HTMLSelectElement).value).toBe('SOFTWARE');
 expect((screen.getByLabelText('Categoria') as HTMLSelectElement).disabled).toBe(true);
 expect(screen.queryByLabelText('Cliente installazione')).toBeNull();
});
it('supports arrow, Home and End navigation with one tab stop',async()=>{
 vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>({sites:[],products:[],deadlines:[],productsTotal:0})})));
 render(<CustomerWorkspace customerId={4} canWrite={false} anagrafica={<p>Dati cliente</p>} contacts={<p>Referenti</p>}/>);
 const user=userEvent.setup();const overview=screen.getByRole('tab',{name:'Panoramica'});overview.focus();
 await user.keyboard('{ArrowRight}');expect(document.activeElement).toBe(screen.getByRole('tab',{name:'Anagrafica'}));expect(screen.getByText('Dati cliente')).toBeTruthy();
 await user.keyboard('{End}');expect(document.activeElement).toBe(screen.getByRole('tab',{name:'Sedi'}));
 await user.keyboard('{Home}');expect(document.activeElement).toBe(overview);
 expect(screen.getAllByRole('tab').filter(tab=>tab.tabIndex===0)).toHaveLength(1);
});
it('pages through all products so older installs can receive a site assignment',async()=>{
 vi.stubGlobal('fetch',vi.fn(async(path)=>({ok:true,json:async()=>({sites:[],products:[{id:String(path).includes('page=1')?101:1,name:String(path).includes('page=1')?'Installazione precedente':'Installazione recente',category:'SOFTWARE',origin:'INTERNAL',status:'ACTIVE',siteId:null}],deadlines:[],productsTotal:101})})));
 render(<CustomerWorkspace customerId={42} canWrite={false} canReadProducts canWriteProducts anagrafica={null} contacts={null}/>);
 expect((await screen.findAllByText('Installazione recente'))[0]).toBeTruthy();
 await userEvent.click(screen.getByRole('button',{name:'Prodotti successivi'}));
 expect((await screen.findAllByText('Installazione precedente'))[0]).toBeTruthy();
 expect(screen.getByLabelText('Sede di Installazione precedente')).toBeTruthy();
 expect(screen.getByRole('button',{name:'Prodotti successivi'}).hasAttribute('disabled')).toBe(true);
 await userEvent.click(screen.getByRole('button',{name:'Prodotti precedenti'}));
 expect((await screen.findAllByText('Installazione recente'))[0]).toBeTruthy();
});

it('shows the reference overview panels and separate software and hardware summaries',async()=>{
 vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>({sites:[],products:[],productsTotal:150,summary:{software:120,hardware:30,thirdParty:10},deadlines:[]})})));
 render(<CustomerWorkspace customerId={4} canWrite={false} canReadProducts anagrafica={<p>Dati reali</p>} contacts={null}/>);
 expect(await screen.findByRole('heading',{name:'Dati principali'})).toBeTruthy();
 expect(screen.getByRole('heading',{name:'Prossime scadenze'})).toBeTruthy();
 expect(screen.getByRole('heading',{name:'Software'})).toBeTruthy();
 expect(screen.getByRole('heading',{name:'Hardware'})).toBeTruthy();
 expect(screen.getByText('120')).toBeTruthy();expect(screen.getByText('30')).toBeTruthy();
});

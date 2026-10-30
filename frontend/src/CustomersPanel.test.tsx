// @vitest-environment jsdom
import React from 'react';
import {it,expect,vi,afterEach} from 'vitest';
import {render,screen,cleanup,waitFor,within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CustomersPanel from './CustomersPanel';
afterEach(()=>{cleanup();vi.unstubAllGlobals();});
it('opens only the selected customer dossier and preserves the customer table behind the overlay',async()=>{
 const customers=[{id:1,name:'Alfa S.r.l.',kind:'COMPANY',lifecycleStatus:'CUSTOMER',country:'Italia'},{id:2,name:'Beta S.r.l.',kind:'COMPANY',lifecycleStatus:'PROSPECT',country:'Italia'}];
 vi.stubGlobal('fetch',vi.fn(async(url)=>({ok:true,json:async()=>String(url).includes('/customer-workspace/')?{sites:[],products:[],deadlines:[],productsTotal:0}:String(url).includes('/contacts')?[]:{items:customers,total:2}})));
 render(<CustomersPanel canWrite={false}/>);
 await userEvent.click(await screen.findByRole('button',{name:'Alfa S.r.l.'}));
 const dialog=screen.getByRole('dialog',{name:'Alfa S.r.l.'});
 expect(within(dialog).queryByRole('region',{name:'Elenco clienti'})).toBeNull();
 expect(within(dialog).queryByText('Beta S.r.l.')).toBeNull();
 expect(within(dialog).getByRole('tablist',{name:'Scheda cliente'})).toBeTruthy();
 await userEvent.click(within(dialog).getByRole('button',{name:'Chiudi Alfa S.r.l.'}));
 expect(screen.queryByRole('dialog')).toBeNull();
 expect(screen.getByRole('button',{name:'Beta S.r.l.'})).toBeTruthy();
});
it('provides explicit active and archived filters with a clear selected state',async()=>{
 vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>({items:[],total:0})})));
 render(<CustomersPanel canWrite/>);const user=userEvent.setup();
 const active=screen.getByRole('button',{name:'Attivi'}),archived=screen.getByRole('button',{name:'Archiviati'});
 expect(active.getAttribute('aria-pressed')).toBe('true');expect(archived.getAttribute('aria-pressed')).toBe('false');
 await user.click(archived);expect(archived.getAttribute('aria-pressed')).toBe('true');
 await waitFor(()=>expect(vi.mocked(fetch).mock.calls.some(c=>String(c[0]).includes('archived=true'))).toBe(true));
 expect(screen.queryByRole('checkbox',{name:'Archiviati'})).toBeNull();
});
it('keeps search submission and creation permissions intact',async()=>{
 vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>({items:[],total:0})})));
 render(<CustomersPanel canWrite={false}/>);const user=userEvent.setup();
 await user.type(screen.getByRole('searchbox',{name:'Cerca clienti'}),'Aurora{Enter}');
 await waitFor(()=>expect(vi.mocked(fetch).mock.calls.some(c=>String(c[0]).includes('q=Aurora'))).toBe(true));
 expect(screen.queryByRole('button',{name:/Nuovo cliente/})).toBeNull();
});
it('does not discard an open form when the already selected filter is pressed',async()=>{
 vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>({items:[],total:0})})));
 render(<CustomersPanel canWrite/>);const user=userEvent.setup();
 await user.click(screen.getByRole('button',{name:'+ Nuovo cliente'}));
 await user.type(screen.getByLabelText('Nome / Ragione sociale *'),'Nuova azienda');
 await user.click(screen.getByRole('button',{name:'Attivi'}));
 expect((screen.getByLabelText('Nome / Ragione sociale *') as HTMLInputElement).value).toBe('Nuova azienda');
});
it('allows manual commercial status while explaining automatic first-sale conversion',async()=>{
 vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>({items:[],total:0})})));
 render(<CustomersPanel canWrite/>);await userEvent.click(screen.getByRole('button',{name:'+ Nuovo cliente'}));
 expect(screen.getByText(/Il prospect diventa cliente automaticamente alla prima vendita;/)).toBeTruthy();
 expect(screen.getByRole('combobox',{name:/Stato commerciale/})).toHaveProperty('value','PROSPECT');
 expect(screen.queryByRole('button',{name:'Diventa cliente'})).toBeNull();
});
it('shows Prospect as a read-only badge without a manual conversion button',async()=>{
 vi.stubGlobal('fetch',vi.fn(async(path)=>({ok:true,json:async()=>String(path).includes('customer-workspace')?{sites:[],products:[],deadlines:[],productsTotal:0}:{items:[{id:1,kind:'COMPANY',name:'Nuova opportunità',lifecycleStatus:'PROSPECT',country:'IT',archived:false}],total:1}})));
 render(<CustomersPanel canWrite/>);await userEvent.click(await screen.findByRole('button',{name:/Nuova opportunità/}));
 expect(screen.getAllByText('Prospect').length).toBeGreaterThan(0);
 expect(screen.queryByRole('button',{name:'Diventa cliente'})).toBeNull();
});
it('distinguishes clients and prospects with explicit list badges, not only color',async()=>{
 vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>({items:[{id:1,kind:'COMPANY',name:'Atlas Srl',lifecycleStatus:'CUSTOMER',country:'IT',archived:false},{id:2,kind:'PERSON',name:'Giulia Prospect',lifecycleStatus:'PROSPECT',country:'IT',archived:false}],total:2})})));
 render(<CustomersPanel canWrite={false}/>);const client=await screen.findByRole('button',{name:/Atlas Srl/});const prospect=screen.getByRole('button',{name:/Giulia Prospect/});
 expect(client.closest('tr')?.querySelector('[aria-label="Stato commerciale: Cliente"]')?.textContent).toBe('Cliente');expect(prospect.closest('tr')?.querySelector('[aria-label="Stato commerciale: Prospect"]')?.textContent).toBe('Prospect');
 expect(client.closest('tr')?.querySelector('.customer-status-badge--customer')).toBeTruthy();expect(prospect.closest('tr')?.querySelector('.customer-status-badge--prospect')).toBeTruthy();
});

it('shows commercial, administrative and corporate business fields for new customers',async()=>{
 vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>({items:[],total:0})})));
 render(<CustomersPanel canWrite/>);await userEvent.click(screen.getByRole('button',{name:'+ Nuovo cliente'}));
 for(const label of ['Settore','Sito web','Indirizzo PEC','Codice SDI','Pagamento predefinito','Coordinate bancarie 1','Coordinate bancarie 2','Note amministrative','Referente amministrativo','Referente commerciale / acquisti','Collaboratore','Insolvente'])expect(screen.getAllByLabelText(label).some(element=>element.closest('.customers-detail form'))).toBe(true);
 expect((screen.getByLabelText('Data inserimento') as HTMLInputElement).readOnly).toBe(true);
 await userEvent.selectOptions(screen.getByLabelText('Stato azienda',{selector:'select'}),'REPLACED');
 expect((screen.getByLabelText('Nuova azienda di riferimento') as HTMLSelectElement).required).toBe(true);
});

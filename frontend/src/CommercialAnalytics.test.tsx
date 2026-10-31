// @vitest-environment jsdom
import React from 'react';
import {it,expect,vi,afterEach} from 'vitest';
import {render,screen,cleanup,waitFor,within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Dashboard from './Dashboard';
const report={from:'2022-01-01',to:'2026-10-26',groupBy:'YEAR',customerId:null,permissions:{salesRead:true,customersRead:true},totals:{salesAmount:'1234.56',soldQuantity:15,saleCount:3,newCustomers:2,customersToDate:8,unknownCustomerDates:1},series:[{period:'2025-01-01',salesAmount:'0.00',soldQuantity:0,saleCount:0,newCustomers:0,customersToDate:6},{period:'2026-01-01',salesAmount:'1234.56',soldQuantity:15,saleCount:3,newCustomers:2,customersToDate:8}]};
function setup(){const fetcher=vi.fn(async(input:RequestInfo|URL)=>{const p=String(input);return {ok:true,json:async()=>p.startsWith('/api/analytics')?report:p.startsWith('/api/customers')?{items:[{id:9,name:'Atlas'}],total:1}:{}};});vi.stubGlobal('fetch',fetcher);return fetcher;}
afterEach(()=>{cleanup();vi.unstubAllGlobals();});
it('shows period charts, exact money and customer growth, and applies customer/date filters together',async()=>{
 const fetcher=setup();const user=userEvent.setup();render(<Dashboard name="Ada" profile={{canReadCustomers:true,canReadSales:true}} onNavigate={()=>{}}/>);
 const section=await screen.findByRole('region',{name:'Andamento commerciale'});await within(section).findByRole('heading',{name:'Valore delle vendite'});expect(within(section).getByRole('heading',{name:'Risorse vendute'})).toBeTruthy();expect(within(section).getByRole('heading',{name:'Clienti nel tempo'})).toBeTruthy();
 await user.selectOptions(screen.getByLabelText('Vista'),'customer');await screen.findByRole('option',{name:'Atlas'});await user.selectOptions(screen.getByLabelText('Cliente analizzato'),'9');
 await user.clear(screen.getByLabelText('Dal'));await user.type(screen.getByLabelText('Dal'),'2026-01-01');await user.clear(screen.getByLabelText('Al'));await user.type(screen.getByLabelText('Al'),'2026-12-31');await user.clear(screen.getByLabelText('Span'));await user.type(screen.getByLabelText('Span'),'2d');
 const count=fetcher.mock.calls.filter(([p])=>String(p).startsWith('/api/analytics')).length;await user.click(screen.getByRole('button',{name:'Applica intervallo'}));
 await waitFor(()=>expect(fetcher.mock.calls.filter(([p])=>String(p).startsWith('/api/analytics')).length).toBe(count+1));const last=String(fetcher.mock.calls.filter(([p])=>String(p).startsWith('/api/analytics')).at(-1)![0]);const params=new URL(last,'http://local').searchParams;expect(params.get('from')).toBe('2026-01-01');expect(params.get('to')).toBe('2026-12-31');expect(params.get('span')).toBe('2d');expect(params.get('customerId')).toBe('9');
 await user.click(screen.getByText('Dati dei grafici'));expect(within(section).getByRole('table')).toBeTruthy();expect(within(section).getAllByText(/1\.?234,56/).length).toBeGreaterThan(0);
});
it('rejects reversed or oversized monthly ranges without sending requests',async()=>{
 const f=setup();const user=userEvent.setup();render(<Dashboard name="Ada" profile={{canReadSales:true}} onNavigate={()=>{}}/>);await screen.findByRole('heading',{name:'Valore delle vendite'});
 await user.clear(screen.getByLabelText('Dal'));await user.type(screen.getByLabelText('Dal'),'2030-01-01');await user.clear(screen.getByLabelText('Al'));await user.type(screen.getByLabelText('Al'),'2029-01-01');const count=f.mock.calls.length;await user.click(screen.getByRole('button',{name:'Applica intervallo'}));expect(await screen.findByText('La data finale precede quella iniziale.')).toBeTruthy();expect(f.mock.calls.length).toBe(count);
 await user.clear(screen.getByLabelText('Dal'));await user.type(screen.getByLabelText('Dal'),'2000-01-01');await user.clear(screen.getByLabelText('Span'));await user.type(screen.getByLabelText('Span'),'2d');await user.click(screen.getByRole('button',{name:'Applica intervallo'}));expect(screen.getByText('Intervallo troppo ampio per questo span: massimo 500 periodi.')).toBeTruthy();
});
it('does not request analytics without permissions and hides forbidden sales metrics',async()=>{
 const f=setup();const v=render(<Dashboard name="Ada" profile={{}} onNavigate={()=>{}}/>);expect(screen.queryByRole('region',{name:'Andamento commerciale'})).toBeNull();expect(f.mock.calls.some(([p])=>String(p).startsWith('/api/analytics'))).toBe(false);v.unmount();
 f.mockImplementation(async(input)=>({ok:true,json:async()=>String(input).startsWith('/api/analytics')?{...report,permissions:{salesRead:false,customersRead:true},totals:{...report.totals,salesAmount:null,soldQuantity:null,saleCount:null},series:report.series.map(p=>({...p,salesAmount:null,soldQuantity:null,saleCount:null}))}:{}} as any));
 render(<Dashboard name="Ada" profile={{canReadCustomers:true}} onNavigate={()=>{}}/>);await screen.findByRole('heading',{name:'Clienti nel tempo'});expect(screen.queryByRole('heading',{name:'Valore delle vendite'})).toBeNull();expect(screen.queryByRole('heading',{name:'Risorse vendute'})).toBeNull();
});

it('offers archived customers for individual historical analysis',async()=>{
 const f=setup();const user=userEvent.setup();render(<Dashboard name="Ada" profile={{canReadSales:true,canReadCustomers:true}} onNavigate={()=>{}}/>);await screen.findByRole('heading',{name:'Valore delle vendite'});await user.selectOptions(screen.getByLabelText('Vista'),'customer');await user.selectOptions(screen.getByLabelText('Elenco clienti'),'archived');await waitFor(()=>expect(f.mock.calls.some(([p])=>String(p).startsWith('/api/customers?archived=true'))).toBe(true));
});

it('handles failed requests with retry and reports empty sales without hiding periods',async()=>{
 const f=setup();const good=f.getMockImplementation()!;let fail=true;
 f.mockImplementation(async(input)=>{if(String(input).startsWith('/api/analytics')){if(fail){fail=false;return {ok:false,status:503,json:async()=>({message:'Temporaneamente non disponibile'})} as any;}return {ok:true,json:async()=>({...report,totals:{...report.totals,salesAmount:'0.00',soldQuantity:0,saleCount:0},series:report.series.map(p=>({...p,salesAmount:'0.00',soldQuantity:0,saleCount:0}))})};}return good(input);});
 render(<Dashboard name="Ada" profile={{canReadSales:true}} onNavigate={()=>{}}/>);expect(await screen.findByRole('alert')).toBeTruthy();await userEvent.click(screen.getByRole('button',{name:'Riprova grafici'}));expect(await screen.findByText('Nessuna vendita confermata nell’intervallo selezionato.')).toBeTruthy();expect(screen.getByRole('heading',{name:'Valore delle vendite'})).toBeTruthy();
});
it('ignores a delayed result for the previous interval',async()=>{
 const f=setup();const good=f.getMockImplementation()!;let resolve:(value:any)=>void=()=>{};let analyticsCalls=0;
 f.mockImplementation((input)=>{if(String(input).startsWith('/api/analytics')&&++analyticsCalls===1)return new Promise(r=>{resolve=r;});return good(input);});
 render(<Dashboard name="Ada" profile={{canReadSales:true}} onNavigate={()=>{}}/>);await userEvent.click(screen.getByRole('button',{name:'Anno corrente'}));await userEvent.click(screen.getByRole('button',{name:'Applica intervallo'}));await screen.findByRole('heading',{name:'Valore delle vendite'});
 resolve({ok:true,json:async()=>({...report,totals:{...report.totals,salesAmount:'987654321.99'}})});await new Promise(r=>setTimeout(r,0));expect(screen.queryByText(/987.*321,99/)).toBeNull();
});

it('accepts free spans with presets and rejects minute spans for date-only sales',async()=>{
 const f=setup();const user=userEvent.setup();render(<Dashboard name="Ada" profile={{canReadSales:true}} onNavigate={()=>{}}/>);await screen.findByRole('heading',{name:'Valore delle vendite'});
 await user.clear(screen.getByLabelText('Dal'));await user.type(screen.getByLabelText('Dal'),'2026-01-01');await user.clear(screen.getByLabelText('Al'));await user.type(screen.getByLabelText('Al'),'2026-01-31');await user.clear(screen.getByLabelText('Span'));await user.type(screen.getByLabelText('Span'),'2w');await user.click(screen.getByRole('button',{name:'Applica intervallo'}));await waitFor(()=>expect(f.mock.calls.some(([p])=>String(p).includes('span=2w'))).toBe(true));
 await user.clear(screen.getByLabelText('Span'));await user.type(screen.getByLabelText('Span'),'1m');const n=f.mock.calls.length;await user.click(screen.getByRole('button',{name:'Applica intervallo'}));expect(screen.getByText('Span non valido: usa d, w, mon o y con un numero intero positivo.')).toBeTruthy();expect(f.mock.calls.length).toBe(n);
});

it('toggles the previous year immediately using applied customer filters, leaving draft changes unapplied',async()=>{
 const f=setup(),user=userEvent.setup();render(<Dashboard name="Ada" profile={{canReadSales:true,canReadCustomers:true}} onNavigate={()=>{}}/>);await screen.findByRole('heading',{name:'Valore delle vendite'});
 const toggle=screen.getByRole('switch',{name:'Confronta con l’anno precedente'});expect((toggle as HTMLInputElement).checked).toBe(false);expect(String(f.mock.calls.find(([p])=>String(p).startsWith('/api/analytics'))![0])).not.toContain('comparePreviousYear');
 await user.selectOptions(screen.getByLabelText('Vista'),'customer');await screen.findByRole('option',{name:'Atlas'});await user.selectOptions(screen.getByLabelText('Cliente analizzato'),'9');await user.click(screen.getByRole('button',{name:'Applica intervallo'}));await screen.findByRole('heading',{name:'Valore delle vendite'});
 const applied=new URL(String(f.mock.calls.filter(([p])=>String(p).startsWith('/api/analytics')).at(-1)![0]),'http://local').searchParams;
 await user.clear(screen.getByLabelText('Span'));await user.type(screen.getByLabelText('Span'),'2d');await user.click(toggle);await screen.findByRole('heading',{name:'Valore delle vendite'});
 let query=new URL(String(f.mock.calls.filter(([p])=>String(p).startsWith('/api/analytics')).at(-1)![0]),'http://local').searchParams;expect(query.get('comparePreviousYear')).toBe('true');for(const key of ['from','to','span','customerId'])expect(query.get(key)).toBe(applied.get(key));
 expect(screen.getAllByText('Anno precedente').length).toBe(3);await user.click(toggle);await screen.findByRole('heading',{name:'Valore delle vendite'});query=new URL(String(f.mock.calls.filter(([p])=>String(p).startsWith('/api/analytics')).at(-1)![0]),'http://local').searchParams;expect(query.has('comparePreviousYear')).toBe(false);
});

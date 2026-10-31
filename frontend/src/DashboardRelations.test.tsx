// @vitest-environment jsdom
import React from 'react';
import {afterEach,it,expect,vi} from 'vitest';
import {render,screen,cleanup,waitFor} from '@testing-library/react';
import {DashboardClients,DashboardDeadlines} from './DashboardRelations';
afterEach(()=>{cleanup();vi.unstubAllGlobals()});
const client={id:1,name:'Aurora',city:'Verona',lifecycleStatus:'CUSTOMER'};
it('does not request or display financial data without sales permission',async()=>{
 const f=vi.fn(async()=>({ok:true,json:async()=>({items:[client],total:1})}));vi.stubGlobal('fetch',f);
 render(<DashboardClients canReadSales={false} period={{from:'2026-01-01',to:'2026-10-31'}} onOpen={()=>{}} onNavigate={()=>{}}/>);
 await screen.findByRole('button',{name:'Aurora'});expect(screen.queryByText('Vendite nel periodo')).toBeNull();expect(f.mock.calls.length).toBe(1);
});
it('keeps Excel disabled until exact customer sales totals have completed',async()=>{
 let resolve!:(value:unknown)=>void;const pending=new Promise(r=>{resolve=r});vi.stubGlobal('fetch',vi.fn(async(input:RequestInfo|URL)=>({ok:true,json:async()=>String(input).startsWith('/api/analytics')?pending:{items:[client],total:1}})));
 render(<DashboardClients canReadSales period={{from:'2026-01-01',to:'2026-10-31'}} onOpen={()=>{}} onNavigate={()=>{}}/>);
 await screen.findByRole('button',{name:'Aurora'});expect((screen.getByRole('button',{name:'Excel'}) as HTMLButtonElement).disabled).toBe(true);
 resolve({totals:{salesAmount:'1234.56'}});await waitFor(()=>expect((screen.getByRole('button',{name:'Excel'}) as HTMLButtonElement).disabled).toBe(false));expect(screen.getByText(/1.?234,56/)).toBeTruthy();
});
it('distinguishes license and warranty events and respects deadline access',async()=>{
 const f=vi.fn(async()=>({ok:true,json:async()=>({items:[{customerId:1,customerName:'Aurora',productName:'CAD',date:'2026-11-04',type:'LICENSE'},{customerId:1,customerName:'Aurora',productName:'Server',date:'2026-11-06',type:'WARRANTY'}]})}));vi.stubGlobal('fetch',f);const count=vi.fn();
 const v=render(<DashboardDeadlines profile={{canReadCustomers:true,canReadProducts:true}} onCount={count} onOpen={()=>{}} onNavigate={()=>{}}/>);
 expect(await screen.findByText(/Scadenza licenza/)).toBeTruthy();expect(screen.getByText(/Fine garanzia/)).toBeTruthy();expect(count).toHaveBeenCalledWith(2);v.unmount();f.mockClear();
 render(<DashboardDeadlines profile={{}} onCount={count} onOpen={()=>{}} onNavigate={()=>{}}/>);expect(f).not.toHaveBeenCalled();
});

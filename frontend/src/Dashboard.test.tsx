// @vitest-environment jsdom
import React from 'react';
import {afterEach,it,expect,vi} from 'vitest';
import {render,screen,cleanup} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Dashboard from './Dashboard';
afterEach(()=>{cleanup();vi.unstubAllGlobals();});
it('shows authorized real totals and never turns unauthorized counters into zero',async()=>{
 vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>({customers:12,catalog:null,installed:3,workstations:2,soldQuantity:null,allocatedQuantity:4,availableQuantity:8,salesAmount:null})})));
 render(<Dashboard name="Ada Rossi" profile={{canReadCustomers:true}} onNavigate={()=>{}}/>);
 expect(await screen.findByText('12')).toBeTruthy();expect(screen.queryByText('Valore vendite')).toBeNull();expect(screen.queryByText('Catalogo prodotti')).toBeNull();
});
it('offers only permitted shortcuts and navigates to their module',async()=>{
 vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>({})})));const go=vi.fn();
 render(<Dashboard name="Ada" profile={{canReadProducts:true}} onNavigate={go}/>);
 await userEvent.click(screen.getByRole('button',{name:/Apri prodotti installati/}));expect(go).toHaveBeenCalledWith('products');expect(screen.queryByRole('button',{name:/Apri vendite/})).toBeNull();
});
it('reports overview failures and provides retry',async()=>{
 vi.stubGlobal('fetch',vi.fn(async()=>({ok:false,json:async()=>({message:'Riprova più tardi'})})));
 render(<Dashboard name="Ada" profile={{}} onNavigate={()=>{}}/>);expect(await screen.findByRole('alert')).toBeTruthy();expect(screen.getByRole('button',{name:'Riprova'})).toBeTruthy();
});
it('preserves cents in large commercial totals',async()=>{
 vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>({salesAmount:'999998999990000.01'})})));
 render(<Dashboard name="Ada" profile={{canReadSales:true}} onNavigate={()=>{}}/>);
 expect(await screen.findByText(/999.*01/)).toBeTruthy();
});
it('uses the approved overview composition and opens real customer actions',async()=>{
 vi.stubGlobal('fetch',vi.fn(async(input:RequestInfo|URL)=>({ok:true,json:async()=>String(input).startsWith('/api/customers')?{items:[{id:4,name:'Aurora',lifecycleStatus:'CUSTOMER',city:'Verona'}],total:1}:String(input).includes('deadlines')?{items:[{customerId:4,customerName:'Aurora',productName:'Licenza CAD',date:'2026-11-04',type:'LICENSE'}]}:{}})));
 const open=vi.fn();render(<Dashboard name="Ada" profile={{canReadCustomers:true,canWriteCustomers:true,canReadProducts:true}} onNavigate={()=>{}} onOpenCustomer={open}/>);
 expect(screen.getByRole('heading',{name:'Panoramica',level:1})).toBeTruthy();await userEvent.click(screen.getByRole('button',{name:'Nuovo cliente'}));expect(open).toHaveBeenCalledWith(null);
 await userEvent.click(await screen.findByRole('button',{name:'Aurora'}));expect(open).toHaveBeenCalledWith(4);
 expect(screen.getByRole('region',{name:'Prossime scadenze'})).toBeTruthy();
});
it('retains applied draft and comparison when dashboard data refreshes after closing a dossier',async()=>{
 vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>({})})));
 const props={name:'Ada',profile:{canReadSales:true},onNavigate:vi.fn()};const v=render(<Dashboard {...props} revision={0}/>);
 await userEvent.clear(screen.getByLabelText('Span'));await userEvent.type(screen.getByLabelText('Span'),'2d');await userEvent.click(screen.getByRole('switch'));
 v.rerender(<Dashboard {...props} revision={1}/>);expect((screen.getByLabelText('Span') as HTMLInputElement).value).toBe('2d');expect((screen.getByRole('switch') as HTMLInputElement).checked).toBe(true);
});

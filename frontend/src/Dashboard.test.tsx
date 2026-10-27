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

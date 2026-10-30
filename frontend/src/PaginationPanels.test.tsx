// @vitest-environment jsdom
import React from 'react';
import {afterEach,expect,test,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor,within} from '@testing-library/react';
import WorkstationsPanel from './WorkstationsPanel';
import LookupSelect from './LookupSelect';
import SalesPanel from './SalesPanel';
import InventoryPanel from './InventoryPanel';
import {api} from './api';
vi.mock('./api',()=>({api:vi.fn()}));
afterEach(()=>{cleanup();vi.resetAllMocks();});
test('hardware groups hide single-page navigation and reset searches to page one',async()=>{
 vi.mocked(api).mockImplementation(async(path)=>({items:[],total:path.includes('q=unique')?1:41}) as never);
 render(<WorkstationsPanel canWrite={false} canReadCustomers={false}/>);
 await screen.findByText('Pagina 1 di 3');fireEvent.click(screen.getByRole('button',{name:'Successiva'}));await screen.findByText('Pagina 2 di 3');
 fireEvent.change(screen.getByLabelText('Cerca gruppi hardware'),{target:{value:'unique'}});
 await screen.findByText('1 gruppo hardware');expect(screen.queryByRole('button',{name:'Successiva'})).toBeNull();expect(screen.queryByText(/Pagina/)).toBeNull();
 expect(vi.mocked(api).mock.calls.some(([path])=>path.includes('q=unique')&&path.includes('page=0'))).toBe(true);
});
test('lookup clamps an out-of-range result and disables navigation while loading',async()=>{
 let resolve:((value:unknown)=>void)|undefined;
 vi.mocked(api).mockResolvedValue({items:[],total:41} as never);
 render(<LookupSelect label="Cliente" path="/api/customers" value={0} onChange={vi.fn()}/>);
 await screen.findByText('Pagina 1 di 3');
 vi.mocked(api).mockImplementation(()=>new Promise(r=>{resolve=r;}) as never);
 fireEvent.click(screen.getByRole('button',{name:'Successivi cliente'}));
 await waitFor(()=>expect((screen.getByRole('button',{name:'Successivi cliente'}) as HTMLButtonElement).disabled).toBe(true));
 vi.mocked(api).mockResolvedValue({items:[],total:1} as never);resolve?.({items:[],total:1});
 await screen.findByText('1 risultato');await waitFor(()=>expect(vi.mocked(api).mock.calls.at(-1)?.[0]).toContain('page=0'));
 expect(screen.queryByRole('button')).toBeNull();
});

test('sales and availability keep single-page counts without navigation',async()=>{
 vi.mocked(api).mockResolvedValue({items:[{id:1,customerName:'Acme',productName:'Suite',totalAmount:10,quantity:1,unitPrice:10,allocatedQuantity:0,availableQuantity:1}],total:1,totals:{soldQuantity:1,allocatedQuantity:0,availableQuantity:1}} as never);
 const sales=render(<SalesPanel canWrite={false} canReadCustomers={false}/>);
 await within(screen.getByRole('region',{name:'Elenco Vendite'})).findByText('1 risultato');expect(screen.queryByRole('button',{name:'Successiva'})).toBeNull();sales.unmount();
 render(<InventoryPanel canAllocate={false} canReadCustomers={false}/>);await screen.findByText('1 vendita');expect(screen.queryByText(/Pagina/)).toBeNull();
});

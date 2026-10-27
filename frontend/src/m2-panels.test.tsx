// @vitest-environment jsdom
import React from 'react';
import {test,expect,vi,afterEach} from 'vitest';
import {render,screen,fireEvent,waitFor,cleanup} from '@testing-library/react';
import SalesPanel from './SalesPanel';
import InventoryPanel from './InventoryPanel';
import {api} from './api';
vi.mock('./api',()=>({api:vi.fn()}));
afterEach(()=>{cleanup();vi.resetAllMocks();});
test('vendita richiede una motivazione per annullare e mantiene la cronologia',async()=>{
 vi.mocked(api).mockImplementation(async(path)=> path.includes('summary')?{items:[],total:0,totals:{soldQuantity:2,allocatedQuantity:0,availableQuantity:2,totalAmount:20}}:path.includes('/cancel')?{cancelled:true}:{items:[{id:1,customerName:'Acme',productName:'Suite',soldOn:'2026-10-25',quantity:2,unitPrice:10,totalAmount:20,status:'CONFIRMED',allocatedQuantity:0,availableQuantity:2}],total:1} as never);
 render(<SalesPanel canWrite canReadCustomers={false} canReadCatalog={false}/>);
 fireEvent.click(await screen.findByRole('button',{name:'Annulla vendita'}));
 expect(screen.getByLabelText('Motivo annullamento')).toBeTruthy();
 fireEvent.change(screen.getByLabelText('Motivo annullamento'),{target:{value:'Ordine duplicato'}});
 fireEvent.click(screen.getByRole('button',{name:'Conferma annullamento'}));
 await waitFor(()=>expect(api).toHaveBeenCalledWith('/api/sales/1/cancel','POST',{reason:'Ordine duplicato'}));
});
test('inventario non espone importi e mostra totali',async()=>{
 vi.mocked(api).mockResolvedValue({items:[],total:0,totals:{soldQuantity:120,allocatedQuantity:20,availableQuantity:100}} as never);
 render(<InventoryPanel canAllocate={false}/>);
 expect(await screen.findByText('120')).toBeTruthy();expect(screen.queryByText(/€/)).toBeNull();
});
test('crea vendita con cliente e catalogo selezionati',async()=>{
 vi.mocked(api).mockImplementation(async(path,method)=>method==='POST'?{id:3}:path.includes('/customers')?{items:[{id:1,name:'Acme'}],total:1}:path.includes('catalog-products')?{items:[{id:2,name:'Suite'}],total:1}:path.includes('summary')?{totals:{soldQuantity:0,allocatedQuantity:0,availableQuantity:0,totalAmount:0}}:{items:[],total:0} as never);
 render(<SalesPanel canWrite/>);fireEvent.click(screen.getByRole('button',{name:'Nuova vendita'}));
 await screen.findByRole('option',{name:'Suite'});
 fireEvent.change(screen.getByLabelText('Cliente',{selector:'select'}),{target:{value:'1'}});
 fireEvent.change(screen.getByLabelText('Prodotto catalogo',{selector:'select'}),{target:{value:'2'}});
 fireEvent.click(screen.getByRole('button',{name:'Salva vendita'}));
 await waitFor(()=>expect(api).toHaveBeenCalledWith('/api/sales','POST',expect.objectContaining({customerId:1,catalogProductId:2,quantity:1,unitPrice:'0'})));
});
test('assegna quantità solo a installazioni compatibili e sfoglia oltre cento',async()=>{
 vi.mocked(api).mockImplementation(async(path,method)=>method==='POST'?{id:5}:path.includes('installed-products')?{items:[{id:9,name:'Corretta',catalogProductId:2,status:'ACTIVE'},{id:10,name:'Altro prodotto',catalogProductId:3,status:'ACTIVE'},{id:11,name:'Dismessa',catalogProductId:2,status:'RETIRED'}],total:121}:path.includes('allocations')?{items:[],total:0}:{items:[{id:1,customerId:1,catalogProductId:2,customerName:'Acme',productName:'Suite',quantity:3,allocatedQuantity:0,availableQuantity:3}],total:1,totals:{soldQuantity:3,allocatedQuantity:0,availableQuantity:3}} as never);
 render(<InventoryPanel canAllocate canReadCustomers={false}/>);
 fireEvent.click(await screen.findByRole('button',{name:'Gestisci assegnazioni'}));
 await screen.findByRole('option',{name:'Corretta'});
 expect(screen.queryByRole('option',{name:'Altro prodotto'})).toBeNull();expect(screen.queryByRole('option',{name:'Dismessa'})).toBeNull();
 fireEvent.click(screen.getByRole('button',{name:'Successivi installazione compatibile'}));
 await waitFor(()=>expect(api).toHaveBeenCalledWith(expect.stringContaining('page=1')));
 fireEvent.change(screen.getByLabelText('Installazione compatibile',{selector:'select'}),{target:{value:'9'}});
 fireEvent.change(screen.getByLabelText('Quantità da assegnare'),{target:{value:'2'}});
 fireEvent.click(screen.getByRole('button',{name:'Assegna quantità'}));
 await waitFor(()=>expect(api).toHaveBeenCalledWith('/api/allocations','POST',{saleId:1,installedProductId:9,quantity:2}));
});
test('senza lettura assegnazioni non espone controlli né interroga assegnazioni',async()=>{
 vi.mocked(api).mockResolvedValue({items:[{id:1,customerId:1,catalogProductId:2,customerName:'Acme',productName:'Suite',quantity:3,allocatedQuantity:0,availableQuantity:3}],total:1,totals:{soldQuantity:3,allocatedQuantity:0,availableQuantity:3}} as never);
 render(<InventoryPanel canAllocate canReadProducts canReadCustomers={false} canReadAllocations={false}/>);
 await screen.findByText('Acme · Suite');
 expect(screen.queryByRole('button',{name:'Gestisci assegnazioni'})).toBeNull();
 expect(vi.mocked(api).mock.calls.some(([path])=>path.includes('/api/allocations'))).toBe(false);
});
test('cambio vendita nasconde immediatamente assegnazioni precedenti anche se la richiesta fallisce',async()=>{
 let rejectSecond:(reason:unknown)=>void=()=>{};
 vi.mocked(api).mockImplementation(async(path)=>path.includes('allocations?saleId=1')?{items:[{id:77,installedName:'Installazione precedente',quantity:1}],total:1}:path.includes('allocations?saleId=2')?await new Promise((_,reject)=>{rejectSecond=reject;}):{items:[{id:1,customerId:1,catalogProductId:2,customerName:'Acme',productName:'Prima',quantity:3,allocatedQuantity:1,availableQuantity:2},{id:2,customerId:1,catalogProductId:2,customerName:'Acme',productName:'Seconda',quantity:3,allocatedQuantity:0,availableQuantity:3}],total:2,totals:{soldQuantity:6,allocatedQuantity:1,availableQuantity:5}} as never);
 render(<InventoryPanel canAllocate canReadCustomers={false}/>);
 fireEvent.click((await screen.findAllByRole('button',{name:'Gestisci assegnazioni'}))[0]);
 await screen.findByText('Installazione precedente');
 fireEvent.click(screen.getAllByRole('button',{name:'Gestisci assegnazioni'})[1]);
 expect(screen.queryByText('Installazione precedente')).toBeNull();
 rejectSecond(new Error('Errore seconda vendita'));
 await screen.findByRole('alert');
 expect(screen.queryByRole('button',{name:'Libera assegnazione'})).toBeNull();
});
test('precompila il prezzo dal catalogo mantenendolo modificabile',async()=>{
 vi.mocked(api).mockImplementation(async(path)=>path.includes('/customers')?{items:[{id:1,name:'Acme'}],total:1}:path.includes('catalog-products')?{items:[{id:2,name:'Suite',price:'99.90'}],total:1}:path.includes('summary')?{totals:{soldQuantity:0,allocatedQuantity:0,availableQuantity:0,totalAmount:0}}:{items:[],total:0} as never);
 render(<SalesPanel canWrite/>);fireEvent.click(screen.getByRole('button',{name:'Nuova vendita'}));await screen.findByRole('option',{name:'Suite'});fireEvent.change(screen.getByLabelText('Prodotto catalogo',{selector:'select'}),{target:{value:'2'}});expect((screen.getByLabelText('Prezzo unitario netto EUR') as HTMLInputElement).value).toBe('99.90');fireEvent.change(screen.getByLabelText('Prezzo unitario netto EUR'),{target:{value:'95.00'}});expect((screen.getByLabelText('Prezzo unitario netto EUR') as HTMLInputElement).value).toBe('95.00');
});

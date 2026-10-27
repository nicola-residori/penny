// @vitest-environment jsdom
import React from 'react';
import {it,expect,vi,afterEach} from 'vitest';
import {render,screen,cleanup,waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CatalogPanel from './CatalogPanel';
const item={id:7,code:'PEN-01',name:'Penny Enterprise',category:'SOFTWARE',origin:'INTERNAL',manufacturer:null,model:null,description:null,active:true};
afterEach(()=>{cleanup();vi.unstubAllGlobals();});
it('archives an entry and retains the product record',async()=>{
 const writes:unknown[]=[];
 vi.stubGlobal('fetch',vi.fn(async(url:string,o?:RequestInit)=>{
  if(o?.method==='PUT')writes.push(JSON.parse(o.body as string));
  return {ok:true,json:async()=>url.includes('/csrf')?{token:'test'}:url==='/api/catalog-products/7'?item:{items:[item],total:1}};
 }));
 const user=userEvent.setup();render(<CatalogPanel canWrite/>);
 await user.click(await screen.findByRole('button',{name:/PEN-01/}));await user.click(screen.getByRole('button',{name:'Modifica'}));
 await user.click(screen.getByLabelText('Attivo'));await user.click(screen.getByRole('button',{name:'Salva'}));
 await waitFor(()=>expect(writes).toContainEqual(expect.objectContaining({active:false,code:'PEN-01'})));
});
it('does not show editing controls to read-only users',async()=>{
 vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>({items:[item],total:1})})));
 const user=userEvent.setup();render(<CatalogPanel canWrite={false}/>);
 await user.click(await screen.findByRole('button',{name:/PEN-01/}));
 expect(screen.queryByRole('button',{name:'Modifica'})).toBeNull();expect(screen.queryByRole('button',{name:'+ Nuovo prodotto'})).toBeNull();
});
it('requires both module write and trash delete capabilities',async()=>{
 vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>({items:[item],total:1})})));
 const view=render(<CatalogPanel canWrite/>);await userEvent.click(await screen.findByRole('button',{name:/PEN-01/}));expect(screen.queryByRole('button',{name:'Elimina'})).toBeNull();view.rerender(<CatalogPanel canWrite={false} canDeleteRecords/>);expect(screen.queryByRole('button',{name:'Elimina'})).toBeNull();view.rerender(<CatalogPanel canWrite canDeleteRecords/>);expect(screen.getByRole('button',{name:'Elimina'})).toBeTruthy();
});
it('returns to the first page after deleting the last item of the final page',async()=>{
 let removed=false;const urls:string[]=[];vi.spyOn(window,'confirm').mockReturnValue(true);
 vi.stubGlobal('fetch',vi.fn(async(url:string,o?:RequestInit)=>{urls.push(url);if(o?.method==='DELETE')removed=true;return {ok:true,json:async()=>url.includes('/csrf')?{token:'x'}:o?.method==='DELETE'?{trashed:true}:{items:Array.from({length:removed?20:21},(_,i)=>i===20?{...item,id:21,code:'LAST',name:'ZZ Last item'}:{...item,id:i+1,name:i===0?item.name:'Penny '+String(i).padStart(2,'0')}),total:removed?20:21}};}));
 render(<CatalogPanel canWrite canDeleteRecords/>);await screen.findByRole('button',{name:/Penny Enterprise/});await userEvent.click(screen.getByRole('button',{name:'Successiva'}));await userEvent.click(await screen.findByRole('button',{name:/LAST/}));await userEvent.click(screen.getByRole('button',{name:'Elimina'}));await waitFor(()=>expect(screen.getByRole('button',{name:/Penny Enterprise/})).toBeTruthy());expect(urls.filter(url=>url.startsWith('/api/catalog-products?')).at(-1)).toContain('page=0');
});
it('edits optional supplier and EUR price and offers training',async()=>{
 const writes:Record<string,unknown>[]=[];
 vi.stubGlobal('fetch',vi.fn(async(url:string,o?:RequestInit)=>{if(o?.method==='PUT')writes.push(JSON.parse(o.body as string));return {ok:true,json:async()=>url.includes('/csrf')?{token:'x'}:{items:[{...item,supplier:'Distributore',price:125.50}],total:1}};}));
 const user=userEvent.setup();render(<CatalogPanel canWrite/>);
 await user.click(await screen.findByRole('button',{name:/PEN-01/}));
 expect(screen.getByText('125,50 €')).toBeTruthy();expect(screen.getAllByText('Distributore').length).toBeGreaterThan(0);
 await user.click(screen.getByRole('button',{name:'Modifica'}));
 expect(screen.getByLabelText('Altro fornitore')).toHaveProperty('value','Distributore');
 await user.selectOptions(screen.getByLabelText('Categoria'),'TRAINING');
 await user.clear(screen.getByLabelText('Prezzo (EUR)'));await user.selectOptions(screen.getByLabelText('Fornitore'),'');
 await user.click(screen.getByRole('button',{name:'Salva'}));
 await waitFor(()=>expect(writes).toContainEqual(expect.objectContaining({category:'TRAINING',supplier:null,price:null})));
 expect(screen.queryByText('Prodotto nostro')).toBeNull();
});
it('saves taxonomy, unit and sales channel with the exact decimal price string',async()=>{
 const writes:Record<string,unknown>[]=[];
 vi.stubGlobal('fetch',vi.fn(async(url:string,o?:RequestInit)=>{if(o?.method==='POST'&&url==='/api/catalog-products')writes.push(JSON.parse(String(o.body)));return {ok:true,json:async()=>url.includes('/csrf')?{token:'x'}:url.startsWith('/api/product-categories')?{items:[{id:2,name:'Software',legacyCategory:'SOFTWARE',operationalKind:'SOFTWARE',active:true}]}:url.startsWith('/api/product-types')?{items:[{id:3,categoryId:2,name:'Autodesk',obsolete:false}]}:o?.method==='POST'?{id:8}:{items:[],total:0}};}));
 render(<CatalogPanel canWrite/>);const user=userEvent.setup();await user.click(screen.getByRole('button',{name:'+ Nuovo prodotto'}));await user.type(screen.getByLabelText('Codice *'),'SW');await user.type(screen.getByLabelText('Nome *'),'Licenza');await screen.findByRole('option',{name:'Software'});await user.selectOptions(screen.getByLabelText('Categoria'),'2');await user.selectOptions(screen.getByLabelText('Tipologia prodotto'),'3');await user.selectOptions(screen.getByLabelText('Canale di vendita'),'EXTERNAL_SUPPLY');await user.type(screen.getByLabelText('Unità di misura'),'licenza');await user.type(screen.getByLabelText('Prezzo (EUR)'),'123456789.99');await user.click(screen.getByRole('button',{name:'Salva'}));await waitFor(()=>expect(writes).toContainEqual(expect.objectContaining({categoryId:2,productTypeId:3,unit:'licenza',supplyChannel:'EXTERNAL_SUPPLY',price:'123456789.99',origin:'INTERNAL'})));
});

it('uses sales channel without exposing the legacy origin in the catalog',async()=>{
 vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>({items:[item],total:1})})));
 render(<CatalogPanel canWrite/>);await userEvent.click(await screen.findByRole('button',{name:/PEN-01/}));expect(screen.queryByText('Origine')).toBeNull();expect(screen.queryByLabelText('Filtra origine')).toBeNull();await userEvent.click(screen.getByRole('button',{name:'Modifica'}));expect(screen.queryByLabelText('Origine')).toBeNull();expect(screen.getByLabelText('Canale di vendita')).toBeTruthy();
});

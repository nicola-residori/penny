// @vitest-environment jsdom
import React from 'react';
import {afterEach,beforeEach,describe,it,expect,vi} from 'vitest';
import {cleanup,render,screen,waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ProductsPanel from './ProductsPanel';

const entry={id:7,code:'PEN-01',name:'Penny Enterprise',category:'SOFTWARE',origin:'INTERNAL',manufacturer:'Penny',model:'Enterprise',active:true};
let installations:unknown[]=[];
let calls:{url:string;body:Record<string,unknown>}[]=[];
beforeEach(()=>{
 installations=[];calls=[];
 vi.stubGlobal('fetch',vi.fn(async (url:string,options?:RequestInit)=>{
  if(options?.body) calls.push({url,body:JSON.parse(options.body as string)});
  const data=url.includes('/csrf')?{token:'test'}:url.startsWith('/api/customers')?{items:[{id:1,name:'Cliente'}],total:1}:url.startsWith('/api/catalog-products')?{items:[entry],total:1}:options?.method==='POST'?{id:1}:url==='/api/installed-products/1'?{id:1,customerId:1,category:'SOFTWARE',name:'Penny Enterprise',origin:'INTERNAL',catalogProductId:7,status:'ACTIVE'}:{items:installations,total:installations.length};
  return {ok:true,json:async()=>data};
 }));
});
afterEach(()=>{cleanup();vi.unstubAllGlobals();});
describe('catalog selection in installed products',()=>{
 it('requires a catalog item for INTERNAL and prefills its snapshot',async()=>{
  const user=userEvent.setup();render(<ProductsPanel canWrite canReadCatalog/>);
  await screen.findByRole('option',{name:'Cliente'});await user.click(screen.getByRole('button',{name:'+ Nuovo prodotto'}));
  await user.selectOptions(screen.getByLabelText('Origine'), 'INTERNAL');
  const selector=screen.getByLabelText(/Prodotto da catalogo/);
  expect(selector.hasAttribute('required')).toBe(true);
  await user.selectOptions(selector,'7');
  expect((screen.getByLabelText('Nome *') as HTMLInputElement).value).toBe('Penny Enterprise');
  await user.click(screen.getByRole('button',{name:'Salva'}));
  await waitFor(()=>expect(calls.some(c=>c.body.origin==='INTERNAL'&&c.body.catalogProductId===7&&c.body.category==='SOFTWARE')).toBe(true));
 });
 it('keeps catalog options when starting another new form',async()=>{
  const user=userEvent.setup();render(<ProductsPanel canWrite canReadCatalog/>);
  await screen.findByRole('option',{name:'Cliente'});await user.click(screen.getByRole('button',{name:'+ Nuovo prodotto'}));
  await screen.findByRole('option',{name:'PEN-01 · Penny Enterprise'});
  await user.click(screen.getByRole('button',{name:'+ Nuovo prodotto'}));
  expect(screen.getByRole('option',{name:'PEN-01 · Penny Enterprise'})).toBeTruthy();
 });
 it('allows an unlinked third-party installation',async()=>{
  const user=userEvent.setup();render(<ProductsPanel canWrite canReadCatalog/>);
  await screen.findByRole('option',{name:'Cliente'});await user.click(screen.getByRole('button',{name:'+ Nuovo prodotto'}));
  expect((screen.getByLabelText('Origine') as HTMLSelectElement).value).toBe('THIRD_PARTY');
  expect(screen.getByLabelText(/Prodotto da catalogo/).hasAttribute('required')).toBe(false);
  await user.type(screen.getByLabelText('Nome *'),'Prodotto esterno');await user.click(screen.getByRole('button',{name:'Salva'}));
  await waitFor(()=>expect(calls.some(c=>c.body.origin==='THIRD_PARTY'&&c.body.catalogProductId===null)).toBe(true));
 });
 it('preserves an archived link without catalog permission',async()=>{
  installations=[{id:1,customerId:1,category:'SOFTWARE',name:'Archivio',origin:'INTERNAL',catalogProductId:7,status:'ACTIVE'}];
  const user=userEvent.setup();render(<ProductsPanel canWrite canReadCatalog={false}/>);
  await user.click(await screen.findByRole('button',{name:/Archivio/}));await user.click(screen.getByRole('button',{name:'Modifica'}));
  await user.click(screen.getByRole('button',{name:'Salva'}));
  await waitFor(()=>expect(calls.some(c=>c.body.catalogProductId===7)).toBe(true));
  expect(vi.mocked(fetch).mock.calls.some(c=>String(c[0]).startsWith('/api/catalog-products'))).toBe(false);
 });
});

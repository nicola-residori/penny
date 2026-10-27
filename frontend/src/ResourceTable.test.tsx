// @vitest-environment jsdom
import React from 'react';
import {it,expect,vi,afterEach} from 'vitest';
import {render,screen,cleanup,waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ResourceTable from './ResourceTable';
import {xlsxBytes} from './xlsxExport';
afterEach(()=>{cleanup();vi.unstubAllGlobals();});
it('filters and exports across all server pages rather than just the first page',async()=>{
 const data=Array.from({length:102},(_,i)=>({id:i+1,name:'Azienda '+i,province:i===101?'VR':'MI'}));const fetcher=vi.fn(async(path)=>{const q=new URL(String(path),'http://local').searchParams;const page=Number(q.get('page'));return {ok:true,json:async()=>({items:data.slice(page*100,(page+1)*100),total:102})};});vi.stubGlobal('fetch',fetcher);
 render(<ResourceTable title="Clienti" path="/api/customers" columns={[{key:'name',label:'Nome',value:(r:typeof data[0])=>r.name},{key:'province',label:'Provincia',value:(r:typeof data[0])=>r.province}]} onOpen={()=>{}}/>);
 await screen.findByText('102 risultati');await userEvent.type(screen.getByLabelText('Filtra Provincia'),'VR');expect(await screen.findByRole('button',{name:'Azienda 101'})).toBeTruthy();expect(screen.getByText('1 risultato')).toBeTruthy();expect(fetcher).toHaveBeenCalledTimes(2);
});
it('creates an actual XLSX ZIP workbook and keeps formula-like text literal',()=>{const bytes=xlsxBytes(['Nome','Quantità'],[['=1+1',2]]);expect(Array.from(bytes.slice(0,4))).toEqual([80,75,3,4]);const text=new TextDecoder().decode(bytes);expect(text).toContain('xl/worksheets/sheet1.xml');expect(text).toContain('t="inlineStr"');expect(text).toContain('=1+1');expect(text).not.toContain('<f>');});

it('disables export if a later server page fails',async()=>{vi.stubGlobal('fetch',vi.fn(async(path)=>{if(new URL(String(path),'http://local').searchParams.get('page')==='1')throw new Error('Pagina non disponibile');return {ok:true,json:async()=>({items:Array.from({length:100},(_,id)=>({id,name:'Riga '+id})),total:101})};}));render(<ResourceTable title="Clienti" path="/api/customers" columns={[{key:'name',label:'Nome',value:(r:{id:number;name:string})=>r.name}]} onOpen={()=>{}}/>);await screen.findByRole('alert');expect(screen.getByRole('button',{name:'Esporta Excel'}).getAttribute('disabled')).not.toBeNull();});
it('rejects an incomplete dataset rather than exporting partial results',async()=>{vi.stubGlobal('fetch',vi.fn(async()=>({ok:true,json:async()=>({items:[{id:1,name:'A'}],total:20})})));render(<ResourceTable title="Clienti" path="/api/customers" columns={[{key:'name',label:'Nome',value:(r:{id:number;name:string})=>r.name}]} onOpen={()=>{}}/>);expect(await screen.findByRole('alert')).toBeTruthy();expect(screen.getByRole('button',{name:'Esporta Excel'}).hasAttribute('disabled')).toBe(true);});

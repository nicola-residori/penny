// @vitest-environment jsdom
import React from 'react';
import {afterEach,it,expect,vi} from 'vitest';
import {render,screen,cleanup,waitFor,fireEvent} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import EntityImageEditor,{EntityAvatar} from './EntityImage';
afterEach(()=>{cleanup();vi.unstubAllGlobals();});
it('uploads a customer logo with CSRF and reports saved metadata',async()=>{
 const fetcher=vi.fn(async(url,options)=>({ok:true,json:async()=>String(url).includes('/csrf')?{token:'csrf'}:{hasImage:true,hasOwnImage:true}}));vi.stubGlobal('fetch',fetcher);const changed=vi.fn();
 render(<EntityImageEditor kind="customers" id={4} label="Logo Alfa" hasImage={false} canWrite onChanged={changed}/>);
 await userEvent.upload(screen.getByLabelText('Carica immagine'),new File(['png'],'logo.png',{type:'image/png'}));
 await waitFor(()=>expect(changed).toHaveBeenCalledWith({hasImage:true,hasOwnImage:true}));
 const call=fetcher.mock.calls.find(c=>String(c[0])==='/api/entity-images/customers/4');expect(call?.[1]).toMatchObject({method:'POST',credentials:'same-origin',headers:{'X-XSRF-TOKEN':'csrf'}});expect(call?.[1]?.body).toBeInstanceOf(FormData);
});
it('keeps an inherited catalog image when removing an installation override',async()=>{
 vi.stubGlobal('fetch',vi.fn(async(url)=>({ok:true,json:async()=>String(url).includes('/csrf')?{token:'csrf'}:{hasImage:true,hasOwnImage:false}})));const changed=vi.fn();
 render(<EntityImageEditor kind="installed-products" id={8} label="Server" hasImage hasOwnImage canWrite onChanged={changed}/>);
 await userEvent.click(screen.getByRole('button',{name:'Rimuovi immagine personalizzata'}));
 await waitFor(()=>expect(changed).toHaveBeenCalledWith({hasImage:true,hasOwnImage:false}));
});
it('hides mutations for readers and falls back to initials after an image error',()=>{
 render(<><EntityImageEditor kind="customers" id={4} label="Logo Alfa" hasImage canWrite={false} onChanged={()=>{}}/><EntityAvatar kind="customers" id={4} name="Alfa Beta" hasImage/></>);
 expect(screen.queryByLabelText('Sostituisci immagine')).toBeNull();expect(screen.queryByRole('button',{name:'Rimuovi immagine'})).toBeNull();
 const image=document.querySelector('.entity-avatar img')!;fireEvent.error(image);expect(document.querySelector('.entity-avatar')?.textContent).toBe('AB');
});
it('rejects oversized images before sending a request',async()=>{
 const fetcher=vi.fn();vi.stubGlobal('fetch',fetcher);render(<EntityImageEditor kind="customers" id={1} label="Logo" hasImage={false} canWrite onChanged={()=>{}}/>);
 await userEvent.upload(screen.getByLabelText('Carica immagine'),new File([new Uint8Array(5242881)],'large.png',{type:'image/png'}));expect(screen.getByRole('alert')).toBeTruthy();expect(fetcher).not.toHaveBeenCalled();
});

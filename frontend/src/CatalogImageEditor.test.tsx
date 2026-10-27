// @vitest-environment jsdom
import React from 'react';
import {it,expect,vi,afterEach} from 'vitest';
import {render,screen,cleanup,waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CatalogImageEditor from './CatalogImageEditor';
afterEach(()=>{cleanup();vi.unstubAllGlobals();});
it('uploads multipart with CSRF without overriding its content type',async()=>{
 const changed=vi.fn(),fetcher=vi.fn(async(url:string,o?:RequestInit)=>({ok:true,json:async()=>({token:'csrf'})}));vi.stubGlobal('fetch',fetcher);render(<CatalogImageEditor productId={8} hasImage={false} canWrite onChanged={changed}/>);await userEvent.upload(screen.getByLabelText(/Carica immagine/),new File(['image'],'photo.png',{type:'image/png'}));await waitFor(()=>expect(changed).toHaveBeenCalledWith(true));const upload=fetcher.mock.calls.find(c=>c[0]==='/api/catalog-products/8/image')?.[1];expect(upload?.body).toBeInstanceOf(FormData);expect(upload?.headers).toEqual({'X-XSRF-TOKEN':'csrf'});expect(fetcher.mock.calls.some(c=>c[0]==='/api/catalog-products')).toBe(false);
});
it('offers no upload or removal to read-only viewers',()=>{render(<CatalogImageEditor productId={8} hasImage canWrite={false} onChanged={()=>{}}/>);expect(screen.getByRole('img')).toBeTruthy();expect(screen.queryByLabelText(/Carica immagine/)).toBeNull();expect(screen.queryByRole('button')).toBeNull();});

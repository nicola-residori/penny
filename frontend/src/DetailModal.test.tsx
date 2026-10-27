// @vitest-environment jsdom
import React,{useState} from 'react';
import {it,expect,afterEach} from 'vitest';
import {render,screen,cleanup,fireEvent} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import DetailModal from './DetailModal';
afterEach(cleanup);
function Example(){const [open,setOpen]=useState(false);return <><button onClick={()=>setOpen(true)}>Apri scheda</button><p>Elenco preservato</p><DetailModal open={open} title="Dettaglio" onClose={()=>setOpen(false)}><input aria-label="Nome"/></DetailModal></>;}
it('opens an overlay, retains its background, closes with Escape and restores focus',async()=>{render(<Example/>);await userEvent.click(screen.getByRole('button',{name:'Apri scheda'}));expect(screen.getByRole('dialog',{name:'Dettaglio'})).toBeTruthy();expect(screen.getByText('Elenco preservato')).toBeTruthy();expect(document.body.style.overflow).toBe('hidden');fireEvent.keyDown(screen.getByRole('dialog'),{key:'Escape'});expect(screen.queryByRole('dialog')).toBeNull();expect(document.activeElement).toBe(screen.getByRole('button',{name:'Apri scheda'}));expect(document.body.style.overflow).toBe('');});
it('contains keyboard focus and closes from its accessible button',async()=>{render(<Example/>);await userEvent.click(screen.getByRole('button',{name:'Apri scheda'}));const input=screen.getByLabelText('Nome');input.focus();fireEvent.keyDown(input,{key:'Tab'});expect(document.activeElement).toBe(screen.getByRole('button',{name:'Chiudi Dettaglio'}));await userEvent.click(screen.getByRole('button',{name:'Chiudi Dettaglio'}));expect(screen.queryByRole('dialog')).toBeNull();});

// @vitest-environment jsdom
import React from 'react';
import {it,expect,vi,afterEach} from 'vitest';
import {render,screen,cleanup} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Tooltip from './Tooltip';
afterEach(cleanup);
it('dismisses keyboard tooltip with Escape without losing focus or overriding child keys',async()=>{
 const key=vi.fn();const user=userEvent.setup();render(<><Tooltip text="Apri relazioni"><button onKeyDown={key}>Mappa</button></Tooltip><button>Altro</button></>);const button=screen.getByRole('button',{name:'Mappa'});
 await user.tab();const tip=screen.getByRole('tooltip');expect(getComputedStyle(tip).visibility).toBe('visible');await user.keyboard('{Escape}');expect(getComputedStyle(tip).visibility).toBe('hidden');expect(document.activeElement).toBe(button);expect(key).toHaveBeenCalled();await user.tab();await user.tab({shift:true});expect(getComputedStyle(tip).visibility).toBe('visible');
});

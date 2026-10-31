// @vitest-environment jsdom
import {beforeEach,afterEach,describe,it,expect,vi} from 'vitest';
import {initializeTheme,setThemePreference,getThemePreference} from './theme';
let dark=false,change:(()=>void)|undefined,cleanup:()=>void;
beforeEach(()=>{localStorage.clear();dark=false;vi.stubGlobal('matchMedia',vi.fn(()=>({get matches(){return dark},addEventListener:(_type:string,fn:()=>void)=>{change=fn},removeEventListener:vi.fn()})));});
afterEach(()=>{cleanup?.();vi.unstubAllGlobals();});
describe('appearance preferences',()=>{
 it('defaults to system and follows live system changes',()=>{cleanup=initializeTheme();expect(getThemePreference()).toBe('system');expect(document.documentElement.dataset.theme).toBe('light');dark=true;change?.();expect(document.documentElement.dataset.theme).toBe('dark');});
 it('persists explicit light even when the system turns dark',()=>{cleanup=initializeTheme();setThemePreference('light');dark=true;change?.();expect(localStorage.getItem('penny.appearance')).toBe('light');expect(document.documentElement.dataset.theme).toBe('light');});
 it('restores dark and can return to system',()=>{localStorage.setItem('penny.appearance','dark');cleanup=initializeTheme();expect(getThemePreference()).toBe('dark');setThemePreference('system');expect(document.documentElement.dataset.theme).toBe('light');});
 it('ignores corrupt preferences and syncs changes from another tab',()=>{localStorage.setItem('penny.appearance','broken');cleanup=initializeTheme();expect(getThemePreference()).toBe('system');window.dispatchEvent(new StorageEvent('storage',{key:'penny.appearance',newValue:'dark'}));expect(getThemePreference()).toBe('dark');expect(document.documentElement.dataset.theme).toBe('dark');});
 it('works when browser storage is unavailable',()=>{vi.spyOn(Storage.prototype,'getItem').mockImplementation(()=>{throw new Error('denied')});vi.spyOn(Storage.prototype,'setItem').mockImplementation(()=>{throw new Error('denied')});cleanup=initializeTheme();expect(()=>setThemePreference('dark')).not.toThrow();expect(document.documentElement.dataset.theme).toBe('dark');vi.restoreAllMocks();});
});

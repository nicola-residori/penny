import {useEffect,useId,useState} from 'react';
import {getThemePreference,setThemePreference,themeChangeEvent,type ThemePreference} from './theme';
export default function ThemeSelector() {
 const id=useId(),[value,setValue]=useState(getThemePreference);
 useEffect(()=>{const update=()=>setValue(getThemePreference());window.addEventListener(themeChangeEvent,update);return()=>window.removeEventListener(themeChangeEvent,update);},[]);
 return <label className="penny-theme-selector" htmlFor={id}><span>Aspetto</span><select aria-label="Aspetto" id={id} value={value} onChange={e=>setThemePreference(e.target.value as ThemePreference)}><option value="system">Sistema</option><option value="light">Chiaro</option><option value="dark">Scuro</option></select></label>;
}

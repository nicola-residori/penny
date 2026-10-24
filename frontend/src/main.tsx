import React from 'react';
import { createRoot } from 'react-dom/client';
import './style.css';
function App() { return <main><h1>Penny</h1><p>Il CRM aziendale</p><p>Milestone M0-01 — Ambiente di sviluppo</p></main>; }
createRoot(document.getElementById('root')!).render(<React.StrictMode><App /></React.StrictMode>);

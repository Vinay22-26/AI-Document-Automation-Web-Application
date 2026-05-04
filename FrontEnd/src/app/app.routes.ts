import { Routes } from '@angular/router';

import { Register } from './components/register/register';
import { NavbarComponent } from './navbar/navbar.component';



import { Files } from './components/files/files';
import { Extract } from './components/extract/extract';
import { Chat } from './components/chat/chat';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'Login',
  },

  {
    path: 'register',
    component: Register,
  },
  {
    path: 'Login',
    loadComponent: () => import('./components/login/login').then((m) => m.Login),
  },
  {
    path: 'navbar',
    component: NavbarComponent,
    children: [

      {
        path: 'Extract',
        component:Extract
      },
      {
        path: 'Validate',
        loadComponent: () => import('./components/validate/validate').then((m) => m.Validate),
      },
      {
        path: 'Export',
        loadComponent: () => import('./components/export/export').then((m) => m.Export),
      },
      {
        path: 'Audit',
        loadComponent: () => import('./components/audit/audit').then((m) => m.Audit),
      },
      {
        path: 'files',
        component:Files
      },
      {
        path: 'settings',
        loadComponent: () => import('./components/settings/settings').then((m) => m.Settings),
      },
      {
        path: 'profile',
        loadComponent: () => import('./components/profile/profile').then((m) => m.Profile),
      },
      {
        path: 'chat',
        component:Chat
      }
    ],
  },
  {
    path: '**',
    redirectTo: 'Login',
  },
];

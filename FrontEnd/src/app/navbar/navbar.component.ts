import { Component, inject } from '@angular/core';
import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { AsyncPipe, NgIf } from '@angular/common';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { Router, RouterModule } from '@angular/router';
import { Observable } from 'rxjs';
import { map, shareReplay } from 'rxjs/operators';
import { MatDividerModule } from '@angular/material/divider';

@Component({
  selector: 'app-navbar',
  standalone: true,
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.scss',
  imports: [
    MatToolbarModule,
    MatButtonModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatMenuModule,
    MatDividerModule,
    RouterModule,
    AsyncPipe,
    NgIf
  ]
})
export class NavbarComponent {

  private breakpointObserver = inject(BreakpointObserver);
  private router = inject(Router);

  isHandset$: Observable<boolean> =
    this.breakpointObserver.observe(Breakpoints.Handset)
      .pipe(map(result => result.matches), shareReplay());

  get isLoggedIn(): boolean {
    return !!localStorage.getItem('LoggedInUser');
  }

  get isAdmin(): boolean {
    const role = localStorage.getItem('role');
    return role ? role.toLowerCase() === 'admin' : false;
  }

  get userEmail(): string | null {
    return localStorage.getItem('LoggedInUser');
  }

  logout() {
    localStorage.removeItem('token');
    localStorage.removeItem('LoggedInUser');
    localStorage.removeItem('role');
    this.router.navigate(['/Login']);
  }
}

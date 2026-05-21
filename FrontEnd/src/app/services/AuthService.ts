import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  // Read the initial role value directly from localStorage when the app starts up
  private roleSubject = new BehaviorSubject<string | null>(localStorage.getItem('role'));

  // Expose the role as an observable so components can listen to changes dynamically
  role$ = this.roleSubject.asObservable();

  // Call this method during login to dispatch the new state globally
  setRole(role: string) {
    localStorage.setItem('role', role);
    this.roleSubject.next(role);
  }

  // Call this method during logout to reset state
  clearRole() {
    localStorage.removeItem('role');
    this.roleSubject.next(null);
  }

  getRoleSync(): string | null {
    return this.roleSubject.value;
  }
}

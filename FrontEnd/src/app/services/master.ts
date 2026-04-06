import { Injectable } from '@angular/core';
import { RegisterType } from '../components/model/Register.type';
import { HttpClient } from '@angular/common/http';

@Injectable({
  providedIn: 'root',
})
export class Master {

  constructor(private http: HttpClient) {}

  RegistrationApi(Registertype: RegisterType) {
  return this.http.post(
    "http://localhost:8080/api/register",
    Registertype,
    { responseType: 'text' }
  );
}
  LoginApi(credentials: { email: string; password: string }) {
    return this.http.post("http://localhost:8080/api/login", credentials);
  }
  getRole() {
  return localStorage.getItem('role'); 
}

isLoggedIn() {
  return !!localStorage.getItem('token');
}




}

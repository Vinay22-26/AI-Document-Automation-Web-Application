import { Component, inject } from '@angular/core';
import { Master } from '../../services/master';
import { Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, RouterModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  constructor(private router: Router) {}

  masterServ = inject(Master);

  credentials = {
    email: '',
    password: ''
  };

  onSubmit() {
    this.masterServ.LoginApi(this.credentials).subscribe({
      next: (res: any) => {
        localStorage.setItem("token", res.token);
        localStorage.setItem("role", res.role);
        localStorage.setItem("LoggedInUser", res.email);
        this.router.navigate(['/navbar/files']);
      },
      error: () => {
        alert("Invalid Email or Password");
      }
    });
  }
}

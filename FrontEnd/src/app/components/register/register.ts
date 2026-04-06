import { Component, inject, OnInit } from '@angular/core';

import { ReactiveFormsModule, FormGroup, FormBuilder, Validators } from '@angular/forms';

import { Router, RouterModule } from '@angular/router';
import { Master } from '../../services/master';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterModule],
  templateUrl: './register.html',
  styleUrl: './register.scss',
})
export class Register implements OnInit {
  constructor(private router: Router) {}

  masterServ = inject(Master);

  private fb = inject(FormBuilder);

  registerForm!: FormGroup;

  initializeForm() {
    this.registerForm = this.fb.group({
      userName: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      gender: ['', Validators.required],
      phoneNumber: ['', [Validators.required, Validators.pattern('^[0-9]{10}$')]],
    });
  }

  registerApi() {

  if (this.registerForm.invalid) {
    alert('Please fill all the required fields correctly');
    return;
  }

  const mem = this.registerForm.value;

  this.masterServ.RegistrationApi(mem).subscribe({
    next: (res) => {
      console.log('Registration Successful');
      this.router.navigate(['/login']);
    },
    error: (err) => {
      if (err.status === 409) {
        alert('Email already exists.');
      } else {
        alert('An error occurred during registration.');
      }
    },
  });
}

  ngOnInit(): void {
    this.initializeForm();
  }


  login(){
    this.router.navigateByUrl('/Login');
  }
}

import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ClipboardModule, Clipboard } from '@angular/cdk/clipboard';
import {  MatSnackBarModule } from '@angular/material/snack-bar';

@Component({
  selector: 'app-export-step',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule, ClipboardModule, MatSnackBarModule],
  templateUrl: './export.html',
  styleUrl: './export.scss'
})
export class Export {

}

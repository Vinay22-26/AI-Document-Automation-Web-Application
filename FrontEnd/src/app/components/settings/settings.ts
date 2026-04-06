import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, MatCardModule, MatSlideToggleModule, MatIconModule],
  templateUrl: './settings.html',
  styleUrl: './settings.scss'
})
export class Settings implements OnInit {
  showDeleteConfirmation = true;

  ngOnInit(): void {
    const skipConfirm = localStorage.getItem("skipDeleteConfirm");

    if (skipConfirm === null) {
      this.showDeleteConfirmation = true;
    } else {
      this.showDeleteConfirmation = skipConfirm === 'false';
    }
  }

  onToggleChange(enabled: boolean): void {
    const skipValue = !enabled;
    localStorage.setItem("skipDeleteConfirm", skipValue.toString());
  }
}

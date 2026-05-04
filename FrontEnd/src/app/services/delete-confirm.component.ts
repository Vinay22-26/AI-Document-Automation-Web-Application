import { Component, Inject } from '@angular/core';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-delete-confirm',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatCheckboxModule, MatButtonModule, MatIconModule, FormsModule],
  template: `
    <div class="delete-dialog-container">
      <div class="dialog-header">
        <mat-icon [style.color]="data.confirmColor || '#ff5252'">{{ data.icon || 'warning' }}</mat-icon>
        <h2 mat-dialog-title>{{ data.title || 'Confirm Action' }}</h2>
      </div>

      <mat-dialog-content>
        <p class="message-text">{{ data.message || 'Are you sure you want to proceed?' }}</p>
        <div class="checkbox-wrapper" *ngIf="data.showCheckbox">
          <mat-checkbox [(ngModel)]="skipNextTime" color="primary">
            Don't show this message again
          </mat-checkbox>
        </div>
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button mat-button class="cancel-btn" (click)="onCancel()">Cancel</button>
        <button
          mat-flat-button
          [style.background-color]="data.confirmColor || '#ff5252'"
          class="confirm-btn"
          (click)="onConfirm()">
          {{ data.btnText || 'Confirm' }}
        </button>
      </mat-dialog-actions>
    </div>
  `,
  styles: [`
    .delete-dialog-container {
      background: #1e1e26;
      color: #e0e0e0;
      padding: 10px;
      border-radius: 8px;
    }
    .dialog-header {
      display: flex;
      align-items: center;
      gap: 12px;
      padding-bottom: 8px;
    }
    h2 {
      margin: 0 !important;
      font-size: 1.4rem;
      color: #ffffff;
    }
    .message-text {
      font-size: 1rem;
      color: #b0b0b0;
      margin-bottom: 20px;
      line-height: 1.5;
    }
    .checkbox-wrapper {
      margin-top: 15px;
      padding: 10px;
      background: #2a2a35;
      border-radius: 6px;
    }
    ::ng-deep .mat-mdc-checkbox label {
      color: #b0b0b0 !important;
      font-size: 0.9rem;
    }
    .mat-dialog-actions {
      padding: 15px 0 5px 0;
    }
    .cancel-btn {
      color: #b0b0b0 !important;
    }
    .confirm-btn {
      color: white !important;
      border-radius: 6px;
      padding: 0 20px;
    }
    mat-dialog-content {
      overflow: hidden !important;
      max-height: none !important;
    }
  `]
})
export class DeleteConfirmDialog {
  skipNextTime = false;

  constructor(
    public dialogRef: MatDialogRef<DeleteConfirmDialog>,
    @Inject(MAT_DIALOG_DATA) public data: any
  ) {}

  onCancel(): void {
    this.dialogRef.close(null);
  }

  onConfirm(): void {
    this.dialogRef.close({ confirm: true, skip: this.skipNextTime });
  }
}

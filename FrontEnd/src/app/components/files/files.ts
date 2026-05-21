import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { CommonModule } from '@angular/common';
import { forkJoin } from 'rxjs';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { DeleteConfirmDialog } from '../../services/delete-confirm.component';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-files',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatSnackBarModule,
    MatDialogModule,
    MatProgressSpinnerModule,
    MatFormFieldModule,
    MatInputModule
  ],
  templateUrl: './files.html',
  styleUrl: './files.scss'
})
export class Files implements OnInit {
  private http = inject(HttpClient);
  private cdr = inject(ChangeDetectorRef);
  private snackBar = inject(MatSnackBar);
  private dialog = inject(MatDialog);

  displayedColumns: string[] = ['name', 'preview', 'status', 'action'];
  files: any[] = [];
  dataSource = new MatTableDataSource<any>([]);
  isDragging = false;
  isProcessing = false;
  selectedFiles: File[] = [];
  userRole: string | null = null;

  private apiUrl = 'http://localhost:8080/api/files/AllFiles';
  private adminUrl = 'http://localhost:8080/api/files/AdminFiles';
  private uploadUrl = 'http://localhost:8080/api/files/upload';
  private deleteUrl = 'http://localhost:8080/api/files/delete/';

  ngOnInit(): void {
    this.userRole = localStorage.getItem("role");
    this.setupPermissions();
    this.loadFiles();
  }

  setupPermissions() {
    if (this.userRole === 'ADMIN') {
      if (!this.displayedColumns.includes('user')) {
        this.displayedColumns.splice(1, 0, 'user');
      }
      if (!this.displayedColumns.includes('approval')) {
        this.displayedColumns.push('approval');
      }
    }
  }

  applyFilter(event: Event) {
    const filterValue = (event.target as HTMLInputElement).value;
    this.dataSource.filter = filterValue.trim().toLowerCase();
  }

  loadFiles() {
    const userEmail = localStorage.getItem("LoggedInUser");
    if (!userEmail) return;
    const finalUrl = (this.userRole === 'ADMIN') ? this.adminUrl : this.apiUrl;
    const urlWithParams = this.userRole === 'ADMIN' ? finalUrl : `${finalUrl}?email=${userEmail}`;

    this.http.get<any[]>(urlWithParams).subscribe({
      next: (res) => {
        this.files = res;
        this.dataSource.data = this.files;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.files = [];
        this.dataSource.data = this.files;
        this.cdr.detectChanges();
      }
    });
  }

  deleteFile(id: number) {
    const userEmail = localStorage.getItem("LoggedInUser");
    const skipConfirm = localStorage.getItem("skipDeleteConfirm") === 'true';
    if (!userEmail) return;

    if (skipConfirm) {
      this.executeDelete(id, userEmail);
    } else {
      const dialogRef = this.dialog.open(DeleteConfirmDialog, {
        data: {
          title: 'Confirm Deletion',
          message: 'Are you sure you want to delete this file?',
          icon: 'warning',
          confirmColor: '#ff5252',
          btnText: 'Delete File',
          showCheckbox: true
        }
      });

      dialogRef.afterClosed().subscribe(result => {
        if (result && result.confirm) {
          if (result.skip) localStorage.setItem("skipDeleteConfirm", 'true');
          this.executeDelete(id, userEmail);
        }
      });
    }
  }

  private executeDelete(id: number, userEmail: string) {
    this.http.delete(`${this.deleteUrl}${id}?email=${userEmail}`, { responseType: 'text' }).subscribe({
      next: () => {
        this.http.delete(`http://localhost:8080/api/extract/DeletingExtractedContent/${id}`).subscribe();
        this.files = this.files.filter(f => f.id !== id);
        this.dataSource.data = this.files;
        this.snackBar.open("File deleted successfully", "Close", { duration: 3000 });
        this.cdr.detectChanges();
      }
    });
  }

  approveFile(id: number) {
    const dialogRef = this.dialog.open(DeleteConfirmDialog, {
      data: {
        title: 'Confirm Approval',
        message: 'Are you sure you want to approve this document?',
        icon: 'check_circle',
        confirmColor: '#4caf50',
        btnText: 'Approve',
        showCheckbox: false
      }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result && result.confirm) {
        this.executeApproval(id);
      }
    });
  }

  private executeApproval(id: number) {
    const userEmail = localStorage.getItem("LoggedInUser");
    if (!userEmail) return;

    this.isProcessing = true;
    this.cdr.detectChanges();

    this.http.put(`http://localhost:8080/api/files/approve/${id}?email=${userEmail}`, {}, { responseType: 'text' }).subscribe({
      next: () => {
        this.files = this.files.map(f => f.id === id ? { ...f, approval: 'approved' } : f);
        this.dataSource.data = this.files;
        this.snackBar.open("File approved successfully", "Close", { duration: 3000 });
        this.isProcessing = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.isProcessing = false;
        this.snackBar.open("Only Admins can approve files", "Close", { duration: 3000 });
        this.cdr.detectChanges();
      }
    });
  }

  previewFile(id: number) {
    this.http.get(`http://localhost:8080/api/files/AllFiles/${id}`, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const fileURL = URL.createObjectURL(blob);
        window.open(fileURL, '_blank');
      }
    });
  }

  onDragOver(event: DragEvent) {
    event.preventDefault();
    this.isDragging = true;
  }

  onDragLeave() {
    this.isDragging = false;
  }

  onFileDropped(event: DragEvent) {
    event.preventDefault();
    this.isDragging = false;
    if (event.dataTransfer?.files.length) {
      this.selectedFiles = Array.from(event.dataTransfer.files);
      this.uploadFiles();
    }
  }

  triggerFileSelect() {
    const input = document.createElement('input');
    input.type = 'file';
    input.multiple = true;
    input.accept = '.pdf,.png,.jpg,.jpeg,.docx';
    input.onchange = (event: any) => {
      if (event.target.files?.length) {
        this.selectedFiles = Array.from(event.target.files);
        this.uploadFiles();
      }
    };
    input.click();
  }

  uploadFiles() {
    if (this.selectedFiles.length === 0) return;
    const userEmail = localStorage.getItem("LoggedInUser");
    if (!userEmail) return;

    const uploadRequests = this.selectedFiles.map(file => {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('approval', 'pending');
      formData.append('email', userEmail);
      return this.http.post(this.uploadUrl, formData);
    });

    forkJoin(uploadRequests).subscribe({
      next: () => {
        this.snackBar.open(`${this.selectedFiles.length} Files uploaded successfully`, "Close", { duration: 3000 });
        this.selectedFiles = [];
        this.loadFiles();
      }
    });
  }
}

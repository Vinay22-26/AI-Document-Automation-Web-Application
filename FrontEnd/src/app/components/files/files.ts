import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { CommonModule } from '@angular/common';
import { forkJoin } from 'rxjs';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { DeleteConfirmDialog } from '../../services/delete-confirm.component';

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
    MatDialogModule
  ],
  templateUrl: './files.html',
  styleUrl: './files.scss'
})
export class Files implements OnInit {
  private http = inject(HttpClient);
  private cdr = inject(ChangeDetectorRef);
  private snackBar = inject(MatSnackBar);
  private dialog = inject(MatDialog);

  displayedColumns: string[] = ['id', 'name', 'preview', 'status', 'action'];
  files: any[] = [];
  isDragging = false;
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
        this.displayedColumns.splice(2, 0, 'user');
      }
      if (!this.displayedColumns.includes('approval')) {
        this.displayedColumns.push('approval');
      }
    }
  }

  loadFiles() {
    const userEmail = localStorage.getItem("LoggedInUser");
    if (!userEmail) {
      this.snackBar.open("User not logged in", "Close", { duration: 3000 });
      return;
    }

    const finalUrl = (this.userRole === 'ADMIN') ? this.adminUrl : this.apiUrl;
    const urlWithParams = this.userRole === 'ADMIN' ? finalUrl : `${finalUrl}?email=${userEmail}`;

    this.http.get<any[]>(urlWithParams).subscribe({
      next: (res) => {
        this.files = res;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error fetching files:', err);
        this.files = [];
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
      const dialogRef = this.dialog.open(DeleteConfirmDialog);

      dialogRef.afterClosed().subscribe(result => {
        if (result && result.confirm) {
          if (result.skip) {
            localStorage.setItem("skipDeleteConfirm", 'true');
          }
          this.executeDelete(id, userEmail);
        }
      });
    }
  }

  private executeDelete(id: number, userEmail: string) {
    this.http.delete(`${this.deleteUrl}${id}?email=${userEmail}`, {
      responseType: 'text'
    }).subscribe({
      next: () => {
        this.files = this.files.filter(f => f.id !== id);
        this.snackBar.open("File deleted successfully", "Close", { duration: 3000 });
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error("Delete error:", err);
        this.snackBar.open("Delete failed", "Close", { duration: 3000 });
      }
    });
  }

  approveFile(id: number) {
    const userEmail = localStorage.getItem("LoggedInUser");
    if (!userEmail) return;

    this.http.put(
      `http://localhost:8080/api/files/approve/${id}?email=${userEmail}`,
      {},
      { responseType: 'text' }
    ).subscribe({
      next: () => {
        this.files = this.files.map(f =>
          f.id === id ? { ...f, approval: 'approved' } : f
        );
        this.snackBar.open("File approved successfully", "Close", { duration: 3000 });
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.snackBar.open("Only Admins can approve files", "Close", { duration: 3000 });
      }
    });
  }

  previewFile(id: number) {
    this.http.get(`http://localhost:8080/api/files/AllFiles/${id}`, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const fileURL = URL.createObjectURL(blob);
        window.open(fileURL, '_blank');
      },
      error: (err) => {
        console.error("Preview error:", err);
        this.snackBar.open("Access Denied or File not found", "Close", { duration: 3000 });
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
      },
      error: (err) => {
        console.error('Upload error:', err);
        this.snackBar.open('One or more uploads failed', "Close", { duration: 3000 });
      }
    });
  }
}

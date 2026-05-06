import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

@Component({
  selector: 'app-validate-step',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatIconModule, MatSnackBarModule],
  templateUrl: './validate.html',
  styleUrl: './validate.scss'
})
export class Validate implements OnInit {
  extractedFiles: any[] = [];
  selectedFile: any = null;
  editedContent: string = '';
  originalContent: string = '';
  aiLoading = false;
  aiPrompt = '';
  errors: string[] = [];

  constructor(private http: HttpClient, private router: Router, private snack: MatSnackBar) {}

  ngOnInit() {
    const email = localStorage.getItem('LoggedInUser');
    const url = email === 'admin123@gmail.com'
      ? 'http://localhost:8080/api/extract/all'
      : `http://localhost:8080/api/extract/user/${email}`;
    this.http.get<any[]>(url).subscribe(data => this.extractedFiles = data);
  }

  selectFile(file: any) {
    this.selectedFile = file;
    this.originalContent = file.content;
    this.editedContent = file.content;
    this.errors = [];
  }

  validate(): boolean {
    this.errors = [];
    if (!this.editedContent || this.editedContent.trim().length === 0)
      this.errors.push('Content cannot be empty.');
    if (this.editedContent.trim().length < 10)
      this.errors.push('Content is too short to be valid.');
    if (this.editedContent.length > 500000)
      this.errors.push('Content exceeds maximum allowed size.');
    return this.errors.length === 0;
  }

  aiAssist() {
    if (!this.aiPrompt.trim()) return;
    this.aiLoading = true;
    const email = localStorage.getItem('LoggedInUser') || '';
    this.http.post<any>('http://localhost:8080/api/chat/send', {
      fileId: this.selectedFile.fileId,
      message: this.aiPrompt + '\n\nHere is the document text to work on:\n' + this.editedContent,
      userEmail: email
    }).subscribe({
      next: (res) => {
        this.editedContent = res.response || res.message || res;
        this.aiLoading = false;
        this.aiPrompt = '';
      },
      error: () => { this.aiLoading = false; this.snack.open('AI assist failed', 'Close', { duration: 3000 }); }
    });
  }

  resetToOriginal() {
    this.editedContent = this.originalContent;
    this.errors = [];
  }

  saveAndProceed() {
    if (!this.validate()) return;
    this.http.put(`http://localhost:8080/api/extract/update/${this.selectedFile.fileId}`,
      { content: this.editedContent },
      { responseType: 'text' }
    ).subscribe({
      next: () => {
        this.snack.open('Saved! Proceeding to export...', '', { duration: 2000 });
        setTimeout(() => this.router.navigate(['/navbar/Export'],
          { queryParams: { fileId: this.selectedFile.fileId, fileName: this.selectedFile.fileName } }), 1500);
      },
      error: () => this.snack.open('Save failed', 'Close', { duration: 3000 })
    });
  }
}

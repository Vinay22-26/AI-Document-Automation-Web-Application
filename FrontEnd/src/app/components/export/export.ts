import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

@Component({
  selector: 'app-export-step',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule, MatSnackBarModule],
  templateUrl: './export.html',
  styleUrl: './export.scss'
})
export class Export implements OnInit {
  fileId: string | null = null;
  fileName: string = 'document';
  downloading = false;

  constructor(private route: ActivatedRoute, private http: HttpClient, private snack: MatSnackBar) {}

  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      this.fileId = params['fileId'];
      this.fileName = params['fileName'] || 'document';
    });
  }

  downloadPdf() {
    if (!this.fileId) return;
    this.downloading = true;
    this.http.get(`http://localhost:8080/api/export/pdf/${this.fileId}`,
      { responseType: 'blob' }
    ).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = this.fileName.replace(/\.(pdf|docx|png|jpg|jpeg)$/, '') + '_edited.pdf';
        a.click();
        URL.revokeObjectURL(url);
        this.downloading = false;
        this.snack.open('PDF downloaded!', '', { duration: 3000 });
      },
      error: () => {
        this.downloading = false;
        this.snack.open('Export failed. Try again.', 'Close', { duration: 3000 });
      }
    });
  }
}

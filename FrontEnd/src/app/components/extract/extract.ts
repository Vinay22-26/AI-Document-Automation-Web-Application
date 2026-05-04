import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';

@Component({
  imports: [CommonModule],
  selector: 'app-extract',
  templateUrl: './extract.html',
  styleUrls: ['./extract.scss']
})
export class Extract implements OnInit {
  extractedFiles: any[] = [];
  selectedContent: string = '';

  constructor(private http: HttpClient, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.fetchExtractedData();
  }

  fetchExtractedData() {
  const email = localStorage.getItem('LoggedInUser');

  if (email === 'admin123@gmail.com') {
    this.http.get<any[]>('http://localhost:8080/api/extract/all').subscribe({
      next: (data) => {
        this.extractedFiles = data;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error fetching admin data', err);
      }
    });
  } else {
    this.http.get<any[]>(`http://localhost:8080/api/extract/user/${email}`).subscribe({
      next: (data) => {
        this.extractedFiles = data;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error fetching user data', err);
      }
    });
  }
}

  viewContent(content: string) {
    this.selectedContent = content;
  }
}

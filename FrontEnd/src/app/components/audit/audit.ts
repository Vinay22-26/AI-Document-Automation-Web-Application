import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

interface AuditLog {
  user: string;
  action: string;
  actionType: 'create' | 'update' | 'delete';
  fileName: string;
  timestamp: string | Date;
  status: 'success' | 'failed';
}

@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatCardModule
  ],
  templateUrl: './audit.html',
  styleUrl: './audit.scss',
})
export class Audit implements OnInit {
  private http = inject(HttpClient);

  private apiUrl = 'http://localhost:8080/api/audit';
  private adminUrl = 'http://localhost:8080/api/audit/AdminLogs';

  displayedColumns: string[] = ['user', 'action', 'file', 'date', 'status'];
  dataSource = new MatTableDataSource<AuditLog>([]);

  ngOnInit(): void {
    this.fetchAuditLogs();
  }

  fetchAuditLogs(): void {
    const userEmail = localStorage.getItem('LoggedInUser');
    const userRole = localStorage.getItem('role');

    if (!userEmail) return;

    const finalUrl = (userRole === 'ADMIN') ? this.adminUrl : this.apiUrl;
    const params = new HttpParams().set('email', userEmail);

    this.http.get<any[]>(finalUrl, { params }).subscribe({
      next: (data) => {
        const mappedData: AuditLog[] = data.map(log => ({
          user: log.email,
          action: log.status,
          actionType: log.status === 'CREATED' ? 'create' : 'update',
          fileName: log.filename,
          timestamp: log.timestamp || log.createdDate,
          status: 'success'
        }));
        this.dataSource.data = mappedData;
      },
      error: (err) => {
        console.error('API Error:', err);
        this.dataSource.data = [];
      }
    });
  }

  applyFilter(event: Event): void {
    const filterValue = (event.target as HTMLInputElement).value;
    this.dataSource.filter = filterValue.trim().toLowerCase();
  }
}

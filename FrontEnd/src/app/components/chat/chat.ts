import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { marked } from 'marked';

interface ExtractedContentDTO {
  fileId: number;
  fileName: string;
  content: string;
}

interface Message {
  text: string;
  sender: 'user' | 'ai';
  safeHtml?: SafeHtml;
}

@Component({
  selector: 'app-chat-interface',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatSidenavModule,
    MatListModule,
    MatDividerModule,
    MatProgressSpinnerModule,
    MatAutocompleteModule
  ],
  templateUrl: './chat.html',
  styleUrls: ['./chat.scss']
})
export class Chat implements OnInit {
  searchQuery: string | ExtractedContentDTO | any = '';
  userInput: string = '';
  selectedFile: ExtractedContentDTO | null = null;
  isLoading: boolean = false;

  files: ExtractedContentDTO[] = [];
  filteredFiles: ExtractedContentDTO[] = [];
  messages: Message[] = [];

  constructor(
    private http: HttpClient,
    private cdr: ChangeDetectorRef,
    private sanitizer: DomSanitizer
  ) {}

  ngOnInit(): void {
    const welcomeText = "Hello! I'm your AI assistant. Select an extracted file from the menu to begin analysis.";
    this.messages.push({
      text: welcomeText,
      sender: 'ai',
      safeHtml: this.renderMarkdown(welcomeText)
    });
    this.fetchExtractedFiles();
  }

  renderMarkdown(content: any): SafeHtml {
    if (!content) return '';
    let text = typeof content === 'string' ? content : JSON.stringify(content, null, 2);

    const renderer = new marked.Renderer();
    renderer.code = ({ text, lang }: any) => {
      const escapedCode = text.replace(/&/g, '&amp;')
                              .replace(/</g, '&lt;')
                              .replace(/>/g, '&gt;')
                              .replace(/"/g, '&quot;')
                              .replace(/'/g, '&#39;');
      const langText = lang ? lang : 'code';
      return `
        <div class="code-block-wrapper">
          <div class="code-header">
            <span class="code-lang">${langText}</span>
            <button class="copy-code-btn">Copy</button>
          </div>
          <pre><code>${escapedCode}</code></pre>
        </div>
      `;
    };

    const html = marked.parse(text, { renderer: renderer }) as string;
    return this.sanitizer.bypassSecurityTrustHtml(html);
  }

  onMessageClick(event: Event): void {
    const target = event.target as HTMLElement;
    const copyBtn = target.closest('.copy-code-btn') as HTMLButtonElement;

    if (copyBtn) {
      const wrapper = copyBtn.closest('.code-block-wrapper');
      const codeBlock = wrapper?.querySelector('code');

      if (codeBlock) {
        const codeText = codeBlock.innerText;
        navigator.clipboard.writeText(codeText).then(() => {
          const originalText = copyBtn.innerHTML;
          copyBtn.innerHTML = 'Copied!';
          copyBtn.style.color = '#4caf50';

          setTimeout(() => {
            copyBtn.innerHTML = originalText;
            copyBtn.style.color = '';
          }, 2000);
        });
      }
    }
  }

  fetchExtractedFiles(): void {
    const userStr = localStorage.getItem('LoggedInUser');
    const token = localStorage.getItem('token');

    if (userStr) {
      let email = '';
      let role = 'USER';

      try {
        const user = JSON.parse(userStr);
        email = user.email || userStr;
        role = user.role?.toUpperCase() || 'USER';
      } catch (error) {
        email = userStr;
        if (email === 'admin123@gmail.com') {
          role = 'ADMIN';
        }
      }

      let apiUrl = '';

      if (role === 'ADMIN') {
        apiUrl = 'http://localhost:8080/api/extract/all';
      } else {
        apiUrl = `http://localhost:8080/api/extract/user/${email}`;
      }

      let headers = new HttpHeaders();
      if (token) {
        headers = headers.set('Authorization', `Bearer ${token}`);
      }

      this.http.get<ExtractedContentDTO[]>(apiUrl, { headers }).subscribe({
        next: (data) => {
          this.files = data;
          this.filteredFiles = [...this.files];
          this.cdr.detectChanges();
        },
        error: (err) => {
          console.error(err);
        }
      });
    }
  }

  performSearch(): void {
    const filterValue = typeof this.searchQuery === 'string'
      ? this.searchQuery.toLowerCase()
      : (this.searchQuery?.fileName || '').toLowerCase();

    this.filteredFiles = this.files.filter(f =>
      f.fileName.toLowerCase().includes(filterValue)
    );
    this.cdr.detectChanges();
  }

  displayFn(file: ExtractedContentDTO): string {
    return file && file.fileName ? file.fileName : '';
  }

  onSearchOptionSelected(event: MatAutocompleteSelectedEvent): void {
    const selectedFile = event.option.value as ExtractedContentDTO;
    this.selectFile(selectedFile);
    this.searchQuery = '';
    this.filteredFiles = [...this.files];
    this.cdr.detectChanges();
  }

  selectFile(file: ExtractedContentDTO): void {
    this.selectedFile = file;
    this.messages = [];
    this.isLoading = true;

    const token = localStorage.getItem('token');
    let headers = new HttpHeaders();
    if (token) {
      headers = headers.set('Authorization', `Bearer ${token}`);
    }

    this.http.get<any[]>(`http://localhost:8080/api/chat/history/${file.fileId}`, { headers }).subscribe({
      next: (history) => {
        if (history && history.length > 0) {
          this.messages = history.map(h => ({
            text: h.message,
            sender: h.sender,
            safeHtml: this.renderMarkdown(h.message)
          }));
        } else {
          const msgText = `Selected file: ${file.fileName}. You can now ask questions about its content.`;
          this.messages.push({
            text: msgText,
            sender: 'ai',
            safeHtml: this.renderMarkdown(msgText)
          });
        }
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error("Failed to load history", err);
        this.isLoading = false;
        this.cdr.detectChanges();
      }
    });
  }

  sendMessage(): void {
    if (this.userInput.trim() && !this.isLoading) {
      const userText = this.userInput;
      this.messages.push({
        text: userText,
        sender: 'user',
        safeHtml: this.renderMarkdown(userText)
      });
      this.userInput = '';
      this.isLoading = true;
      this.cdr.detectChanges();

      const payload = {
        fileId: this.selectedFile ? this.selectedFile.fileId : null,
        message: userText
      };

      const token = localStorage.getItem('token');
      let headers = new HttpHeaders();
      if (token) {
        headers = headers.set('Authorization', `Bearer ${token}`);
      }

      this.http.post<{response: string}>('http://localhost:8080/api/chat/ask', payload, { headers }).subscribe({
        next: (res) => {
          this.messages.push({
            text: res.response,
            sender: 'ai',
            safeHtml: this.renderMarkdown(res.response)
          });
          this.isLoading = false;
          this.cdr.detectChanges();
        },
        error: (err) => {
          const errorMsg = "Sorry, I encountered an error while connecting to the server.";
          this.messages.push({
            text: errorMsg,
            sender: 'ai',
            safeHtml: this.renderMarkdown(errorMsg)
          });
          this.isLoading = false;
          this.cdr.detectChanges();
        }
      });
    }
  }
}

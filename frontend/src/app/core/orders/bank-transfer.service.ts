import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';

export type BankTransferProofStatus = 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'FILE_DELETED';

export interface BankAccount {
  holder: string;
  taxId: string;
  bankName: string;
  alias: string;
  cbu: string;
  currency: string;
}

export interface BankTransferProof {
  id: string;
  status: BankTransferProofStatus;
  originalFilename: string | null;
  contentType: string | null;
  sizeBytes: number;
  submittedAt: string;
  reviewedAt: string | null;
  rejectionReason: string | null;
  previewCount: number;
}

export interface BankTransferDetails {
  orderId: number;
  paymentDueAt: string | null;
  bankAccount: BankAccount;
  proof: BankTransferProof | null;
}

@Injectable({ providedIn: 'root' })
export class BankTransferService {
  private readonly http = inject(HttpClient);

  get(orderId: number) {
    return this.http.get<BankTransferDetails>(`${environment.apiBaseUrl}/orders/${orderId}/bank-transfer`);
  }

  uploadProof(orderId: number, file: File) {
    const body = new FormData();
    body.append('file', file);
    const idempotencyKey = this.proofAttempt(orderId, file);
    return this.http.post<BankTransferDetails>(`${environment.apiBaseUrl}/orders/${orderId}/bank-transfer/proof`, body, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  private proofAttempt(orderId: number, file: File): string {
    const storageKey = `pinatech-bank-transfer-proof-${orderId}`;
    const fingerprint = `${file.name}|${file.type}|${file.size}|${file.lastModified}`;
    try {
      const stored = JSON.parse(localStorage.getItem(storageKey) ?? 'null') as unknown;
      if (stored && typeof stored === 'object') {
        const value = stored as Record<string, unknown>;
        if (value['fingerprint'] === fingerprint && typeof value['key'] === 'string' && value['key']) {
          return value['key'];
        }
      }
    } catch { /* Replace malformed or unavailable storage below. */ }

    const key = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    try { localStorage.setItem(storageKey, JSON.stringify({ fingerprint, key })); } catch { /* The current request remains valid. */ }
    return key;
  }
}

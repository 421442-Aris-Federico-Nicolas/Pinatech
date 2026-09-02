export interface UploadResult<TPending, TUploaded> {
  pending: TPending;
  uploaded: TUploaded | null;
  error?: unknown;
}

export function summarizeUploadResults<TPending, TUploaded>(results: UploadResult<TPending, TUploaded>[]) {
  const uploaded: TUploaded[] = [];
  const succeeded: TPending[] = [];
  const failed: TPending[] = [];
  const errors: unknown[] = [];

  for (const result of results) {
    if (result.uploaded === null) {
      failed.push(result.pending);
      if (result.error !== undefined) errors.push(result.error);
    } else {
      uploaded.push(result.uploaded);
      succeeded.push(result.pending);
    }
  }

  return { uploaded, succeeded, failed, errors };
}

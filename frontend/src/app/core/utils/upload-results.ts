export interface UploadResult<TPending, TUploaded> {
  pending: TPending;
  uploaded: TUploaded | null;
}

export function summarizeUploadResults<TPending, TUploaded>(results: UploadResult<TPending, TUploaded>[]) {
  const uploaded: TUploaded[] = [];
  const succeeded: TPending[] = [];
  const failed: TPending[] = [];

  for (const result of results) {
    if (result.uploaded === null) {
      failed.push(result.pending);
    } else {
      uploaded.push(result.uploaded);
      succeeded.push(result.pending);
    }
  }

  return { uploaded, succeeded, failed };
}

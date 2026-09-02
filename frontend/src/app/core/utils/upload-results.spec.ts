import { summarizeUploadResults, UploadResult } from './upload-results';

describe('summarizeUploadResults', () => {
  it('keeps each upload associated with its pending item', () => {
    const first = { name: 'first.jpg' };
    const second = { name: 'second.jpg' };
    const third = { name: 'third.jpg' };
    const uploadedFirst = { id: 1 };
    const uploadedThird = { id: 3 };
    const results: UploadResult<{ name: string }, { id: number }>[] = [
      { pending: first, uploaded: uploadedFirst },
      { pending: second, uploaded: null, error: 'failed' },
      { pending: third, uploaded: uploadedThird },
    ];

    expect(summarizeUploadResults(results)).toEqual({
      uploaded: [uploadedFirst, uploadedThird],
      succeeded: [first, third],
      failed: [second],
      errors: ['failed'],
    });
  });
});

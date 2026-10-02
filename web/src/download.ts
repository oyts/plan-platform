import { isAndroid, nativeInvoke } from '@/desktop';
export async function downloadFile(blob: Blob, fileName: string): Promise<void> {
  if (isAndroid()) {
    await nativeInvoke('share_file', { fileName, body: await blob.text(), mimeType: blob.type || 'text/plain' });
    return;
  }
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a'); link.href = url; link.download = fileName; link.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

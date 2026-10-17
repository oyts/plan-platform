import { isAndroid, nativeInvoke } from '@/desktop';
import axios from 'axios';
export async function downloadErrorMessage(error:unknown):Promise<string|undefined>{
  if(!axios.isAxiosError(error)||!(error.response?.data instanceof Blob)||error.response.data.size>8192)return;
  try{const body=JSON.parse(await error.response.data.text()) as {message?:unknown};return typeof body.message==='string'?body.message.slice(0,300):undefined;}catch{return;}
}
export async function downloadFile(blob: Blob, fileName: string): Promise<void> {
  if (isAndroid()) {
    // Blob.text() strips the UTF-8 BOM that the server adds for Excel exports.
    // ignoreBOM preserves that character; Android re-encodes the body as UTF-8.
    const body = new TextDecoder('utf-8', { ignoreBOM: true }).decode(await blob.arrayBuffer());
    await nativeInvoke('share_file', { fileName, body, mimeType: blob.type || 'text/plain' });
    return;
  }
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a'); link.href = url; link.download = fileName; link.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

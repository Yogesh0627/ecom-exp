'use client';

import { useRef } from 'react';
import { ImagePlus, X, Loader2 } from 'lucide-react';
import { cn } from '@/lib';
import { useUploadFile } from '@/hooks';
import { useToast } from '@/hooks/use-toast';

/**
 * Uploads images to object storage and hands back their public URLs. The parent owns the list of
 * URLs (they get stored on the variant); this just manages upload + preview. Category defaults to
 * product-images; certificates pass their own.
 */
export function ImageUploader({
  value,
  onChange,
  category = 'product-images',
  accept = 'image/*',
  max = 6,
}: {
  value: string[];
  onChange: (urls: string[]) => void;
  category?: string;
  accept?: string;
  max?: number;
}) {
  const upload = useUploadFile();
  const { toast } = useToast();
  const inputRef = useRef<HTMLInputElement>(null);

  const onPick = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? []);
    if (e.target) e.target.value = ''; // allow re-picking the same file
    for (const file of files) {
      if (value.length >= max) break;
      try {
        const { url } = await upload.mutateAsync({ file, category });
        onChange([...value, url]);
      } catch (err: unknown) {
        const msg =
          (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
          'Upload failed.';
        toast({ variant: 'destructive', title: 'Upload failed', description: msg });
      }
    }
  };

  const remove = (url: string) => onChange(value.filter((u) => u !== url));

  return (
    <div className="flex flex-wrap gap-3">
      {value.map((url) => (
        <div key={url} className="relative h-20 w-20 overflow-hidden rounded-md border">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={url} alt="" className="h-full w-full object-cover" />
          <button
            type="button"
            onClick={() => remove(url)}
            className="absolute right-0.5 top-0.5 rounded-full bg-background/80 p-0.5 text-muted-foreground hover:text-destructive"
            aria-label="Remove image"
          >
            <X className="h-3.5 w-3.5" />
          </button>
        </div>
      ))}

      {value.length < max && (
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          disabled={upload.isPending}
          className={cn(
            'flex h-20 w-20 flex-col items-center justify-center gap-1 rounded-md border-2 border-dashed text-xs text-muted-foreground transition-colors hover:border-primary hover:text-primary',
            upload.isPending && 'pointer-events-none opacity-60',
          )}
        >
          {upload.isPending ? (
            <Loader2 className="h-5 w-5 animate-spin" />
          ) : (
            <>
              <ImagePlus className="h-5 w-5" />
              Add
            </>
          )}
        </button>
      )}

      <input ref={inputRef} type="file" accept={accept} multiple onChange={onPick} className="hidden" />
    </div>
  );
}

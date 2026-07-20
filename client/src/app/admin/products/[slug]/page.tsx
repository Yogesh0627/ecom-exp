'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useRef, useState } from 'react';
import { ArrowLeft, Plus, Trash2, ShieldCheck, FileText, Leaf, Upload, Loader2 } from 'lucide-react';
import { ROUTES } from '@/constants';
import { dayjs } from '@/lib';
import {
  useProduct,
  useProductCertifications,
  useAddCertification,
  useVerifyCertification,
  useDeleteCertification,
  useUploadFile,
} from '@/hooks';
import { useToast } from '@/hooks/use-toast';
import { CERT_TYPE_LABEL, CERT_TYPES, type CertType } from '@/types';
import {
  Badge,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Input,
  Label,
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  DialogTrigger,
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
  DatePicker,
} from '@/components/ui';
import { AdminPageHeader, DataState, ProductContentManager } from '@/components/admin';

export default function AdminProductDetailPage() {
  const { slug } = useParams<{ slug: string }>();
  const { data: product, isLoading, error } = useProduct(slug);
  const { data: certs } = useProductCertifications(slug);
  const add = useAddCertification(slug);
  const verify = useVerifyCertification(slug);
  const remove = useDeleteCertification(slug);
  const upload = useUploadFile();
  const { toast } = useToast();
  const fileRef = useRef<HTMLInputElement>(null);

  const [open, setOpen] = useState(false);
  const [certType, setCertType] = useState<CertType>('NPOP_INDIA_ORGANIC');
  const [issuingBody, setIssuingBody] = useState('');
  const [certNo, setCertNo] = useState('');
  const [validFrom, setValidFrom] = useState('');
  const [validUntil, setValidUntil] = useState('');
  const [docUrl, setDocUrl] = useState('');
  const [docName, setDocName] = useState('');

  const reset = () => {
    setCertType('NPOP_INDIA_ORGANIC');
    setIssuingBody('');
    setCertNo('');
    setValidFrom('');
    setValidUntil('');
    setDocUrl('');
    setDocName('');
  };

  const onUploadDoc = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (e.target) e.target.value = '';
    if (!file) return;
    try {
      const { url } = await upload.mutateAsync({ file, category: 'certificates' });
      setDocUrl(url);
      setDocName(file.name);
    } catch {
      toast({ variant: 'destructive', title: 'Upload failed' });
    }
  };

  const onAdd = async () => {
    if (!product || !docUrl) return;
    try {
      await add.mutateAsync({
        productId: product.id,
        payload: {
          certType,
          issuingBody: issuingBody.trim() || undefined,
          certificateNumber: certNo.trim() || undefined,
          documentUrl: docUrl,
          validFrom: validFrom || undefined,
          validUntil: validUntil || undefined,
        },
      });
      toast({ variant: 'success', title: 'Certificate added' });
      setOpen(false);
      reset();
    } catch (e: unknown) {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Could not add the certificate.';
      toast({ variant: 'destructive', title: 'Failed', description: msg });
    }
  };

  return (
    <div className="mx-auto max-w-3xl">
      <Link
        href={ROUTES.admin.products}
        className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-4 w-4" /> Back to products
      </Link>

      <DataState isLoading={isLoading} error={error} skeletonRows={4} />

      {product && (
        <>
          <AdminPageHeader
            title={product.name}
            description={`/${product.slug}`}
            action={
              product.isOrganic ? (
                <Badge variant="success" className="gap-1">
                  <Leaf className="h-3 w-3" /> Organic
                </Badge>
              ) : undefined
            }
          />

          <Card>
            <CardHeader className="flex-row items-center justify-between pb-3">
              <CardTitle className="text-base">Certifications</CardTitle>
              <Dialog open={open} onOpenChange={setOpen}>
                <DialogTrigger asChild>
                  <Button size="sm">
                    <Plus className="mr-1 h-4 w-4" /> Add certificate
                  </Button>
                </DialogTrigger>
                <DialogContent>
                  <DialogHeader>
                    <DialogTitle>Add certificate</DialogTitle>
                  </DialogHeader>
                  <div className="space-y-3">
                    <div>
                      <Label className="text-xs">Type</Label>
                      <Select value={certType} onValueChange={(v) => setCertType(v as CertType)}>
                        <SelectTrigger className="mt-1 flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm">
                          <SelectValue placeholder="Select…" />
                        </SelectTrigger>
                        <SelectContent>
                          {CERT_TYPES.map((t) => (
                            <SelectItem key={t} value={t}>
                              {CERT_TYPE_LABEL[t]}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <Label className="text-xs">Issuing body</Label>
                        <Input value={issuingBody} onChange={(e) => setIssuingBody(e.target.value)} placeholder="APEDA" className="mt-1" />
                      </div>
                      <div>
                        <Label className="text-xs">Certificate no.</Label>
                        <Input value={certNo} onChange={(e) => setCertNo(e.target.value)} className="mt-1" />
                      </div>
                      <div>
                        <Label className="text-xs">Valid from</Label>
                        <DatePicker value={validFrom} onChange={setValidFrom} className="mt-1" />
                      </div>
                      <div>
                        <Label className="text-xs">Valid until</Label>
                        <DatePicker value={validUntil} onChange={setValidUntil} className="mt-1" />
                      </div>
                    </div>
                    <div>
                      <Label className="text-xs">Document (PDF or image)</Label>
                      <input ref={fileRef} type="file" accept=".pdf,image/*" onChange={onUploadDoc} className="hidden" />
                      <div className="mt-1 flex items-center gap-2">
                        <Button type="button" variant="outline" size="sm" onClick={() => fileRef.current?.click()} disabled={upload.isPending}>
                          {upload.isPending ? <Loader2 className="mr-1 h-4 w-4 animate-spin" /> : <Upload className="mr-1 h-4 w-4" />}
                          Upload
                        </Button>
                        {docUrl && <span className="truncate text-xs text-success">{docName || 'Uploaded'}</span>}
                      </div>
                    </div>
                  </div>
                  <DialogFooter>
                    <Button variant="outline" onClick={() => setOpen(false)}>
                      Cancel
                    </Button>
                    <Button onClick={onAdd} disabled={!docUrl || add.isPending}>
                      {add.isPending ? 'Adding…' : 'Add'}
                    </Button>
                  </DialogFooter>
                </DialogContent>
              </Dialog>
            </CardHeader>
            <CardContent>
              {!certs || certs.length === 0 ? (
                <p className="py-6 text-center text-sm text-muted-foreground">
                  No certificates yet. Add one to back the organic claim with proof.
                </p>
              ) : (
                <div className="space-y-2">
                  {certs.map((c) => (
                    <div key={c.id} className="flex items-center gap-3 rounded-md border p-3">
                      <FileText className="h-5 w-5 shrink-0 text-muted-foreground" />
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="font-medium">{CERT_TYPE_LABEL[c.certType]}</span>
                          {c.verified ? (
                            <Badge variant="success" className="gap-1">
                              <ShieldCheck className="h-3 w-3" /> Verified
                            </Badge>
                          ) : (
                            <Badge variant="warning">Unverified</Badge>
                          )}
                          {c.expired && <Badge variant="destructive">Expired</Badge>}
                        </div>
                        <p className="text-xs text-muted-foreground">
                          {c.issuingBody}
                          {c.certificateNumber && ` · ${c.certificateNumber}`}
                          {c.validUntil && ` · valid to ${dayjs(c.validUntil).format('ll')}`}
                        </p>
                      </div>
                      <a
                        href={c.documentUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-xs text-primary hover:underline"
                      >
                        View
                      </a>
                      {!c.verified && (
                        <Button size="sm" variant="outline" onClick={() => verify.mutate(c.id)} disabled={verify.isPending}>
                          Verify
                        </Button>
                      )}
                      <Button variant="ghost" size="icon" onClick={() => remove.mutate(c.id)}>
                        <Trash2 className="h-4 w-4 text-muted-foreground" />
                      </Button>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          <ProductContentManager slug={slug} />
        </>
      )}
    </div>
  );
}

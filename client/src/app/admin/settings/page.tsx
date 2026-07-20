'use client';

import { useEffect, useState } from 'react';
import { Globe, Lock } from 'lucide-react';
import { useSettings, useUpdateSetting } from '@/hooks';
import { useToast } from '@/hooks/use-toast';
import type { Setting } from '@/types';
import { Button, Card, CardContent, Input, Badge } from '@/components/ui';
import { AdminPageHeader, DataState } from '@/components/admin';

function SettingRow({ setting }: { setting: Setting }) {
  const update = useUpdateSetting();
  const { toast } = useToast();
  const [value, setValue] = useState(setting.value);

  // Keep the field in sync if the underlying value changes (e.g. after a refetch).
  useEffect(() => setValue(setting.value), [setting.value]);

  const dirty = value !== setting.value;

  const save = async () => {
    // Values are stored as JSON; validate before sending so the user gets a clear message
    // instead of a 400 from the server's JSON parse.
    try {
      JSON.parse(value);
    } catch {
      toast({
        variant: 'destructive',
        title: 'Invalid JSON',
        description: 'Wrap text in quotes, e.g. "value". Numbers and true/false are fine as-is.',
      });
      return;
    }
    try {
      await update.mutateAsync({ key: setting.key, value });
      toast({ variant: 'success', title: 'Saved', description: setting.key });
    } catch (e: unknown) {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Could not save.';
      toast({ variant: 'destructive', title: 'Failed', description: msg });
    }
  };

  return (
    <Card>
      <CardContent className="flex flex-wrap items-center gap-4 p-4">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <span className="font-mono text-sm font-medium">{setting.key}</span>
            {setting.isPublic ? (
              <Badge variant="secondary" className="gap-1">
                <Globe className="h-3 w-3" /> public
              </Badge>
            ) : (
              <Badge variant="outline" className="gap-1">
                <Lock className="h-3 w-3" /> private
              </Badge>
            )}
          </div>
          {setting.description && (
            <p className="mt-0.5 text-xs text-muted-foreground">{setting.description}</p>
          )}
        </div>
        <Input
          value={value}
          onChange={(e) => setValue(e.target.value)}
          className="w-full font-mono sm:w-64"
        />
        <Button size="sm" onClick={save} disabled={!dirty || update.isPending}>
          Save
        </Button>
      </CardContent>
    </Card>
  );
}

export default function AdminSettingsPage() {
  const { data: settings, isLoading, error } = useSettings();

  return (
    <div>
      <AdminPageHeader
        title="Settings"
        description="Store configuration. Values are JSON — numbers and true/false as-is, text in quotes."
      />

      <DataState
        isLoading={isLoading}
        error={error}
        isEmpty={!settings || settings.length === 0}
        emptyLabel="No settings defined."
      />

      {settings && settings.length > 0 && (
        <div className="space-y-2">
          {settings.map((s) => (
            <SettingRow key={s.key} setting={s} />
          ))}
        </div>
      )}
    </div>
  );
}

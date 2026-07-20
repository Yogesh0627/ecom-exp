'use client';

import * as React from 'react';
import { format } from 'date-fns';
import { Calendar as CalendarIcon, X } from 'lucide-react';
import { cn } from '@/lib';
import { Button } from './button';
import { Calendar } from './calendar';
import { Popover, PopoverContent, PopoverTrigger } from './popover';

interface DatePickerProps {
  /** ISO date string "YYYY-MM-DD", or empty/null for none. */
  value?: string | null;
  /** Called with "YYYY-MM-DD" on pick, or "" when cleared. */
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
  /** react-day-picker matcher for disabled days (e.g. past dates). */
  disabled?: React.ComponentProps<typeof Calendar>['disabled'];
  /** Show an inline clear (×) button when a date is set (default true). */
  clearable?: boolean;
  id?: string;
}

/** A shadcn date picker (Popover + Calendar) that reads/writes a "YYYY-MM-DD" string. */
export function DatePicker({
  value,
  onChange,
  placeholder = 'Pick a date',
  className,
  disabled,
  clearable = true,
  id,
}: DatePickerProps) {
  const [open, setOpen] = React.useState(false);
  // Parse as local midnight so the calendar doesn't shift a day across time zones.
  const selected = value ? new Date(`${value}T00:00:00`) : undefined;

  return (
    <div className={cn('relative', className)}>
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <Button
            id={id}
            type="button"
            variant="outline"
            className={cn(
              'w-full justify-start pl-3 pr-9 text-left font-normal',
              !selected && 'text-muted-foreground',
            )}
          >
            <CalendarIcon className="mr-2 h-4 w-4 shrink-0 opacity-70" />
            {selected ? format(selected, 'd MMM yyyy') : placeholder}
          </Button>
        </PopoverTrigger>
        <PopoverContent
          align="start"
          // The month/year Selects portal outside this popover; clicking an option must
          // not be treated as an outside click, or the calendar would close mid-pick.
          onInteractOutside={(e) => {
            const target = e.detail.originalEvent.target as Element | null;
            if (target?.closest('[data-radix-popper-content-wrapper],[role="listbox"]')) {
              e.preventDefault();
            }
          }}
        >
          <Calendar
            mode="single"
            selected={selected}
            onSelect={(d) => {
              onChange(d ? format(d, 'yyyy-MM-dd') : '');
              setOpen(false);
            }}
            disabled={disabled}
            defaultMonth={selected}
            initialFocus
          />
        </PopoverContent>
      </Popover>
      {clearable && selected && (
        <button
          type="button"
          onClick={() => onChange('')}
          aria-label="Clear date"
          className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-0.5 text-muted-foreground hover:text-foreground"
        >
          <X className="h-4 w-4" />
        </button>
      )}
    </div>
  );
}

'use client';

import * as React from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { DayPicker, type DropdownProps } from 'react-day-picker';
import { cn } from '@/lib';
import { buttonVariants } from './button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from './select';

/** Month / year caption rendered with the real shadcn Select (not react-day-picker's plain one). */
function CaptionDropdown({ value, onChange, children, caption }: DropdownProps) {
  const options = React.Children.toArray(children) as React.ReactElement<
    React.OptionHTMLAttributes<HTMLOptionElement>
  >[];

  const handleChange = (next: string) => {
    // react-day-picker's onChange expects a native select change event; synthesise one.
    onChange?.({ target: { value: next } } as React.ChangeEvent<HTMLSelectElement>);
  };

  return (
    <Select value={value?.toString()} onValueChange={handleChange}>
      <SelectTrigger className="h-8 w-fit gap-1 border-input px-2 text-sm font-medium focus:ring-1">
        <SelectValue>{caption}</SelectValue>
      </SelectTrigger>
      <SelectContent className="max-h-60">
        {options.map((option) => {
          const v = option.props.value?.toString() ?? '';
          return (
            <SelectItem key={v} value={v}>
              {option.props.children}
            </SelectItem>
          );
        })}
      </SelectContent>
    </Select>
  );
}

export type CalendarProps = React.ComponentProps<typeof DayPicker>;

const THIS_YEAR = new Date().getFullYear();

/** shadcn-style calendar built on react-day-picker. Used inside the DatePicker popover. */
export function Calendar({
  className,
  classNames,
  showOutsideDays = true,
  // Month + year dropdowns so you can jump to any date without clicking through months.
  captionLayout = 'dropdown',
  fromYear = THIS_YEAR - 100,
  toYear = THIS_YEAR + 20,
  ...props
}: CalendarProps) {
  return (
    <DayPicker
      showOutsideDays={showOutsideDays}
      captionLayout={captionLayout}
      fromYear={fromYear}
      toYear={toYear}
      className={cn('p-1', className)}
      classNames={{
        months: 'flex flex-col sm:flex-row gap-2',
        month: 'flex flex-col gap-3',
        caption: 'flex justify-center pt-1 relative items-center',
        caption_label: 'text-sm font-medium',
        caption_dropdowns: 'flex items-center justify-center gap-1.5',
        vhidden: 'hidden',
        nav: 'flex items-center gap-1',
        nav_button: cn(
          buttonVariants({ variant: 'outline' }),
          'h-7 w-7 bg-transparent p-0 opacity-70 hover:opacity-100',
        ),
        nav_button_previous: 'absolute left-1',
        nav_button_next: 'absolute right-1',
        table: 'w-full border-collapse space-y-1',
        head_row: 'flex',
        head_cell: 'text-muted-foreground rounded-md w-8 font-normal text-[0.8rem]',
        row: 'flex w-full mt-2',
        cell: 'h-8 w-8 text-center text-sm p-0 relative focus-within:relative focus-within:z-20',
        day: cn(buttonVariants({ variant: 'ghost' }), 'h-8 w-8 p-0 font-normal aria-selected:opacity-100'),
        day_selected:
          'bg-primary text-primary-foreground hover:bg-primary hover:text-primary-foreground focus:bg-primary focus:text-primary-foreground',
        day_today: 'bg-accent text-accent-foreground',
        day_outside: 'text-muted-foreground opacity-50',
        day_disabled: 'text-muted-foreground opacity-50',
        day_hidden: 'invisible',
        ...classNames,
      }}
      components={{
        IconLeft: () => <ChevronLeft className="h-4 w-4" />,
        IconRight: () => <ChevronRight className="h-4 w-4" />,
        Dropdown: CaptionDropdown,
      }}
      {...props}
    />
  );
}

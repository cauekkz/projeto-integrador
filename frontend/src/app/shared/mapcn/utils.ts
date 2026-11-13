import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

// junta classes do Tailwind (o "cn" do shadcn), usado pelo map.tsx do mapcn
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

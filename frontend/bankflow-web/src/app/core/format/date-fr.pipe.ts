import { Pipe, PipeTransform } from '@angular/core';

const EMPTY_VALUE = '—';

/** Formate une date ISO au format français, dans le fuseau du navigateur. */
@Pipe({ name: 'dateFr' })
export class DateFrPipe implements PipeTransform {
  transform(value: string | null | undefined, format: 'datetime' | 'date' = 'datetime'): string {
    const date = value ? new Date(value) : null;
    if (!date || Number.isNaN(date.getTime())) {
      return EMPTY_VALUE;
    }
    const day = new Intl.DateTimeFormat('fr-FR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    }).format(date);
    if (format === 'date') {
      return day;
    }
    const time = new Intl.DateTimeFormat('fr-FR', {
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    }).format(date);
    return `${day} ${time}`;
  }
}

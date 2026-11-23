import { HttpClient, HttpContext } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SILENT_ERRORS } from '../../core/http/api';
import { Customer, ProfileUpdate } from './profile.models';

const ME_URL = `${environment.apiUrl}/customers/me`;

@Service()
export class ProfileService {
  private readonly http = inject(HttpClient);

  /** Le 404 (profil pas encore créé) est géré par l'écran, sans message global. */
  getMyProfile(): Observable<Customer> {
    return this.http.get<Customer>(ME_URL, { context: new HttpContext().set(SILENT_ERRORS, true) });
  }

  updateMyProfile(update: ProfileUpdate): Observable<Customer> {
    return this.http.put<Customer>(ME_URL, update, {
      context: new HttpContext().set(SILENT_ERRORS, true),
    });
  }
}

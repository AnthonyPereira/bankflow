import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SILENT_ERRORS } from '../../core/http/api';
import { Customer } from './profile.models';
import { ProfileService } from './profile.service';

const CUSTOMER: Customer = {
  id: 'c1',
  userId: 'u1',
  email: 'a@bankflow.test',
  firstName: 'Ada',
  lastName: null,
  phone: null,
  address: null,
  status: 'ACTIVE',
  demo: false,
  createdAt: '2026-11-23T10:00:00Z',
  updatedAt: '2026-11-23T10:00:00Z',
};

describe('ProfileService', () => {
  let service: ProfileService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProfileService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lit le profil en gérant lui-même les erreurs', () => {
    let result: Customer | undefined;
    service.getMyProfile().subscribe((customer) => (result = customer));

    const request = http.expectOne('/api/customers/me');
    expect(request.request.method).toBe('GET');
    expect(request.request.context.get(SILENT_ERRORS)).toBe(true);
    request.flush(CUSTOMER);
    expect(result).toEqual(CUSTOMER);
  });

  it('met à jour le profil avec le corps attendu', () => {
    const update = { firstName: 'Ada', lastName: null, phone: '+33 6 12 34 56 78', address: null };
    service.updateMyProfile(update).subscribe();

    const request = http.expectOne('/api/customers/me');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(update);
    request.flush(CUSTOMER);
  });
});

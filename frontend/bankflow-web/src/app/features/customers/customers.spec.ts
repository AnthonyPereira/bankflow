import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { Customer, PageResponse } from './customers.models';
import { Customers, SEARCH_DELAY_MS } from './customers';

const URL = '/api/customers';

function build(overrides: Partial<Customer> = {}): Customer {
  return {
    id: 'c1',
    userId: 'u1',
    email: 'ada@bankflow.test',
    firstName: 'Ada',
    lastName: 'Lovelace',
    phone: null,
    address: null,
    status: 'ACTIVE',
    demo: false,
    createdAt: '2026-11-23T10:00:00Z',
    updatedAt: '2026-11-23T10:00:00Z',
    ...overrides,
  };
}

function page(content: Customer[], totalElements = content.length): PageResponse<Customer> {
  return { content, page: 0, size: 20, totalElements, totalPages: Math.ceil(totalElements / 20) };
}

describe('Customers', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  async function setup(response: PageResponse<Customer>) {
    await render(Customers, {
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const http = TestBed.inject(HttpTestingController);
    http.expectOne((r) => r.url === URL).flush(response);
    await screen.findByRole('table').catch(() => undefined);
    return { http, user: userEvent.setup() };
  }

  it('affiche les clients, leur statut et le badge Démo, avec un lien vers la fiche', async () => {
    await setup(
      page([
        build(),
        build({
          id: 'c2',
          email: 'demo@bankflow.test',
          firstName: null,
          lastName: null,
          demo: true,
        }),
        build({ id: 'c3', email: 'sus@bankflow.test', firstName: 'Bob', status: 'SUSPENDED' }),
      ]),
    );

    expect(screen.getByRole('heading', { level: 1, name: 'Clients' })).toBeTruthy();
    const link = screen.getByRole('link', { name: 'Ada Lovelace' });
    expect(link.getAttribute('href')).toBe('/administration/clients/c1');
    expect(screen.getByRole('link', { name: 'Nom non renseigné' })).toBeTruthy();
    expect(screen.getByText('Démo')).toBeTruthy();
    expect(screen.getByText('Suspendu')).toBeTruthy();
    expect(screen.getByText('3 clients trouvés.')).toBeTruthy();
  });

  it("indique qu'aucun client n'a été trouvé", async () => {
    await setup(page([]));

    expect(screen.getByText('Aucun client trouvé.')).toBeTruthy();
    expect(screen.queryByRole('table')).toBeNull();
  });

  it('cherche à la validation du formulaire, sans attendre la pause, et revient à la page 1', async () => {
    const { http, user } = await setup(page([build()]));

    await user.type(screen.getByLabelText('Nom ou adresse email'), '  ada  {Enter}');

    const request = http.expectOne((r) => r.url === URL);
    expect(request.request.params.get('q')).toBe('ada');
    expect(request.request.params.get('page')).toBe('0');
    request.flush(page([build()]));
  });

  it('cherche après une courte pause de saisie', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { http, user } = await setup(page([build()]));
    const field = screen.getByLabelText('Nom ou adresse email');

    await user.type(field, 'lov');
    http.expectNone((r) => r.url === URL);
    await vi.advanceTimersByTimeAsync(SEARCH_DELAY_MS);

    const request = http.expectOne((r) => r.url === URL);
    expect(request.request.params.get('q')).toBe('lov');
    request.flush(page([build()]));
  });

  it('filtre par statut', async () => {
    const { http, user } = await setup(page([build()]));

    await user.selectOptions(screen.getByLabelText('Statut'), 'SUSPENDED');

    const request = http.expectOne((r) => r.url === URL);
    expect(request.request.params.get('status')).toBe('SUSPENDED');
    request.flush(page([]));
    expect(await screen.findByText('Aucun client trouvé.')).toBeTruthy();
  });

  it('pagine avec la pagination maison', async () => {
    const { http, user } = await setup(page([build()], 45));

    expect(screen.getByText('1 – 20 sur 45')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Page suivante' }));

    const request = http.expectOne((r) => r.url === URL);
    expect(request.request.params.get('page')).toBe('1');
    request.flush({ ...page([build()], 45), page: 1 });
    expect(await screen.findByText('21 – 40 sur 45')).toBeTruthy();
  });

  it("n'affiche pas la pagination quand tout tient sur une page", async () => {
    await setup(page([build()]));

    expect(screen.queryByRole('navigation', { name: 'Pagination des clients' })).toBeNull();
  });

  it('propose de réessayer après une erreur', async () => {
    await render(Customers, {
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const http = TestBed.inject(HttpTestingController);
    http.expectOne((r) => r.url === URL).flush({}, { status: 500, statusText: 'Server Error' });

    expect((await screen.findByRole('alert')).textContent).toContain('pas pu être chargés');
    await userEvent.click(screen.getByRole('button', { name: 'Réessayer' }));
    http.expectOne((r) => r.url === URL).flush(page([build()]));
  });
});

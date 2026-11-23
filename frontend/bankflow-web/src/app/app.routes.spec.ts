import { Route } from '@angular/router';
import { adminGuard } from './core/auth/auth.guards';
import { routes } from './app.routes';

function findChild(parent: Route | undefined, path: string): Route | undefined {
  return parent?.children?.find((route) => route.path === path);
}

describe('routes', () => {
  const shell = routes.find((route) => route.path === '');
  const administration = findChild(shell, 'administration');

  it("réserve toute l'administration à l'administrateur", () => {
    expect(administration?.canActivate).toEqual([adminGuard]);
  });

  it('redirige administration vers la liste des clients', () => {
    expect(findChild(administration, '')?.redirectTo).toBe('clients');
  });

  it('expose la liste et la fiche des clients, sans garde propre (héritée du parent)', () => {
    const list = findChild(administration, 'clients');
    const detail = findChild(administration, 'clients/:id');

    expect(list?.title).toBe('Clients · BankFlow');
    expect(detail?.title).toBe('Fiche client · BankFlow');
    expect(list?.loadComponent).toBeTypeOf('function');
    expect(detail?.loadComponent).toBeTypeOf('function');
  });
});

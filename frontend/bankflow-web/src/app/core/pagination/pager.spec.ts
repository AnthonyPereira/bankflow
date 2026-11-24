import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { Pager } from './pager';

describe('Pager', () => {
  async function setup(pageIndex: number, totalElements: number) {
    const onPageChange = vi.fn();
    await render(Pager, {
      inputs: {
        pageIndex,
        pageSize: 20,
        totalElements,
        totalPages: Math.ceil(totalElements / 20),
        label: 'Pagination des comptes',
      },
      on: { pageChange: onPageChange },
    });
    return onPageChange;
  }

  it("affiche la plage dans une région annoncée aux lecteurs d'écran", async () => {
    await setup(0, 25);

    expect(screen.getByRole('navigation', { name: 'Pagination des comptes' })).toBeTruthy();
    expect(screen.getByText('1 – 20 sur 25').getAttribute('aria-live')).toBe('polite');
  });

  it("n'affiche rien quand tout tient sur une page", async () => {
    await setup(0, 20);

    expect(screen.queryByRole('navigation')).toBeNull();
  });

  it('désactive « Page précédente » sur la première page et émet la page suivante', async () => {
    const onPageChange = await setup(0, 25);

    expect(
      (screen.getByRole('button', { name: 'Page précédente' }) as HTMLButtonElement).disabled,
    ).toBe(true);
    await userEvent.setup().click(screen.getByRole('button', { name: 'Page suivante' }));

    expect(onPageChange).toHaveBeenCalledWith(1);
  });

  it('désactive « Page suivante » sur la dernière page et émet la page précédente', async () => {
    const onPageChange = await setup(1, 25);

    expect(screen.getByText('21 – 25 sur 25')).toBeTruthy();
    expect(
      (screen.getByRole('button', { name: 'Page suivante' }) as HTMLButtonElement).disabled,
    ).toBe(true);
    await userEvent.setup().click(screen.getByRole('button', { name: 'Page précédente' }));

    expect(onPageChange).toHaveBeenCalledWith(0);
  });
});
